package app.saikiran.SentinelFlow.model.entity;

import app.saikiran.SentinelFlow.model.enums.AccountType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "account_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountStats {

    @Id
    @Column(name = "account_id", length = 64)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type")
    private AccountType accountType;

    @Column(name = "txn_count_today", nullable = false)
    @Builder.Default
    private Integer txnCountToday = 0;

    @Column(name = "amount_sum_today", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal amountSumToday = BigDecimal.ZERO;

    @Column(name = "avg_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal avgAmount = BigDecimal.ZERO;

    @Column(name = "avg_daily_total", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal avgDailyTotal = BigDecimal.ZERO;

    @Column(name = "max_txn_seen", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal maxTxnSeen = BigDecimal.ZERO;

    @Column(name = "total_historical_txn_count", nullable = false)
    @Builder.Default
    private Long totalHistoricalTxnCount = 0L;

    @Column(name = "total_active_days", nullable = false)
    @Builder.Default
    private Integer totalActiveDays = 0;

    @Column(name = "last_txn_date")
    private LocalDate lastTxnDate;
}
