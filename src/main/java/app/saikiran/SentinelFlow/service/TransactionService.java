package app.saikiran.SentinelFlow.service;

import app.saikiran.SentinelFlow.dto.FlagResponseDto;
import app.saikiran.SentinelFlow.dto.TransactionRequestDto;
import app.saikiran.SentinelFlow.dto.TransactionResponseDto;
import app.saikiran.SentinelFlow.engine.RuleEngine;
import app.saikiran.SentinelFlow.model.entity.AccountStats;
import app.saikiran.SentinelFlow.model.entity.FlaggedTransaction;
import app.saikiran.SentinelFlow.model.entity.Transaction;
import app.saikiran.SentinelFlow.model.enums.AccountType;
import app.saikiran.SentinelFlow.repository.AccountStatsRepository;
import app.saikiran.SentinelFlow.repository.FlaggedTransactionRepository;
import app.saikiran.SentinelFlow.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountStatsRepository accountStatsRepository;
    private final FlaggedTransactionRepository flaggedTransactionRepository;
    private final RedisCacheService redisCacheService;
    private final RuleEngine ruleEngine;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public TransactionResponseDto processTransaction(TransactionRequestDto dto) {
        if (dto.getAccountId().trim().equalsIgnoreCase(dto.getCounterpartyId().trim())) {
            throw new IllegalArgumentException("Self-transfers between identical account and counterparty IDs are not allowed");
        }

        LocalDateTime now = LocalDateTime.now();
        String txnId = "txn_" + UUID.randomUUID().toString().substring(0, 8);

        AccountType accountType = dto.getAccountType() != null ? dto.getAccountType() : AccountType.SAVINGS_ACCOUNT;

        Transaction transaction = Transaction.builder()
                .id(txnId)
                .accountId(dto.getAccountId())
                .accountType(accountType)
                .counterpartyId(dto.getCounterpartyId())
                .amount(dto.getAmount())
                .type(dto.getType())
                .timestamp(now)
                .build();

        // 1. Write transaction to Postgres first (Source of Truth)
        transactionRepository.save(transaction);

        // 2. Read / Update AccountStats
        AccountStats stats = updateAccountStats(dto.getAccountId(), accountType, dto.getAmount(), now.toLocalDate());

        // 3. Write-Through to Redis Cache
        redisCacheService.saveAccountStats(stats);

        // 4. Run Rule Engine
        List<FlaggedTransaction> flags = ruleEngine.evaluateAll(transaction, stats);

        // 5. Persist Flags to Postgres and push via WebSocket
        List<FlagResponseDto> flagDtos = new ArrayList<>();
        if (!flags.isEmpty()) {
            flaggedTransactionRepository.saveAll(flags);
            for (FlaggedTransaction flag : flags) {
                FlagResponseDto flagDto = mapFlagToDto(flag);
                flagDtos.add(flagDto);

                // Broadcast over WebSocket to /topic/flags
                try {
                    messagingTemplate.convertAndSend("/topic/flags", flagDto);
                    log.info("Broadcasted flag {} to /topic/flags via WebSocket", flag.getId());
                } catch (Exception e) {
                    log.error("Failed to broadcast flag {} via WebSocket: {}", flag.getId(), e.getMessage());
                }
            }
        }

        return TransactionResponseDto.builder()
                .id(transaction.getId())
                .accountId(transaction.getAccountId())
                .accountType(transaction.getAccountType())
                .counterpartyId(transaction.getCounterpartyId())
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .timestamp(transaction.getTimestamp())
                .flagged(!flags.isEmpty())
                .flags(flagDtos)
                .build();
    }

    private AccountStats updateAccountStats(String accountId, AccountType accountType, BigDecimal amount, LocalDate today) {
        AccountStats stats = redisCacheService.getAccountStats(accountId)
                .orElseGet(() -> accountStatsRepository.findById(accountId)
                        .orElseGet(() -> AccountStats.builder()
                                .accountId(accountId)
                                .accountType(accountType)
                                .txnCountToday(0)
                                .amountSumToday(BigDecimal.ZERO)
                                .avgAmount(BigDecimal.ZERO)
                                .avgDailyTotal(BigDecimal.ZERO)
                                .maxTxnSeen(BigDecimal.ZERO)
                                .totalHistoricalTxnCount(0L)
                                .totalActiveDays(0)
                                .build()));

        if (stats.getAccountType() == null) {
            stats.setAccountType(accountType);
        }

        // Dynamic Date Rollover Handling
        if (stats.getLastTxnDate() == null) {
            stats.setLastTxnDate(today);
            stats.setTotalActiveDays(1);
        } else if (today.isAfter(stats.getLastTxnDate())) {
            // Fold yesterday's sum into avgDailyTotal
            int activeDays = stats.getTotalActiveDays() > 0 ? stats.getTotalActiveDays() : 1;
            BigDecimal diff = stats.getAmountSumToday().subtract(stats.getAvgDailyTotal());
            BigDecimal newAvgDailyTotal = stats.getAvgDailyTotal().add(diff.divide(BigDecimal.valueOf(activeDays), 2, RoundingMode.HALF_UP));

            stats.setAvgDailyTotal(newAvgDailyTotal);
            stats.setTotalActiveDays(stats.getTotalActiveDays() + 1);
            stats.setAmountSumToday(BigDecimal.ZERO);
            stats.setTxnCountToday(0);
            stats.setLastTxnDate(today);
        }

        // Update Today's Counters
        stats.setTxnCountToday(stats.getTxnCountToday() + 1);
        stats.setAmountSumToday(stats.getAmountSumToday().add(amount));

        // Update Running Historical Counters
        long newTxnCount = stats.getTotalHistoricalTxnCount() + 1;
        stats.setTotalHistoricalTxnCount(newTxnCount);

        // Incremental Running Mean Formula for avgAmount: newAvg = oldAvg + (amount - oldAvg) / newCount
        BigDecimal diff = amount.subtract(stats.getAvgAmount());
        BigDecimal newAvgAmount = stats.getAvgAmount().add(diff.divide(BigDecimal.valueOf(newTxnCount), 2, RoundingMode.HALF_UP));
        stats.setAvgAmount(newAvgAmount);

        // Update Max Txn Seen
        if (amount.compareTo(stats.getMaxTxnSeen()) > 0) {
            stats.setMaxTxnSeen(amount);
        }

        // Save to Postgres
        return accountStatsRepository.save(stats);
    }

    public List<FlagResponseDto> getAllFlags() {
        return flaggedTransactionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapFlagToDto)
                .collect(Collectors.toList());
    }

    public List<FlagResponseDto> getFlagsForAccount(String accountId) {
        return flaggedTransactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(this::mapFlagToDto)
                .collect(Collectors.toList());
    }

    private FlagResponseDto mapFlagToDto(FlaggedTransaction flag) {
        return FlagResponseDto.builder()
                .id(flag.getId())
                .transactionId(flag.getTransactionId())
                .accountId(flag.getAccountId())
                .ruleName(flag.getRuleName())
                .severity(flag.getSeverity())
                .reason(flag.getReason())
                .createdAt(flag.getCreatedAt())
                .build();
    }
}
