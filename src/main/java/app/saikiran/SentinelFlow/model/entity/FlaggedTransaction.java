package app.saikiran.SentinelFlow.model.entity;

import app.saikiran.SentinelFlow.model.enums.RuleName;
import app.saikiran.SentinelFlow.model.enums.Severity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "flagged_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlaggedTransaction {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_name", nullable = false, length = 64)
    private RuleName ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private Severity severity;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
