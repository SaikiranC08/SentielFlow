package app.saikiran.SentinelFlow.engine;

import app.saikiran.SentinelFlow.model.entity.AccountStats;
import app.saikiran.SentinelFlow.model.entity.FlaggedTransaction;
import app.saikiran.SentinelFlow.model.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RuleEngine {

    private final Algorithm1BehavioralAnomaly algorithm1BehavioralAnomaly;
    private final Algorithm2CircularTrading algorithm2CircularTrading;

    public List<FlaggedTransaction> evaluateAll(Transaction transaction, AccountStats accountStats) {
        List<FlaggedTransaction> flags = new ArrayList<>();

        try {
            // Algorithm 1: Behavioral Anomaly Detection
            List<FlaggedTransaction> alg1Flags = algorithm1BehavioralAnomaly.evaluate(transaction, accountStats);
            flags.addAll(alg1Flags);
        } catch (Exception e) {
            log.error("Error evaluating Algorithm 1 for transaction {}: {}", transaction.getId(), e.getMessage(), e);
        }

        try {
            // Algorithm 2: Circular Trading Detection
            List<FlaggedTransaction> alg2Flags = algorithm2CircularTrading.evaluate(transaction);
            flags.addAll(alg2Flags);
        } catch (Exception e) {
            log.error("Error evaluating Algorithm 2 for transaction {}: {}", transaction.getId(), e.getMessage(), e);
        }

        return flags;
    }
}
