package app.saikiran.SentinelFlow.engine;

import app.saikiran.SentinelFlow.dto.RedisRoundTripEntry;
import app.saikiran.SentinelFlow.model.entity.FlaggedTransaction;
import app.saikiran.SentinelFlow.model.entity.PairRepeatCount;
import app.saikiran.SentinelFlow.model.entity.Transaction;
import app.saikiran.SentinelFlow.model.enums.RuleName;
import app.saikiran.SentinelFlow.model.enums.Severity;
import app.saikiran.SentinelFlow.model.enums.TransactionType;
import app.saikiran.SentinelFlow.repository.PairRepeatCountRepository;
import app.saikiran.SentinelFlow.service.RedisCacheService;
import app.saikiran.SentinelFlow.service.RuleConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class Algorithm2CircularTradingTest {

    private RedisCacheService redisCacheService;
    private PairRepeatCountRepository pairRepeatCountRepository;
    private RuleConfigService ruleConfigService;
    private Algorithm2CircularTrading circularEngine;

    @BeforeEach
    void setUp() {
        redisCacheService = Mockito.mock(RedisCacheService.class);
        pairRepeatCountRepository = Mockito.mock(PairRepeatCountRepository.class);
        ruleConfigService = Mockito.mock(RuleConfigService.class);

        Mockito.when(ruleConfigService.getWindowMinutes(eq(RuleName.ROUND_TRIP))).thenReturn(10);
        Mockito.when(redisCacheService.getRecentRecipients(any())).thenReturn(Collections.emptySet());

        circularEngine = new Algorithm2CircularTrading(redisCacheService, pairRepeatCountRepository, ruleConfigService);
    }

    @Test
    void testDirectRoundTripFlaggedInRupees() {
        // Leg 1 was acc_501 -> acc_777 ₹80,000.00 (saved in Redis as roundtrip:acc_501:acc_777)
        RedisRoundTripEntry leg1 = RedisRoundTripEntry.builder()
                .transactionId("txn_1001")
                .accountId("acc_501")
                .counterpartyId("acc_777")
                .amount(new BigDecimal("80000.00"))
                .timestamp(LocalDateTime.now().minusMinutes(5))
                .build();

        // When Leg 2 (acc_777 -> acc_501) arrives, engine queries roundtrip:acc_501:acc_777
        Mockito.when(redisCacheService.getRoundTripLeg("acc_501", "acc_777"))
                .thenReturn(Optional.of(leg1));

        PairRepeatCount existingCount = PairRepeatCount.builder()
                .accountA("acc_501")
                .accountB("acc_777")
                .repeatCount(2)
                .build();

        Mockito.when(pairRepeatCountRepository.findByAccountAAndAccountB("acc_501", "acc_777"))
                .thenReturn(Optional.of(existingCount));

        Transaction leg2Txn = Transaction.builder()
                .id("txn_1002")
                .accountId("acc_777")
                .counterpartyId("acc_501")
                .amount(new BigDecimal("79500.00"))
                .type(TransactionType.TRANSFER)
                .timestamp(LocalDateTime.now())
                .build();

        List<FlaggedTransaction> flags = circularEngine.evaluate(leg2Txn);

        assertFalse(flags.isEmpty(), "Circular trading leg 2 should generate a flag");
        FlaggedTransaction flag = flags.get(0);
        assertEquals(RuleName.ROUND_TRIP, flag.getRuleName());
        assertEquals(Severity.CRITICAL, flag.getSeverity()); // 3rd repeat = CRITICAL
        assertTrue(flag.getReason().contains("₹79,500.00"));
        assertTrue(flag.getReason().contains("₹80,000.00"));
        assertTrue(flag.getReason().contains("#3 repeat"));

        Mockito.verify(redisCacheService).removeRoundTripLeg("acc_501", "acc_777");
    }
}
