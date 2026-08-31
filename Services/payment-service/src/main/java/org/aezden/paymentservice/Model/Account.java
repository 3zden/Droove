package org.aezden.paymentservice.Model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallet_owner", columnNames = {"account_type", "owner_id"}),
        @UniqueConstraint(name = "uk_escrow_trip", columnNames = {"account_type", "trip_id"})
})
public class Account {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    private AccountType type;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "balance_cents", nullable = false)
    private long balanceCents;

    protected Account() {
    }

    private Account(UUID id, AccountType type, UUID ownerId, UUID tripId) {
        this.id = id;
        this.type = type;
        this.ownerId = ownerId;
        this.tripId = tripId;
    }

    public static Account wallet(UUID ownerId) {
        return new Account(UUID.randomUUID(), AccountType.WALLET, ownerId, null);
    }

    public static Account escrow(UUID tripId) {
        return new Account(UUID.randomUUID(), AccountType.ESCROW, null, tripId);
    }

    public static Account system(UUID id, AccountType type) {
        return new Account(id, type, null, null);
    }

    public UUID getId() {
        return id;
    }

    public AccountType getType() {
        return type;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getTripId() {
        return tripId;
    }

    public long getBalanceCents() {
        return balanceCents;
    }

    public void apply(long deltaCents) {
        balanceCents = Math.addExact(balanceCents, deltaCents);
    }
}
