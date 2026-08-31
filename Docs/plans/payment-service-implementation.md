# Payment service implementation

## What was implemented

`Services/payment-service` is now a Postgres-backed, double-entry ledger on port
`8105`. It provides:

- rider/driver wallets with a cached balance;
- top-ups from a system external-funding account;
- per-trip escrow accounts;
- 80/20 driver/platform settlement on completion;
- refunds for held funds on cancellation;
- append-only ledger transactions and entries;
- deterministic Kafka idempotency keys and replay-safe event handling;
- account locking in UUID order to prevent transfer deadlocks.

The frontend payment client was also wired to the existing standalone service
contract and sends the bearer token expected by the other clients.

## HTTP contract

The service accepts both the standalone paths and the gateway-prefixed paths:

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| `GET` | `/wallet` (or `/api/payments/wallet`) | authenticated identity | `{ "balanceCents": 1234 }` |
| `POST` | `/wallet/topup` (or `/api/payments/wallet/topup`) | `{ "amountCents": 5000 }` | updated wallet balance |
| `GET` | `/ledger/trip/{tripId}` (or `/api/payments/ledger/trip/{tripId}`) | authenticated identity | transaction history |

Identity is read from the gateway's trusted `X-User-Id` header. For local
standalone development, a signed HS256 `Authorization: Bearer ...` JWT is also
accepted using `JWT_SECRET`. A top-up may include an `Idempotency-Key`; without
one, each request is intentionally a new deposit.

## Ledger model

- `accounts` contains wallet, escrow, external-funding, and platform-revenue
  accounts. Wallet and escrow balances are never allowed below zero.
- `ledger_transactions` stores the business instruction and its unique
  idempotency key.
- `ledger_entries` stores the signed postings (`credit > 0`, `debit < 0`).
  Every transaction is written atomically and its postings must sum to zero.
- The external-funding account is the deliberate exception to the
  non-negative rule: it represents simulated money entering the system.
- Entry and transaction rows have no update/delete API; corrections must be
  represented by additional reversing transactions.

Kafka consumes `TRIP_MATCHED`, `TRIP_COMPLETED`, and both spellings of
`TRIP_CANCELLED`/`TRIP_CANCELED` from `trip-events` (and the current
`trip-topic` producer name for compatibility). Event keys are:

```
{tripId}:ESCROW_HOLD
{tripId}:DISBURSE
{tripId}:REFUND
```

Cancellation before a hold is a successful no-op. Completion without an escrow
is rejected so Kafka can retry rather than silently losing a payout. The odd
cent in an 80/20 split is assigned using exact integer arithmetic; the driver
share is rounded and the platform share is derived by subtraction.

## Validation

`./mvnw test` passes all four payment-service tests, including top-up
idempotency, balanced postings, insufficient-funds protection, odd-cent
settlement, and cancellation no-op behavior. The focused frontend
`payments.test.ts` also passes.

## Remaining limitations

The service does not call a real payment processor, expose payout withdrawal,
or reconcile cached balances periodically against a full ledger sum. The
gateway is still responsible for production JWT validation and stripping
client-supplied identity headers; the local JWT fallback exists only because
the gateway is not yet complete. Ledger access currently authenticates the
caller but does not yet verify that the caller is the rider/driver associated
with the requested trip. Production additions would include authorization
lookups, multi-currency/fee rules, chargebacks, and accounting-period close.
