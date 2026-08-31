package org.aezden.paymentservice.Model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private LedgerTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    protected LedgerEntry() {
    }

    public LedgerEntry(LedgerTransaction transaction, Account account, long amountCents) {
        this.transaction = transaction;
        this.account = account;
        this.amountCents = amountCents;
    }

    public UUID getId() {
        return id;
    }

    public LedgerTransaction getTransaction() {
        return transaction;
    }

    public Account getAccount() {
        return account;
    }

    public long getAmountCents() {
        return amountCents;
    }
}
