package app.saikiran.SentinelFlow.engine;

import app.saikiran.SentinelFlow.model.entity.AccountStats;
import app.saikiran.SentinelFlow.model.entity.FlaggedTransaction;
import app.saikiran.SentinelFlow.model.entity.Transaction;
import app.saikiran.SentinelFlow.model.enums.AccountType;
import app.saikiran.SentinelFlow.model.enums.RuleName;
import app.saikiran.SentinelFlow.model.enums.Severity;
import app.saikiran.SentinelFlow.model.enums.TransactionType;
import app.saikiran.SentinelFlow.service.RuleConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class Algorithm1BehavioralAnomalyTest {

    private RuleConfigService ruleConfigService;
    private Algorithm1BehavioralAnomaly anomalyEngine;

    @BeforeEach
    void setUp() {
        ruleConfigService = Mockito.mock(RuleConfigService.class);
        anomalyEngine = new Algorithm1BehavioralAnomaly(ruleConfigService);
    }

    @Test
    void testColdStartSkipped() {
        Transaction txn = Transaction.builder()
                .id("txn_1")
                .accountId("acc_1")
                .accountType(AccountType.SAVINGS_ACCOUNT)
                .counterpartyId("acc_2")
                .amount(new BigDecimal("50000.00"))
                .type(TransactionType.TRANSFER)
                .timestamp(LocalDateTime.now())
                .build();

        AccountStats stats = AccountStats.builder()
                .accountId("acc_1")
                .totalHistoricalTxnCount(2L) // Less than 3 txns
                .build();

        List<FlaggedTransaction> flags = anomalyEngine.evaluate(txn, stats);
        assertTrue(flags.isEmpty(), "Cold start accounts should skip anomaly flagging");
    }

    @Test
    void testSingleTransactionAnomalyFlaggedInRupees() {
        Mockito.when(ruleConfigService.getMultiplierThreshold(eq(RuleName.AMOUNT_ANOMALY), any(), any()))
                .thenReturn(8.0);

        Transaction txn = Transaction.builder()
                .id("txn_1001")
                .accountId("acc_501")
                .accountType(AccountType.TRADING_ACCOUNT)
                .counterpartyId("acc_777")
                .amount(new BigDecimal("80000.00"))
                .type(TransactionType.TRANSFER)
                .timestamp(LocalDateTime.now())
                .build();

        AccountStats stats = AccountStats.builder()
                .accountId("acc_501")
                .accountType(AccountType.TRADING_ACCOUNT)
                .totalHistoricalTxnCount(10L)
                .avgAmount(new BigDecimal("7000.00")) // 80k / 7k = 11.4x (exceeds 8.0x)
                .amountSumToday(new BigDecimal("92000.00"))
                .avgDailyTotal(new BigDecimal("70000.00"))
                .build();

        List<FlaggedTransaction> flags = anomalyEngine.evaluate(txn, stats);

        assertFalse(flags.isEmpty());
        FlaggedTransaction flag = flags.get(0);
        assertEquals(RuleName.AMOUNT_ANOMALY, flag.getRuleName());
        assertTrue(flag.getReason().contains("₹80,000.00"));
        assertTrue(flag.getReason().contains("₹7,000.00"));
        assertTrue(flag.getReason().contains("11.4x"));
    }
}
