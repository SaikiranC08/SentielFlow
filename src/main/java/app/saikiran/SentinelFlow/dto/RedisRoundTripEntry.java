package app.saikiran.SentinelFlow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedisRoundTripEntry {
    private String transactionId;
    private String accountId;
    private String counterpartyId;
    private BigDecimal amount;
    private LocalDateTime timestamp;
}
