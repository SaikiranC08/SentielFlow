package app.saikiran.SentinelFlow.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pair_repeat_counts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"account_a", "account_b"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PairRepeatCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_a", nullable = false, length = 64)
    private String accountA;

    @Column(name = "account_b", nullable = false, length = 64)
    private String accountB;

    @Column(name = "repeat_count", nullable = false)
    @Builder.Default
    private Integer repeatCount = 0;

    @Column(name = "last_occurred", nullable = false)
    private LocalDateTime lastOccurred;
}
