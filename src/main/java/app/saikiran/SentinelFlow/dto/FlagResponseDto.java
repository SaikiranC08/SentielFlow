package app.saikiran.SentinelFlow.dto;

import app.saikiran.SentinelFlow.model.enums.RuleName;
import app.saikiran.SentinelFlow.model.enums.Severity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlagResponseDto {
    private String id;
    private String transactionId;
    private String accountId;
    private RuleName ruleName;
    private Severity severity;
    private String reason;
    private LocalDateTime createdAt;
}
