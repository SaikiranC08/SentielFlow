package app.saikiran.SentinelFlow.engine;

import app.saikiran.SentinelFlow.model.entity.AccountStats;
import app.saikiran.SentinelFlow.model.entity.FlaggedTransaction;
import app.saikiran.SentinelFlow.model.entity.Transaction;
import app.saikiran.SentinelFlow.model.enums.RuleName;
import app.saikiran.SentinelFlow.model.enums.Severity;
import app.saikiran.SentinelFlow.service.RuleConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class Algorithm1BehavioralAnomaly {

    private final RuleConfigService ruleConfigService;

    public List<FlaggedTransaction> evaluate(Transaction txn, AccountStats stats) {
        List<FlaggedTransaction> flags = new ArrayList<>();

        // Cold Start Check: Skip anomaly evaluation for brand new accounts (< 3 historical txns)
        if (stats.getTotalHistoricalTxnCount() != null && stats.getTotalHistoricalTxnCount() < 3) {
            log.debug("Skipping Algorithm 1 for account {}: Cold start (txns: {})", txn.getAccountId(), stats.getTotalHistoricalTxnCount());
            return flags;
        }

        double thresholdMultiplier = ruleConfigService.getMultiplierThreshold(
                RuleName.AMOUNT_ANOMALY,
                txn.getAccountId(),
                stats.getAccountType()
        );

        BigDecimal txnAmount = txn.getAmount();

        // 1. Single Transaction Anomaly Check
        if (stats.getAvgAmount() != null && stats.getAvgAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal singleRatio = txnAmount.divide(stats.getAvgAmount(), 2, RoundingMode.HALF_UP);
            if (singleRatio.doubleValue() >= thresholdMultiplier) {
                Severity severity = singleRatio.doubleValue() >= (thresholdMultiplier * 2.0) ? Severity.HIGH : Severity.MEDIUM;
                if (singleRatio.doubleValue() >= (thresholdMultiplier * 3.0)) {
                    severity = Severity.CRITICAL;
                }

                String reason = String.format(
                        "Single transaction %s is %.1fx this account's average transaction of %s (threshold: %.1fx for %s)",
                        formatRupees(txnAmount),
                        singleRatio.doubleValue(),
                        formatRupees(stats.getAvgAmount()),
                        thresholdMultiplier,
                        stats.getAccountType() != null ? stats.getAccountType().name() : "DEFAULT"
                );

                flags.add(FlaggedTransaction.builder()
                        .id("flg_" + UUID.randomUUID().toString().substring(0, 8))
                        .transactionId(txn.getId())
                        .accountId(txn.getAccountId())
                        .ruleName(RuleName.AMOUNT_ANOMALY)
                        .severity(severity)
                        .reason(reason)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }

        // 2. Daily Total Volume Anomaly Check
        if (stats.getAvgDailyTotal() != null && stats.getAvgDailyTotal().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dailyRatio = stats.getAmountSumToday().divide(stats.getAvgDailyTotal(), 2, RoundingMode.HALF_UP);
            if (dailyRatio.doubleValue() >= thresholdMultiplier) {
                Severity severity = dailyRatio.doubleValue() >= (thresholdMultiplier * 2.0) ? Severity.HIGH : Severity.MEDIUM;
                if (dailyRatio.doubleValue() >= (thresholdMultiplier * 3.0)) {
                    severity = Severity.CRITICAL;
                }

                String reason = String.format(
                        "Daily transaction volume %s is %.1fx this account's average daily total of %s (threshold: %.1fx)",
                        formatRupees(stats.getAmountSumToday()),
                        dailyRatio.doubleValue(),
                        formatRupees(stats.getAvgDailyTotal()),
                        thresholdMultiplier
                );

                flags.add(FlaggedTransaction.builder()
                        .id("flg_" + UUID.randomUUID().toString().substring(0, 8))
                        .transactionId(txn.getId())
                        .accountId(txn.getAccountId())
                        .ruleName(RuleName.AMOUNT_ANOMALY)
                        .severity(severity)
                        .reason(reason)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }

        return flags;
    }

    private String formatRupees(BigDecimal amount) {
        if (amount == null) return "₹0.00";
        return String.format("₹%,.2f", amount);
    }
}
