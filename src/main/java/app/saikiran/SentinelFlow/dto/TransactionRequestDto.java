package app.saikiran.SentinelFlow.dto;

import app.saikiran.SentinelFlow.model.enums.AccountType;
import app.saikiran.SentinelFlow.model.enums.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionRequestDto {

    @NotBlank(message = "Account ID is required")
    private String accountId;

    private AccountType accountType;

    @NotBlank(message = "Counterparty ID is required")
    private String counterpartyId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be strictly positive")
    private BigDecimal amount;

    @NotNull(message = "Transaction type is required")
    private TransactionType type;
}
