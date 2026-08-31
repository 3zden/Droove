package org.aezden.paymentservice.Model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_transactions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ledger_idempotency_key", columnNames = "idempotency_key")
})
public class LedgerTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LedgerTransactionType type;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerTransaction() {
    }

    public LedgerTransaction(LedgerTransactionType type, String idempotencyKey, UUID tripId, long amountCents) {
        this.type = type;
        this.idempotencyKey = idempotencyKey;
        this.tripId = tripId;
        this.amountCents = amountCents;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public LedgerTransactionType getType() {
        return type;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getTripId() {
        return tripId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
