package app.saikiran.SentinelFlow.job;

import app.saikiran.SentinelFlow.model.entity.AccountStats;
import app.saikiran.SentinelFlow.repository.AccountStatsRepository;
import app.saikiran.SentinelFlow.service.RedisCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJob {

    private final RedisCacheService redisCacheService;
    private final AccountStatsRepository accountStatsRepository;

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void reconcileRedisWithPostgres() {
        log.info("Starting scheduled Redis vs. Postgres cache reconciliation job...");
        Set<String> activeAccounts = redisCacheService.getActiveAccounts();

        if (activeAccounts.isEmpty()) {
            log.info("Reconciliation job finished: No active accounts in Redis set.");
            return;
        }

        int syncedCount = 0;
        for (String accountId : activeAccounts) {
            try {
                Optional<AccountStats> dbStatsOpt = accountStatsRepository.findById(accountId);
                if (dbStatsOpt.isPresent()) {
                    AccountStats dbStats = dbStatsOpt.get();
                    redisCacheService.saveAccountStats(dbStats);
                    syncedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to reconcile stats for account {}: {}", accountId, e.getMessage());
            }
        }

        log.info("Reconciliation job completed successfully. Re-synced {} active accounts from Postgres to Redis.", syncedCount);
    }
}
