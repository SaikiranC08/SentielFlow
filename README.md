# SentinelFlow — Real-Time Transaction Surveillance Engine

![Java 21](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot 3.3](https://img.shields.io/badge/Spring_Boot-3.3.3-brightgreen.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)
![Redis](https://img.shields.io/badge/Redis-7-red.svg)
![Resilience4j](https://img.shields.io/badge/Resilience4j-2.2.0-yellow.svg)
![WebSockets](https://img.shields.io/badge/WebSockets-STOMP-blueviolet.svg)
![OpenAPI](https://img.shields.io/badge/Swagger-OpenAPI_3-green.svg)

**SentinelFlow** is a backend system that monitors financial transaction streams and flags suspicious patterns in real time — built to demonstrate backend engineering depth for fintech, stock/crypto exchanges, and payment processing domains.

---

## 1. Problem Statement

Financial platforms — brokers, exchanges, payment processors, and lending apps — are required by regulators (such as SEBI/RBI) or internal risk teams to monitor transaction streams for suspicious activity. Two of the most critical patterns are:

1. **Abnormal Account Behavior**: An account suddenly transacting far more often, or for far larger amounts, than its historical norm.
2. **Circular Trading / Wash Trading**: Accounts moving money or assets back and forth (e.g. $A \rightarrow B \rightarrow A$) to fake activity or manipulate volume/price.

**SentinelFlow** implements both as a real-time, rule-based detection engine. It does not try to "predict fraud" with a black box — every flag comes with a **plain-English reason in Indian Rupees (₹ / INR)**, allowing human compliance officers to review flags quickly and transparently.

---

## 2. Industry Domain Fit

| Algorithm | Domain Fit | Rationale |
|---|---|---|
| **Algorithm 1 — Behavioral Anomaly Detection** | Everyone: Stock brokers, payment gateways, lending apps, exchanges | Every account-based fintech product has a "normal" baseline per user that account takeover or fraud breaks |
| **Algorithm 2 — Circular Trading Detection** | Stock/Crypto exchanges, trading brokers specifically | Applies where an *asset* or fund transfer is returned to inflate volume (SEBI circular trading compliance) |

---

## 3. Core Algorithms & Mathematical Computations

### Algorithm 1 — Behavioral Anomaly Detection

Learns each account's normal behavior incrementally without scanning historical logs ($O(1)$ constant time and space). Checks two metrics combined into one risk signal:
- **Single Transaction Check**: Ratio of current transaction amount vs. account's historical average transaction amount (`avg_amount`).
- **Daily Total Volume Check**: Ratio of today's cumulative transaction sum (`amount_sum_today`) vs. account's average daily total (`avg_daily_total`).

#### Dynamic Threshold Hierarchy
Thresholds resolve most-specific-first:
$$\text{Per-Account Override} \longrightarrow \text{Account-Type Default} \longrightarrow \text{Global Default}$$

| `account_type` | Multiplier Threshold | Reasoning |
|---|---|---|
| `SAVINGS_ACCOUNT` | **3.0x** | Low, stable volatility expected |
| `BUSINESS_ACCOUNT` | **6.0x** | Seasonal business volume spikes are normal |
| `TRADING_ACCOUNT` | **8.0x** | Naturally volatile, market-driven behavior |
| `HIGH_NET_WORTH` | **10.0x** | Large, irregular capital movements expected |

#### Mathematical Incremental Computations
1. **Running Mean Formula (Single Txn Average)**:
   $$\text{newAvgAmount} = \text{oldAvgAmount} + \frac{\text{currentAmount} - \text{oldAvgAmount}}{\text{totalHistoricalTxnCount}}$$
2. **Daily Folding Formula (Daily Total Average)**:
   When a new day starts (`today > lastTxnDate`), yesterday's completed sum is folded into `avgDailyTotal`:
   $$\text{newAvgDailyTotal} = \text{oldAvgDailyTotal} + \frac{\text{yesterdaySum} - \text{oldAvgDailyTotal}}{\text{totalActiveDays}}$$
3. **Cold Start Handling**:
   Accounts with fewer than 3 historical transactions skip the dynamic anomaly check to prevent division by near-zero or false flagging on brand new accounts.

---

### Algorithm 2 — Circular Trading Detection (Round-Trip & Multi-Hop)

Detects wash-trading patterns where Account A sends funds/assets to Account B, and B returns a similar amount back to A within a configurable window (e.g., 10 minutes).

1. **Amount-Similarity Check**: Only flags if the returned amount is within **~1–3%** of the original (e.g. ₹79,500.00 returned vs. ₹80,000.00 sent).
2. **Repeat Tracking & Severity Scaling**: Increments a durable `pair_repeat_counts` table in PostgreSQL. Severity scales automatically:
   - 1st repeat: `MEDIUM`
   - 2nd repeat: `HIGH`
   - 3+ repeats: `CRITICAL`
3. **Bounded 3-Hop Extension**: Checks capped multi-hop chains ($A \rightarrow B \rightarrow C \rightarrow A$) using recipient sets stored in Redis.

---

## 4. Architecture & Pipeline Overview

```
                      ┌─────────────────────────┐
                      │   Incoming Transaction  │
                      └────────────┬────────────┘
                                   │
                                   ▼
                     ┌───────────────────────────┐
                     │ 1. WRITE POSTGRES FIRST   │  ← Source of truth (transactions table)
                     └────────────┬──────────────┘
                                   │
                                   ▼
                     ┌───────────────────────────┐
                     │ 2. UPDATE account_stats   │  ← Atomic SQL update & running mean calculation
                     └────────────┬──────────────┘
                                   │
                                   ▼
                     ┌───────────────────────────┐
                     │ 3. WRITE-THROUGH REDIS    │  ← Fast path cache (stats:{accountId})
                     └────────────┬──────────────┘
                                   │
                                   ▼
                     ┌───────────────────────────┐
                     │ 4. RULE ENGINE EVALUATION │  ← Algorithm 1 + Algorithm 2 against rule_config
                     └────────────┬──────────────┘
                                   │
                           Flagged?│
                                   ▼
                     ┌───────────────────────────┐
                     │ 5. WRITE flagged_txns     │  ← Save flag to PostgreSQL
                     └────────────┬──────────────┘
                                   │
                                   ▼
                     ┌───────────────────────────┐
                     │ 6. WEBSOCKET STOMP PUSH   │  → Real-time broadcast (/topic/flags)
                     └───────────────────────────┘
```

### Resiliency & Cache Synchronization Strategy
- **Durable Storage First**: PostgreSQL is written first. Redis is a disposable speed layer.
- **Circuit Breaker Fallback**: All Redis calls are protected by Resilience4j `@CircuitBreaker(name = "redisCache")`. If Redis drops, system seamlessly falls back to direct PostgreSQL reads without throwing request errors.
- **Scheduled Reconciliation**: A `@Scheduled(fixedRate = 300000)` background worker runs every 5 minutes, compares active Redis account keys against PostgreSQL `account_stats`, and corrects cache drift.

---

## 5. Database Schema & Data Structures

### PostgreSQL Schemas

#### `transactions`
| Column | Type | Description |
|---|---|---|
| `id` | VARCHAR(64) PK | Transaction ID (e.g. `txn_1001`) |
| `account_id` | VARCHAR(64) | Sender account ID |
| `account_type` | VARCHAR(32) | Account risk type enum |
| `counterparty_id` | VARCHAR(64) | Recipient account ID |
| `amount` | NUMERIC(15,2) | Transaction amount in Rupees (₹) |
| `type` | VARCHAR(32) | `TRANSFER`, `TRADE`, or `PAYMENT` |
| `timestamp` | TIMESTAMP | Event timestamp |

#### `account_stats`
| Column | Type | Description |
|---|---|---|
| `account_id` | VARCHAR(64) PK | Account ID |
| `txn_count_today` | INT | Count of transactions today |
| `amount_sum_today` | NUMERIC(15,2) | Cumulative total amount today (₹) |
| `avg_amount` | NUMERIC(15,2) | Lifetime single transaction mean (₹) |
| `avg_daily_total` | NUMERIC(15,2) | Historical daily average sum (₹) |
| `max_txn_seen` | NUMERIC(15,2) | Largest transaction seen (₹) |
| `total_historical_txn_count` | BIGINT | Total historical transactions |
| `total_active_days` | INT | Total active days |
| `last_txn_date` | DATE | Date of last transaction |

#### `flagged_transactions`
| Column | Type | Description |
|---|---|---|
| `id` | VARCHAR(64) PK | Flag ID (e.g. `flg_a1b2c3d4`) |
| `transaction_id` | VARCHAR(64) | Associated transaction ID |
| `account_id` | VARCHAR(64) | Flagged account ID |
| `rule_name` | VARCHAR(64) | `AMOUNT_ANOMALY` or `ROUND_TRIP` |
| `severity` | VARCHAR(32) | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `reason` | VARCHAR(512) | Plain-English explanation formatted in Rupees |
| `created_at` | TIMESTAMP | Flag creation timestamp |

### Redis Key Patterns

| Key Pattern | Data Structure | Purpose | TTL |
|---|---|---|---|
| `stats:{accountId}` | Hash / Object | Cached account statistics | 24 Hours |
| `roundtrip:{A}:{B}` | String (JSON) | Leg 1 payload waiting for Leg 2 return | 10 Minutes |
| `recent_recipients:{accountId}` | Set | Recent recipients for 3-hop chain checks | 10 Minutes |
| `active_accounts` | Set | Active account IDs for background reconciliation | Rolling |

---

## 6. End-to-End Data Flow Example

**Scenario**: `acc_501` (`TRADING_ACCOUNT`, average transaction ₹7,000.00) sends **₹80,000.00** to `acc_777`. Five minutes later, `acc_777` sends **₹79,500.00** back to `acc_501`.

1. **10:02:11** — Transaction `txn_1001` (₹80,000.00) saved to PostgreSQL `transactions`.
2. `account_stats` updated: `avg_amount` = ₹7,000.00. Ratio = $\frac{80000}{7000} = 11.4x$.
3. Exceeds `TRADING_ACCOUNT` threshold (8.0x) $\rightarrow$ **Flagged, HIGH Severity**:
   > *"Single transaction ₹80,000.00 is 11.4x this account's average transaction of ₹7,000.00 (threshold: 8.0x for TRADING_ACCOUNT)"*
4. Leg saved in Redis `roundtrip:acc_501:acc_777` with 10-minute TTL. Flag pushed over WebSocket `/topic/flags`.
5. **10:06:45** — Reverse transaction `txn_1002` (₹79,500.00) arrives from `acc_777` to `acc_501`.
6. Redis lookup finds `roundtrip:acc_501:acc_777`. Amount variance ratio = $\frac{|79500 - 80000|}{80000} = 0.6\%$ (within 3% tolerance).
7. `pair_repeat_counts` incremented to 3 in PostgreSQL $\rightarrow$ **Flagged, CRITICAL Severity**:
   > *"Round-trip circular trade detected (#3 repeat) between acc_777 and acc_501: returned ₹79,500.00 is within 0.6% of original ₹80,000.00 (window: 10 mins)"*

---

## 7. REST APIs & OpenAPI Documentation

Interactive Swagger UI is available at **`http://localhost:8080/swagger-ui.html`**.

### Key Endpoints

#### Ingest Transaction
```http
POST /api/v1/transactions
Content-Type: application/json

{
  "accountId": "acc_501",
  "accountType": "TRADING_ACCOUNT",
  "counterpartyId": "acc_777",
  "amount": 80000.00,
  "type": "TRANSFER"
}
```

#### Sample Response Payload
```json
{
  "id": "txn_a1b2c3d4",
  "accountId": "acc_501",
  "accountType": "TRADING_ACCOUNT",
  "counterpartyId": "acc_777",
  "amount": 80000.00,
  "type": "TRANSFER",
  "timestamp": "2026-08-26T10:02:11",
  "flagged": true,
  "flags": [
    {
      "id": "flg_e5f6g7h8",
      "transactionId": "txn_a1b2c3d4",
      "accountId": "acc_501",
      "ruleName": "AMOUNT_ANOMALY",
      "severity": "HIGH",
      "reason": "Single transaction ₹80,000.00 is 11.4x this account's average transaction of ₹7,000.00 (threshold: 8.0x for TRADING_ACCOUNT)",
      "createdAt": "2026-08-26T10:02:11"
    }
  ]
}
```

#### Fetch All Flagged Transactions
```http
GET /api/v1/flags
```

#### Fetch Rule Configurations
```http
GET /api/v1/rules
```

---

## 8. Real-Time WebSocket Push (STOMP / SockJS)

Clients can subscribe to `/topic/flags` to receive real-time flag push broadcasts without polling:

```javascript
// Connect via SockJS & STOMP Client
const socket = new SockJS('http://localhost:8080/ws-sentinelflow');
const stompClient = Stomp.over(socket);

stompClient.connect({}, () => {
  stompClient.subscribe('/topic/flags', (message) => {
    const flag = JSON.parse(message.body);
    console.log('Real-time surveillance alert received:', flag);
  });
});
```

---

## 9. Local Setup & Quick Start

### Prerequisites
- Java 21 JDK
- Docker & Docker Compose

### Step 1: Start Infrastructure (PostgreSQL & Redis)
```bash
docker-compose up -d
```

### Step 2: Build & Run SentinelFlow Application
```bash
./gradlew bootRun
```

### Step 3: Run Automated Test Seeder
In a separate terminal, execute the demo script to fire sample transactions:
```bash
./scripts/seed-data.sh
```

---

## 10. Verification & Unit Testing

Run unit tests covering rule calculations, cold-start handling, and circular trading logic:

```bash
./gradlew test
```

---

## 11. Stated System Trade-Offs & Limitations

- **Non-Atomic Dual-Write**: PostgreSQL and Redis writes are executed in sequence rather than a 2PC distributed transaction. The `@Scheduled` reconciliation job resolves any temporary consistency drift.
- **Narrow Sub-Millisecond Race Window**: Reverse transfers arriving in the exact same millisecond rely on Redis key existence; a production setup at extreme throughput would use Redis `MULTI`/`EXEC` or distributed locks.
- **Capped 3-Hop Search**: Unbounded cycle detection across $N$ hops is an NP-hard graph search better suited for graph databases (e.g., Neo4j).
- **Synchronous Execution Model**: Direct synchronous processing within HTTP lifecycle is chosen for simplicity and low operational overhead (Kafka messaging is omitted intentionally).

---

## 12. License & Author

Developed by **Saikiran Chevula** — Built to demonstrate production-grade backend engineering practices in Java, Spring Boot, microservice resilience, and real-time surveillance algorithms.
