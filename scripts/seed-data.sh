#!/usr/bin/env bash

# SentinelFlow Seed Data Script
# Posts sample transaction events to trigger Algorithm 1 (Anomaly) and Algorithm 2 (Circular Trading).

BASE_URL="http://localhost:8080/api/v1"

echo "=========================================================="
echo " SentinelFlow — Surveillance Engine Demo Transaction Seeder "
echo "=========================================================="
echo ""

echo "[1/3] Posting single large transaction to trigger Algorithm 1 (Behavioral Anomaly)..."
echo "Scenario: acc_501 (TRADING_ACCOUNT) average is ₹7,000.00. Sending ₹80,000.00 (11.4x average)..."
curl -s -X POST "$BASE_URL/transactions" \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "acc_501",
    "accountType": "TRADING_ACCOUNT",
    "counterpartyId": "acc_777",
    "amount": 80000.00,
    "type": "TRANSFER"
  }' | jq .

echo ""
echo "----------------------------------------------------------"
echo "[2/3] Waiting 2 seconds before posting return leg..."
sleep 2

echo "[3/3] Posting reverse leg to trigger Algorithm 2 (Circular Trading)..."
echo "Scenario: acc_777 sends ₹79,500.00 back to acc_501 (within 1% of ₹80,000.00 within 10 mins)..."
curl -s -X POST "$BASE_URL/transactions" \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "acc_777",
    "accountType": "TRADING_ACCOUNT",
    "counterpartyId": "acc_501",
    "amount": 79500.00,
    "type": "TRANSFER"
  }' | jq .

echo ""
echo "=========================================================="
echo " Fetching all generated flags from SentinelFlow..."
echo "=========================================================="
curl -s -X GET "$BASE_URL/flags" | jq .
