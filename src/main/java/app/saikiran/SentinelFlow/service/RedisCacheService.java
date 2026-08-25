package app.saikiran.SentinelFlow.service;

import app.saikiran.SentinelFlow.dto.RedisRoundTripEntry;
import app.saikiran.SentinelFlow.model.entity.AccountStats;
import app.saikiran.SentinelFlow.repository.AccountStatsRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AccountStatsRepository accountStatsRepository;

    private static final String STATS_KEY_PREFIX = "stats:";
    private static final String ROUNDTRIP_KEY_PREFIX = "roundtrip:";
    private static final String RECIPIENTS_KEY_PREFIX = "recent_recipients:";
    private static final String ACTIVE_ACCOUNTS_KEY = "active_accounts";

    @CircuitBreaker(name = "redisCache", fallbackMethod = "fallbackGetAccountStats")
    public Optional<AccountStats> getAccountStats(String accountId) {
        String key = STATS_KEY_PREFIX + accountId;
        Object cachedObj = redisTemplate.opsForValue().get(key);

        if (cachedObj instanceof AccountStats accountStats) {
            log.debug("Redis cache HIT for account: {}", accountId);
            return Optional.of(accountStats);
        }

        log.debug("Redis cache MISS for account: {}, fetching from Postgres", accountId);
        Optional<AccountStats> dbStats = accountStatsRepository.findById(accountId);
        dbStats.ifPresent(this::saveAccountStats);
        return dbStats;
    }

    public Optional<AccountStats> fallbackGetAccountStats(String accountId, Throwable throwable) {
        log.warn("Redis CircuitBreaker OPEN/Fallback triggered for account {}. Error: {}", accountId, throwable.getMessage());
        return accountStatsRepository.findById(accountId);
    }

    public void saveAccountStats(AccountStats stats) {
        try {
            String key = STATS_KEY_PREFIX + stats.getAccountId();
            redisTemplate.opsForValue().set(key, stats, Duration.ofDays(1));
            markAccountActive(stats.getAccountId());
        } catch (Exception e) {
            log.error("Failed to save account stats to Redis for account {}: {}", stats.getAccountId(), e.getMessage());
        }
    }

    public void saveRoundTripLeg(String fromAccount, String toAccount, String txnId, BigDecimal amount, LocalDateTime timestamp, int windowMinutes) {
        try {
            String key = ROUNDTRIP_KEY_PREFIX + fromAccount + ":" + toAccount;
            RedisRoundTripEntry entry = RedisRoundTripEntry.builder()
                    .transactionId(txnId)
                    .accountId(fromAccount)
                    .counterpartyId(toAccount)
                    .amount(amount)
                    .timestamp(timestamp)
                    .build();

            redisTemplate.opsForValue().set(key, entry, Duration.ofMinutes(windowMinutes));
        } catch (Exception e) {
            log.error("Failed to save round trip leg in Redis for {}->{}: {}", fromAccount, toAccount, e.getMessage());
        }
    }

    public Optional<RedisRoundTripEntry> getRoundTripLeg(String fromAccount, String toAccount) {
        try {
            String key = ROUNDTRIP_KEY_PREFIX + fromAccount + ":" + toAccount;
            Object val = redisTemplate.opsForValue().get(key);
            if (val instanceof RedisRoundTripEntry entry) {
                return Optional.of(entry);
            }
            if (val instanceof Map<?, ?> map) {
                // Defensive deserialization if stored as Map
                RedisRoundTripEntry entry = RedisRoundTripEntry.builder()
                        .transactionId((String) map.get("transactionId"))
                        .accountId((String) map.get("accountId"))
                        .counterpartyId((String) map.get("counterpartyId"))
                        .amount(map.get("amount") != null ? new BigDecimal(map.get("amount").toString()) : null)
                        .build();
                return Optional.of(entry);
            }
        } catch (Exception e) {
            log.error("Failed to get round trip leg from Redis for {}->{}: {}", fromAccount, toAccount, e.getMessage());
        }
        return Optional.empty();
    }

    public void removeRoundTripLeg(String fromAccount, String toAccount) {
        try {
            String key = ROUNDTRIP_KEY_PREFIX + fromAccount + ":" + toAccount;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Failed to remove round trip leg from Redis: {}", e.getMessage());
        }
    }

    public void addRecentRecipient(String accountId, String recipientId, int windowMinutes) {
        try {
            String key = RECIPIENTS_KEY_PREFIX + accountId;
            redisTemplate.opsForSet().add(key, recipientId);
            redisTemplate.expire(key, windowMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Failed to add recent recipient in Redis for {}: {}", accountId, e.getMessage());
        }
    }

    public Set<String> getRecentRecipients(String accountId) {
        try {
            String key = RECIPIENTS_KEY_PREFIX + accountId;
            Set<Object> members = redisTemplate.opsForSet().members(key);
            if (members != null) {
                Set<String> recipients = new HashSet<>();
                for (Object m : members) {
                    recipients.add(m.toString());
                }
                return recipients;
            }
        } catch (Exception e) {
            log.error("Failed to get recent recipients from Redis for {}: {}", accountId, e.getMessage());
        }
        return Collections.emptySet();
    }

    public void markAccountActive(String accountId) {
        try {
            redisTemplate.opsForSet().add(ACTIVE_ACCOUNTS_KEY, accountId);
        } catch (Exception e) {
            log.error("Failed to mark account active in Redis for {}: {}", accountId, e.getMessage());
        }
    }

    public Set<String> getActiveAccounts() {
        try {
            Set<Object> members = redisTemplate.opsForSet().members(ACTIVE_ACCOUNTS_KEY);
            if (members != null) {
                Set<String> accounts = new HashSet<>();
                for (Object m : members) {
                    accounts.add(m.toString());
                }
                return accounts;
            }
        } catch (Exception e) {
            log.error("Failed to get active accounts from Redis: {}", e.getMessage());
        }
        return Collections.emptySet();
    }
}
