package app.saikiran.SentinelFlow.engine;

import app.saikiran.SentinelFlow.dto.RedisRoundTripEntry;
import app.saikiran.SentinelFlow.model.entity.FlaggedTransaction;
import app.saikiran.SentinelFlow.model.entity.PairRepeatCount;
import app.saikiran.SentinelFlow.model.entity.Transaction;
import app.saikiran.SentinelFlow.model.enums.RuleName;
import app.saikiran.SentinelFlow.model.enums.Severity;
import app.saikiran.SentinelFlow.repository.PairRepeatCountRepository;
import app.saikiran.SentinelFlow.service.RedisCacheService;
import app.saikiran.SentinelFlow.service.RuleConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class Algorithm2CircularTrading {

    private final RedisCacheService redisCacheService;
    private final PairRepeatCountRepository pairRepeatCountRepository;
    private final RuleConfigService ruleConfigService;

    public List<FlaggedTransaction> evaluate(Transaction txn) {
        List<FlaggedTransaction> flags = new ArrayList<>();

        String accountA = txn.getAccountId();
        String accountB = txn.getCounterpartyId();
        BigDecimal currentAmount = txn.getAmount();
        int windowMinutes = ruleConfigService.getWindowMinutes(RuleName.ROUND_TRIP);

        // 1. Direct Round-Trip Check (B -> A exists in Redis, now A -> B arrives)
        Optional<RedisRoundTripEntry> priorLeg = redisCacheService.getRoundTripLeg(accountB, accountA);

        if (priorLeg.isPresent()) {
            BigDecimal priorAmount = priorLeg.get().getAmount();
            BigDecimal diff = currentAmount.subtract(priorAmount).abs();
            BigDecimal varianceRatio = diff.divide(priorAmount, 4, RoundingMode.HALF_UP);

            // Amount similarity threshold: within 3% (0.03)
            if (varianceRatio.doubleValue() <= 0.03) {
                // Leg matched! Increment durable pair repeat count in PostgreSQL
                String lowerAcc = accountA.compareTo(accountB) < 0 ? accountA : accountB;
                String higherAcc = accountA.compareTo(accountB) < 0 ? accountB : accountA;

                PairRepeatCount pairCount = pairRepeatCountRepository.findByAccountAAndAccountB(lowerAcc, higherAcc)
                        .orElseGet(() -> PairRepeatCount.builder()
                                .accountA(lowerAcc)
                                .accountB(higherAcc)
                                .repeatCount(0)
                                .build());

                int newRepeatCount = pairCount.getRepeatCount() + 1;
                pairCount.setRepeatCount(newRepeatCount);
                pairCount.setLastOccurred(LocalDateTime.now());
                pairRepeatCountRepository.save(pairCount);

                Severity severity = newRepeatCount == 1 ? Severity.MEDIUM : (newRepeatCount == 2 ? Severity.HIGH : Severity.CRITICAL);
                double pctDiff = varianceRatio.doubleValue() * 100.0;

                String reason = String.format(
                        "Round-trip circular trade detected (#%d repeat) between %s and %s: returned %s is within %.1f%% of original %s (window: %d mins)",
                        newRepeatCount,
                        accountA,
                        accountB,
                        formatRupees(currentAmount),
                        pctDiff,
                        formatRupees(priorAmount),
                        windowMinutes
                );

                flags.add(FlaggedTransaction.builder()
                        .id("flg_" + UUID.randomUUID().toString().substring(0, 8))
                        .transactionId(txn.getId())
                        .accountId(accountA)
                        .ruleName(RuleName.ROUND_TRIP)
                        .severity(severity)
                        .reason(reason)
                        .createdAt(LocalDateTime.now())
                        .build());

                // Consume the leg so it doesn't double-flag
                redisCacheService.removeRoundTripLeg(accountB, accountA);
            }
        }

        // 2. Bounded 3-Hop Check (A -> B -> C -> A)
        // Check if B's recipients include C, and C has sent money to A recently
        Set<String> bRecipients = redisCacheService.getRecentRecipients(accountB);
        for (String c : bRecipients) {
            Set<String> cRecipients = redisCacheService.getRecentRecipients(c);
            if (cRecipients.contains(accountA)) {
                String reason = String.format(
                        "Multi-hop circular chain detected (3-hop: %s -> %s -> %s -> %s) within %d mins window",
                        accountA, accountB, c, accountA, windowMinutes
                );

                flags.add(FlaggedTransaction.builder()
                        .id("flg_" + UUID.randomUUID().toString().substring(0, 8))
                        .transactionId(txn.getId())
                        .accountId(accountA)
                        .ruleName(RuleName.ROUND_TRIP)
                        .severity(Severity.HIGH)
                        .reason(reason)
                        .createdAt(LocalDateTime.now())
                        .build());
                break;
            }
        }

        // 3. Save current leg to Redis for future reverse checks
        redisCacheService.saveRoundTripLeg(accountA, accountB, txn.getId(), currentAmount, txn.getTimestamp(), windowMinutes);
        redisCacheService.addRecentRecipient(accountA, accountB, windowMinutes);

        return flags;
    }

    private String formatRupees(BigDecimal amount) {
        if (amount == null) return "₹0.00";
        return String.format("₹%,.2f", amount);
    }
}
