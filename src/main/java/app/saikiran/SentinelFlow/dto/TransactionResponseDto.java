package app.saikiran.SentinelFlow.dto;

import app.saikiran.SentinelFlow.model.enums.AccountType;
import app.saikiran.SentinelFlow.model.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponseDto {
    private String id;
    private String accountId;
    private AccountType accountType;
    private String counterpartyId;
    private BigDecimal amount;
    private TransactionType type;
    private LocalDateTime timestamp;
    private boolean flagged;
    private List<FlagResponseDto> flags;
}
