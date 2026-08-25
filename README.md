sentinelFlow — Real-Time Transaction Surveillance Engine

A backend system that monitors transaction streams and flags suspicious patterns in real time — built to demonstrate backend engineering depth for fintech, trading, and payments domains.

---

## 1. Problem Statement

Financial platforms — brokers, exchanges, payment processors, lending apps — are required (by regulators or by their own risk teams) to monitor transaction activity for suspicious patterns. Two of the most common patterns are:

1. **Abnormal account behavior** — an account suddenly transacting far more often, or for far larger amounts, than its own historical normal.
2. **Circular trading / wash trading** — two accounts moving money or assets back and forth to fake activity or manipulate volume/price.

TradeGuard implements both as a real-time, rule-based detection engine. It does not try to "predict fraud" with a black box — every flag comes with a plain-English reason, because in real compliance systems, a human reviews flags; the system's job is to surface patterns fast and explainably, not to make final judgments.

**This is a portfolio/demo project, not a hosted product.** It is meant to be cloned, run locally, and evaluated by an engineer — the same model as a package a bank or fintech company would run inside their own infrastructure, not a SaaS.

---

## 2. Who This Is For (and why the same engine fits multiple domains)

| Algorithm | Best fit | Why |
|---|---|---|
| **Algorithm 1 — Behavioral Anomaly Detection** | Everyone: brokers, payment platforms, lending apps, exchanges | Every account-based fintech product has a "normal" pattern per user that fraud/takeover breaks |
| **Algorithm 2 — Circular Trading Detection** | Brokers, crypto/stock exchanges specifically | Only applies where an *asset* is bought/sold; not meaningful for plain money transfers |

At a company like a broker or exchange, both algorithms apply. At a pure payments/lending company, only Algorithm 1 is relevant — and saying so explicitly (rather than forcing both) shows an understanding of each business, not just the code.

---

## 3. Algorithm 1 — Behavioral Anomaly Detection

**Plain-English idea:** the system learns each account's own normal behavior — how often it transacts, how much it typically moves — and flags anything that breaks sharply from that pattern.

**What it checks, combined into one risk signal (not separate pass/fail gates):**
- Transaction **count** today vs. this account's average daily count
- **Total amount** moved today vs. this account's average daily total
- **Single largest transaction** today vs. this account's historical largest

Checking these *together* closes a real gap: an attacker who stays under the count threshold but sends unusually large amounts (or vice versa) would slip past checks done in isolation. A combined score catches both the "many small suspicious transactions" case and the "one big unusual transaction" case.

**Thresholds are configurable, not hardcoded** — what counts as "abnormal" is a business decision:

| account_type | multiplier_threshold | reasoning |
|---|---|---|
| SAVINGS_ACCOUNT | 3.0x | stable, low-volatility behavior expected |
| BUSINESS_ACCOUNT | 6.0x | seasonal spikes are normal |
| TRADING_ACCOUNT | 8.0x | naturally volatile, market-driven |
| HIGH_NET_WORTH | 10.0x | large, irregular movements are normal |

Config resolves most-specific-first: per-account override → account-type default → global default.


How the threshold is actually calculated in the backend (Algorithm 1)

The threshold isn't a fixed number you hardcode — it's calculated dynamically from each account's own history, then compared using a multiplier pulled from rule_config. Here's the actual computation: every time a transaction comes in, the backend reads account_stats for that account (avg_amount, avg_daily_total, txn_count_today, amount_sum_today), then computes two ratios — currentTransactionAmount / avg_amount for the single-transaction check, and amount_sum_today / avg_daily_total for the daily-total check. It then looks up the applicable multiplier_threshold from rule_config (checking account-specific override first, then account_type, then global default) and simply compares: if either ratio exceeds that multiplier, it's flagged. The "average" itself is maintained incrementally, not recalculated from scratch each time — every new transaction updates avg_amount using a running mean formula like newAvg = oldAvg + (newAmount - oldAvg) / newCount, so the backend never needs to re-scan historical transactions to know what "normal" looks like; it just reads one row from account_stats (or Redis) and does simple arithmetic. For the very first transaction on a brand-new account (cold start, no history yet), there's no meaningful average to compare against, so the backend should explicitly skip the anomaly check for that first transaction (or compare against a global fallback average) rather than either falsely flagging it or dividing by a near-zero baseline. This is a genuinely important design point to be able to explain, because it's the actual "engine" of Algorithm 1 — everything else (Redis, Postgres, WebSocket) is just infrastructure around this core calculation. Algorithm 2 doesn't need this kind of computed threshold at all — it's a direct comparison (does a matching reverse transaction exist within a time window, are the amounts within X% of each other), not a statistical deviation from a baseline, so there's no "threshold calculation" step to add there beyond the config lookup itself.

---

## 4. Algorithm 2 — Circular Trading Detection (Round-Trip)

**Plain-English idea:** if Account A sends money/assets to Account B, and B sends a very similar amount back to A shortly after, that pair may be faking trading activity rather than doing genuine, independent trades. In Indian markets, SEBI refers to this pattern as **circular trading**.

**What makes this implementation deeper than a naive check:**
1. **Amount-similarity check** — only flags if the returned amount is within ~1-3% of the original (real wash trades return nearly the same amount; unrelated transactions rarely do)
2. **Repeat tracking** — a single occurrence may be coincidental (a genuine reversal/refund); the same pair repeating this pattern is a much stronger signal. Severity scales with `repeat_count`.
3. **Bounded multi-hop extension** — beyond the direct A→B→A case, the system optionally checks a capped 3-hop chain (A→B→C→A) using each account's recent-recipients set. Depth is intentionally capped — unbounded cycle search is expensive at scale and is a known harder problem (related to "layering" in AML terminology); a production system would use graph-database tooling for the general case.

**Known, stated limitation:** a determined actor using more hops (A→B→C→D, no return) evades this detection entirely. This rule catches the common, lower-effort pattern, not all possible layering schemes — that trade-off is intentional and documented, not an oversight.

---

## 5. Architecture Overview

```
                    ┌─────────────────────┐
                    │   Incoming Transaction │
                    └───────────┬─────────┘
                                │
                                ▼
                  ┌──────────────────────────┐
                  │  1. WRITE POSTGRES FIRST   │   ← source of truth, always first
                  │     (transactions table)   │
                  └───────────┬───────────────┘
                                │
                                ▼
                  ┌──────────────────────────┐
                  │  2. UPDATE account_stats   │   ← atomic SQL UPDATE (no lost updates)
                  │     (Postgres, durable)    │
                  └───────────┬───────────────┘
                                │
                                ▼
                  ┌──────────────────────────┐
                  │  3. UPDATE REDIS CACHE     │   ← write-through, fast path
                  │     (stats:{accountId})    │
                  └───────────┬───────────────┘
                                │
                                ▼
                  ┌──────────────────────────┐
                  │  4. RULE ENGINE EVALUATES  │
                  │     Algorithm 1  +  Algorithm 2 │
                  │     against rule_config     │
                  └───────────┬───────────────┘
                                │
                        flagged? │
                                ▼
                  ┌──────────────────────────┐
                  │  5. WRITE flagged_transactions │
                  └───────────┬───────────────┘
                                │
                                ▼
                  ┌──────────────────────────┐
                  │  6. PUSH VIA WEBSOCKET     │   → dashboard updates instantly,
                  │     (/topic/flags)         │      no polling, no refresh
                  └──────────────────────────┘
```

**Why Postgres is written first, always:** Postgres is the durable source of truth; Redis is a disposable speed layer. If Redis fails mid-write, no financial data is lost — only the real-time check temporarily falls back to Postgres directly until Redis recovers. This is the standard "durable storage first, cache second" principle.

### Redis Sync Strategy: Write-Through + Cache-Aside Recovery

- **Normal operation → Write-Through:** every write updates Postgres and Redis together, so both stay current during normal traffic.
- **Redis failure/restart → Cache-Aside:** if Redis is empty or unreachable, the app reads directly from Postgres, serves the request (slower but correct), and repopulates Redis on the way — the system degrades gracefully, never breaks.
- **Drift protection → Scheduled Reconciliation:** a `@Scheduled` job runs periodically, compares Redis vs. Postgres for recently active accounts (batched, not per-account), and re-syncs on mismatch — Postgres always wins as source of truth. This closes the small consistency gap that write-through alone doesn't fully guarantee (the two writes aren't atomic across systems).
- **Resilience:** Redis calls are wrapped with Resilience4j's `@CircuitBreaker`. If Redis starts failing repeatedly, the circuit opens and the system automatically falls back to Postgres reads instead of hanging or erroring on every request.

---

## 6. Database Schema (with sample data)

### `transactions` — every event, source of truth

| id | account_id | counterparty_id | amount | type | timestamp |
|---|---|---|---|---|---|
| txn_1001 | acc_501 | acc_777 | 80000.00 | TRANSFER | 2026-08-29 10:02:11 |
| txn_1002 | acc_777 | acc_501 | 79500.00 | TRANSFER | 2026-08-29 10:06:45 |
| txn_1003 | acc_888 | acc_401 | 1500.00 | TRADE | 2026-08-29 10:23:01 |

### `account_stats` — running "normal behavior" per account (Algorithm 1)

| account_id | txn_count_today | amount_sum_today | avg_amount | avg_daily_total | max_txn_seen |
|---|---|---|---|---|---|
| acc_501 | 3 | 92000.00 | 7000.00 | 70000.00 | 15000.00 |
| acc_888 | 40 | 48000.00 | 1200.00 | 48000.00 | 5000.00 |

### `rule_config` — dynamic, business-configurable thresholds

| id | rule_name | scope | scope_value | multiplier_threshold | window_minutes |
|---|---|---|---|---|---|
| 1 | AMOUNT_ANOMALY | GLOBAL | NULL | 3.0 | 1440 |
| 2 | AMOUNT_ANOMALY | ACCOUNT_TYPE | TRADING_ACCOUNT | 8.0 | 1440 |
| 3 | ROUND_TRIP | ACCOUNT_TYPE | TRADING_ACCOUNT | NULL | 10 |

### `pair_repeat_counts` — how often a pair round-trips (Algorithm 2 severity)

| account_a | account_b | repeat_count | last_occurred |
|---|---|---|---|
| acc_501 | acc_777 | 3 | 2026-08-29 10:06:45 |

### `flagged_transactions` — final output, what the demo shows

| id | transaction_id | rule_name | severity | reason | created_at |
|---|---|---|---|---|---|
| flg_1 | txn_1001 | AMOUNT_ANOMALY | HIGH | single txn 11.4x this account's average | 2026-08-29 10:02:11 |
| flg_2 | txn_1002 | ROUND_TRIP | HIGH | 3rd repeat between this pair, amounts within 1% | 2026-08-29 10:06:45 |

---

## 7. Redis Data Structures

| Purpose | Key pattern | Type | TTL |
|---|---|---|---|
| Running count/amount per account | `stats:{accountId}` | Hash | daily reset |
| Waiting for round-trip reverse leg | `roundtrip:{A}:{B}` | String (JSON) | 10 min (window) |
| Recent recipients (for 3-hop check) | `recent_recipients:{accountId}` | Set | 10 min |
| Recently active accounts (for reconciliation) | `active_accounts` | Set | rolling |

---

## 8. Data Flow — Worked Example

**Scenario:** acc_501 sends ₹80,000 to acc_777 (10:02:11). Five minutes later, acc_777 sends ₹79,500 back (10:06:45).

1. `txn_1001` written to `transactions` (Postgres, first)
2. `account_stats` for acc_501 updated atomically: count 2→3, sum ₹12,000→₹92,000
3. Redis `stats:acc_501` updated to match (write-through)
4. Redis `roundtrip:acc_501:acc_777` set with 10-min TTL, holding the ₹80,000/timestamp
5. Algorithm 1 check: single transaction is 11.4x this account's average → **flagged, HIGH**
6. Flag written to `flagged_transactions`, pushed via WebSocket to any connected dashboard
7. At 10:06:45, `txn_1002` arrives (acc_777 → acc_501)
8. Redis lookup finds `roundtrip:acc_501:acc_777` → amounts compared (₹79,500 vs ₹80,000, within 1%) → match confirmed
9. `pair_repeat_counts` incremented for this pair (now 3rd occurrence) — Postgres, durable, survives Redis restarts
10. Algorithm 2 check: repeat count 3 → **flagged, HIGH severity** (repeated pattern, not a one-off)
11. Flag written, pushed via WebSocket

---

## 9. Frontend — Real-Time Dashboard

The dashboard is a single React page showing **both algorithms' flags together**, differentiated by the `rule_name` and `type` fields already present on each record — not by building separate UIs. The same seed data set is simply relabeled (`TRADE` vs `PAYMENT` vs `TRANSFER`) depending on which company's problem is being demoed; the code never changes, only the narration does.

**Live updates via WebSocket (STOMP over SockJS):**

```java
// Backend: broadcast the moment a flag is created
messagingTemplate.convertAndSend("/topic/flags", newFlag);
```

```javascript
// Frontend: subscribe once, table updates instantly, no polling
stompClient.subscribe('/topic/flags', (message) => {
  const newFlag = JSON.parse(message.body);
  setFlags(prev => [newFlag, ...prev]);
});
```

**Sample WebSocket payload:**
```json
{
  "id": "flg_2",
  "accountId": "acc_501",
  "rule": "ROUND_TRIP",
  "severity": "HIGH",
  "reason": "3rd repeat, matching amounts within 1%",
  "timestamp": "2026-08-29T10:06:45"
}
```

The demo: fire a seeded suspicious transaction via Swagger/Postman → a new row appears on the dashboard instantly, with no refresh — the visible proof that this is a live pipeline, not a static report.

---

## 10. Presenting This to Different Companies

| Talking to... | Lead with | Framing |
|---|---|---|
| **Broker / exchange** (e.g., trading platforms) | Algorithm 2 (Circular Trading) + Algorithm 1 | "Flags wash-trading patterns and abnormal account activity — the kind of monitoring SEBI requires." |
| **Payments / lending fintech** | Algorithm 1 only | "Flags fraud-pattern account behavior — sudden spikes in transaction count or amount versus that account's own history. Circular trading detection doesn't apply here since there's no asset being traded, so I scoped it out for this use case." |

Explicitly *not* pitching Algorithm 2 to a payments company (rather than forcing it) is itself part of the pitch — it shows the project was designed with real domain boundaries in mind, not a one-size-fits-all demo.

---

## 11. Known Limitations (stated intentionally, not gaps to hide)

- **Dual-write is not atomic.** Postgres and Redis writes are two separate operations; a crash between them causes brief staleness, closed by the reconciliation job rather than a distributed transaction (outbox pattern / 2PC would be the production-grade fix).
- **Round-trip detection has a narrow race window** if both legs of a transfer arrive within the same millisecond — acceptable at this scale, would need Redis `MULTI`/`EXEC` or a distributed lock in production.
- **Circular trading detection is bounded to ~3 hops.** Longer layering chains (A→B→C→D, no return) are not caught — full graph-cycle detection is a known harder problem, better suited to a graph database at scale.
- **Kafka/async decoupling deliberately not used.** At this project's scale, a synchronous flow is simpler and equally correct; Kafka would add operational complexity without a throughput problem to justify it. (Already demonstrated separately in a prior project — Conversion Analytics.)
- **Security hardening scoped out of week 1** — see below for what a production version requires.

---

## 12. Production Considerations (documented, not built, for this scope)

- **Encryption in transit** — TLS/HTTPS for all API and WebSocket traffic, even on internal bank networks.
- **No sensitive data in logs** — mask account IDs (e.g., last 4 digits only); avoid logging full transaction payloads, per PCI-DSS-adjacent practice.
- **Role-based access control on flagged data** — `flagged_transactions` is the most sensitive output of the system and should be restricted to compliance/risk roles, not general engineering access.
- **Append-only audit trail** — flags should never be edited or deleted; corrections should be new records referencing the original, to preserve evidentiary integrity.
- **Regulatory data retention** — financial surveillance data typically has mandated multi-year retention, unlike generic application data that can simply be archived or deleted.
- **Data minimization** — the engine only needs account IDs and transaction data, not full customer PII, following least-privilege principles.

---

## 13. Tech Stack

- **Backend:** Spring Boot, Spring Security (JWT via Kong Gateway, reused from prior projects)
- **Database:** PostgreSQL (source of truth)
- **Cache:** Redis (real-time speed layer)
- **Resilience:** Resilience4j (`@CircuitBreaker` for Redis fallback)
- **Real-time push:** WebSocket (STOMP over SockJS)
- **Frontend:** React, Tailwind CSS
- **Containerization:** Docker Compose

---

## 14. Running Locally

```bash
docker-compose up -d          # starts Postgres, Redis, and the backend
./scripts/seed-data.sh        # loads sample transactions (see /seed for variants)
```

Swagger UI available at `http://localhost:8080/swagger-ui.html` for triggering transactions manually during a demo. Dashboard available at `http://localhost:3000`.

---

## 15. What I'd Build Next

- Outbox pattern for guaranteed Postgres↔Redis consistency
- Graph-database-backed cycle detection for unbounded layering chains
- Full running-variance (Welford's algorithm) for statistically rigorous outlier detection, beyond the current mean-based approximation
- Configurable rule admin UI (currently `rule_config` is edited directly)
