package app.saikiran.SentinelFlow.controller;

import app.saikiran.SentinelFlow.dto.FlagResponseDto;
import app.saikiran.SentinelFlow.dto.TransactionRequestDto;
import app.saikiran.SentinelFlow.dto.TransactionResponseDto;
import app.saikiran.SentinelFlow.model.entity.RuleConfig;
import app.saikiran.SentinelFlow.repository.RuleConfigRepository;
import app.saikiran.SentinelFlow.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Transaction Surveillance APIs", description = "Endpoints for submitting financial transactions and viewing surveillance flags")
public class TransactionController {

    private final TransactionService transactionService;
    private final RuleConfigRepository ruleConfigRepository;

    @PostMapping("/transactions")
    @Operation(summary = "Submit a financial transaction", description = "Ingests a transaction stream event, updates durable & Redis stats, runs surveillance rules, and broadcasts flags via WebSockets if detected.")
    public ResponseEntity<TransactionResponseDto> processTransaction(@Valid @RequestBody TransactionRequestDto requestDto) {
        TransactionResponseDto response = transactionService.processTransaction(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/flags")
    @Operation(summary = "Get all flagged transactions", description = "Returns all suspicious transactions detected by SentinelFlow ordered by timestamp descending.")
    public ResponseEntity<List<FlagResponseDto>> getAllFlags() {
        return ResponseEntity.ok(transactionService.getAllFlags());
    }

    @GetMapping("/flags/account/{accountId}")
    @Operation(summary = "Get flagged transactions for account", description = "Returns suspicious transactions detected for a specific account ID.")
    public ResponseEntity<List<FlagResponseDto>> getFlagsForAccount(@PathVariable String accountId) {
        return ResponseEntity.ok(transactionService.getFlagsForAccount(accountId));
    }

    @GetMapping("/rules")
    @Operation(summary = "List rule configurations", description = "Returns active surveillance rules and their configurable threshold settings.")
    public ResponseEntity<List<RuleConfig>> getRuleConfigs() {
        return ResponseEntity.ok(ruleConfigRepository.findAll());
    }
}
