package app.saikiran.SentinelFlow.model.entity;

import app.saikiran.SentinelFlow.model.enums.RuleName;
import app.saikiran.SentinelFlow.model.enums.RuleScope;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rule_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RuleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_name", nullable = false, length = 64)
    private RuleName ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    private RuleScope scope;

    @Column(name = "scope_value", length = 64)
    private String scopeValue;

    @Column(name = "multiplier_threshold")
    private Double multiplierThreshold;

    @Column(name = "window_minutes")
    private Integer windowMinutes;
}
