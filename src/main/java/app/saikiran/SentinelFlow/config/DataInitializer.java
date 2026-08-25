package app.saikiran.SentinelFlow.config;

import app.saikiran.SentinelFlow.model.entity.AccountStats;
import app.saikiran.SentinelFlow.model.entity.RuleConfig;
import app.saikiran.SentinelFlow.model.enums.AccountType;
import app.saikiran.SentinelFlow.model.enums.RuleName;
import app.saikiran.SentinelFlow.model.enums.RuleScope;
import app.saikiran.SentinelFlow.repository.AccountStatsRepository;
import app.saikiran.SentinelFlow.repository.RuleConfigRepository;
import app.saikiran.SentinelFlow.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RuleConfigRepository ruleConfigRepository;
    private final AccountStatsRepository accountStatsRepository;
    private final RedisCacheService redisCacheService;

    @Override
    public void run(String... args) {
        initRuleConfigs();
        initAccountStats();
    }

    private void initRuleConfigs() {
        if (ruleConfigRepository.count() == 0) {
            log.info("Initializing rule configurations in PostgreSQL...");

            RuleConfig globalAnomaly = RuleConfig.builder()
                    .ruleName(RuleName.AMOUNT_ANOMALY)
                    .scope(RuleScope.GLOBAL)
                    .multiplierThreshold(3.0)
                    .windowMinutes(1440)
                    .build();

            RuleConfig tradingAnomaly = RuleConfig.builder()
                    .ruleName(RuleName.AMOUNT_ANOMALY)
                    .scope(RuleScope.ACCOUNT_TYPE)
                    .scopeValue(AccountType.TRADING_ACCOUNT.name())
                    .multiplierThreshold(8.0)
                    .windowMinutes(1440)
                    .build();

            RuleConfig businessAnomaly = RuleConfig.builder()
                    .ruleName(RuleName.AMOUNT_ANOMALY)
                    .scope(RuleScope.ACCOUNT_TYPE)
                    .scopeValue(AccountType.BUSINESS_ACCOUNT.name())
                    .multiplierThreshold(6.0)
                    .windowMinutes(1440)
                    .build();

            RuleConfig hnwAnomaly = RuleConfig.builder()
                    .ruleName(RuleName.AMOUNT_ANOMALY)
                    .scope(RuleScope.ACCOUNT_TYPE)
                    .scopeValue(AccountType.HIGH_NET_WORTH.name())
                    .multiplierThreshold(10.0)
                    .windowMinutes(1440)
                    .build();

            RuleConfig roundTripGlobal = RuleConfig.builder()
                    .ruleName(RuleName.ROUND_TRIP)
                    .scope(RuleScope.GLOBAL)
                    .multiplierThreshold(null)
                    .windowMinutes(10)
                    .build();

            ruleConfigRepository.saveAll(List.of(globalAnomaly, tradingAnomaly, businessAnomaly, hnwAnomaly, roundTripGlobal));
            log.info("Rule configurations initialized successfully.");
        }
    }

    private void initAccountStats() {
        if (accountStatsRepository.count() == 0) {
            log.info("Initializing sample account stats (in Rupees ₹) for demo...");

            AccountStats acc501 = AccountStats.builder()
                    .accountId("acc_501")
                    .accountType(AccountType.TRADING_ACCOUNT)
                    .txnCountToday(3)
                    .amountSumToday(new BigDecimal("92000.00"))
                    .avgAmount(new BigDecimal("7000.00"))
                    .avgDailyTotal(new BigDecimal("70000.00"))
                    .maxTxnSeen(new BigDecimal("15000.00"))
                    .totalHistoricalTxnCount(25L)
                    .totalActiveDays(10)
                    .lastTxnDate(LocalDate.now())
                    .build();

            AccountStats acc777 = AccountStats.builder()
                    .accountId("acc_777")
                    .accountType(AccountType.TRADING_ACCOUNT)
                    .txnCountToday(2)
                    .amountSumToday(new BigDecimal("15000.00"))
                    .avgAmount(new BigDecimal("5000.00"))
                    .avgDailyTotal(new BigDecimal("50000.00"))
                    .maxTxnSeen(new BigDecimal("10000.00"))
                    .totalHistoricalTxnCount(20L)
                    .totalActiveDays(8)
                    .lastTxnDate(LocalDate.now())
                    .build();

            AccountStats acc888 = AccountStats.builder()
                    .accountId("acc_888")
                    .accountType(AccountType.SAVINGS_ACCOUNT)
                    .txnCountToday(40)
                    .amountSumToday(new BigDecimal("48000.00"))
                    .avgAmount(new BigDecimal("1200.00"))
                    .avgDailyTotal(new BigDecimal("48000.00"))
                    .maxTxnSeen(new BigDecimal("5000.00"))
                    .totalHistoricalTxnCount(150L)
                    .totalActiveDays(30)
                    .lastTxnDate(LocalDate.now())
                    .build();

            accountStatsRepository.saveAll(List.of(acc501, acc777, acc888));
            redisCacheService.saveAccountStats(acc501);
            redisCacheService.saveAccountStats(acc777);
            redisCacheService.saveAccountStats(acc888);

            log.info("Sample account stats initialized successfully.");
        }
    }
}
