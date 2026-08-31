package org.aezden.paymentservice.Service;

import jakarta.transaction.Transactional;
import org.aezden.paymentservice.Exception.InsufficientFundsException;
import org.aezden.paymentservice.Model.*;
import org.aezden.paymentservice.Repository.AccountRepository;
import org.aezden.paymentservice.Repository.LedgerEntryRepository;
import org.aezden.paymentservice.Repository.LedgerTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.*;

@Service
public class LedgerService {
    private static final UUID EXTERNAL_FUNDING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLATFORM_REVENUE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    public LedgerService(AccountRepository accountRepository,
                         LedgerTransactionRepository transactionRepository,
                         LedgerEntryRepository entryRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional
    public long walletBalance(UUID userId) {
        return accountRepository.findByTypeAndOwnerId(AccountType.WALLET, userId)
                .map(this::reconciledBalance)
                .orElse(0L);
    }

    @Transactional
    public long topUp(UUID userId, long amountCents, String requestId) {
        requirePositive(amountCents);
        String key = "TOPUP:" + userId + ":" +
                (requestId == null || requestId.isBlank() ? UUID.randomUUID() : requestId);
        LedgerTransaction existing = transactionRepository.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            return walletBalance(userId);
        }

        Account wallet = wallet(userId);
        Account external = systemAccount(EXTERNAL_FUNDING_ID, AccountType.EXTERNAL_FUNDING);
        transfer(key, LedgerTransactionType.TOPUP, null, amountCents, external, wallet);
        return wallet.getBalanceCents();
    }

    @Transactional
    public void handle(TripEventType event, UUID tripId, UUID riderId, UUID driverId, long fare) {
        switch (event) {
            case MATCHED -> hold(tripId, riderId, fare);
            case COMPLETED -> disburse(tripId, driverId);
            case CANCELLED -> refund(tripId, riderId);
        }
    }

    @Transactional
    public List<LedgerTransaction> tripTransactions(UUID tripId) {
        return transactionRepository.findAllByTripIdOrderByCreatedAtAsc(tripId);
    }

    private void hold(UUID tripId, UUID riderId, long fare) {
        requirePositive(fare);
        String key = tripId + ":ESCROW_HOLD";
        if (transactionRepository.findByIdempotencyKey(key).isPresent()) {
            return;
        }
        Account escrow = escrow(tripId);
        Account riderWallet = wallet(riderId);
        transfer(key, LedgerTransactionType.ESCROW_HOLD, tripId, fare, riderWallet, escrow);
    }

    private void disburse(UUID tripId, UUID driverId) {
        if (driverId == null) {
            throw new IllegalArgumentException("A completed trip must have a driver");
        }
        String key = tripId + ":DISBURSE";
        if (transactionRepository.findByIdempotencyKey(key).isPresent()) {
            return;
        }
        Account escrow = accountRepository.findByTypeAndTripId(AccountType.ESCROW, tripId)
                .orElseThrow(() -> new IllegalStateException("No escrow exists for trip " + tripId));
        long fare = escrow.getBalanceCents();
        requirePositive(fare);
        long driverShare = roundedEightyPercent(fare);
        long platformShare = Math.subtractExact(fare, driverShare);
        Account driverWallet = wallet(driverId);
        Account platform = systemAccount(PLATFORM_REVENUE_ID, AccountType.PLATFORM_REVENUE);
        transfer(key, LedgerTransactionType.DISBURSE, tripId, fare, escrow,
                List.of(driverWallet, platform), List.of(driverShare, platformShare));
    }

    private void refund(UUID tripId, UUID riderId) {
        String key = tripId + ":REFUND";
        if (transactionRepository.findByIdempotencyKey(key).isPresent()) {
            return;
        }
        Optional<Account> escrowAccount = accountRepository.findByTypeAndTripId(AccountType.ESCROW, tripId);
        if (escrowAccount.isEmpty() || escrowAccount.get().getBalanceCents() == 0) {
            return;
        }
        if (riderId == null) {
            throw new IllegalArgumentException("A refund requires a rider");
        }
        Account escrow = escrowAccount.get();
        long fare = escrow.getBalanceCents();
        Account riderWallet = wallet(riderId);
        transfer(key, LedgerTransactionType.REFUND, tripId, fare, escrow, riderWallet);
    }

    private LedgerTransaction transfer(String key, LedgerTransactionType type, UUID tripId,
                                       long amount, Account source, Account target) {
        return transfer(key, type, tripId, amount, source, List.of(target), List.of(amount));
    }

    private LedgerTransaction transfer(String key, LedgerTransactionType type, UUID tripId,
                                       long amount, Account source, List<Account> targets,
                                       List<Long> targetAmounts) {
        if (transactionRepository.findByIdempotencyKey(key).isPresent()) {
            return transactionRepository.findByIdempotencyKey(key).orElseThrow();
        }
        if (targets.size() != targetAmounts.size() || targetAmounts.stream().mapToLong(Long::longValue).sum() != amount) {
            throw new IllegalArgumentException("Ledger transfer does not balance");
        }

        List<Account> all = new ArrayList<>();
        all.add(source);
        all.addAll(targets);
        all.stream().distinct().sorted(Comparator.comparing(Account::getId))
                .forEach(account -> accountRepository.findByIdForUpdate(account.getId())
                        .orElseThrow(() -> new IllegalStateException("Account disappeared: " + account.getId())));

        Account lockedSource = accountRepository.findByIdForUpdate(source.getId())
                .orElseThrow(() -> new IllegalStateException("Source account disappeared"));
        List<Account> lockedTargets = targets.stream()
                .map(account -> accountRepository.findByIdForUpdate(account.getId())
                        .orElseThrow(() -> new IllegalStateException("Target account disappeared")))
                .toList();
        if (lockedSource.getType() != AccountType.EXTERNAL_FUNDING && lockedSource.getBalanceCents() < amount) {
            throw new InsufficientFundsException();
        }
        LedgerTransaction alreadyProcessed = transactionRepository.findByIdempotencyKey(key).orElse(null);
        if (alreadyProcessed != null) {
            return alreadyProcessed;
        }

        LedgerTransaction transaction = transactionRepository.save(
                new LedgerTransaction(type, key, tripId, amount));
        List<LedgerEntry> entries = new ArrayList<>();
        lockedSource.apply(Math.negateExact(amount));
        entries.add(new LedgerEntry(transaction, lockedSource, Math.negateExact(amount)));
        for (int i = 0; i < lockedTargets.size(); i++) {
            long targetAmount = targetAmounts.get(i);
            lockedTargets.get(i).apply(targetAmount);
            entries.add(new LedgerEntry(transaction, lockedTargets.get(i), targetAmount));
        }
        if (entries.stream().mapToLong(LedgerEntry::getAmountCents).sum() != 0) {
            throw new IllegalStateException("Ledger entries must sum to zero");
        }
        entryRepository.saveAll(entries);
        return transaction;
    }

    private Account wallet(UUID ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("Wallet owner is required");
        }
        return accountRepository.findByTypeAndOwnerId(AccountType.WALLET, ownerId)
                .orElseGet(() -> accountRepository.save(Account.wallet(ownerId)));
    }

    private Account escrow(UUID tripId) {
        return accountRepository.findByTypeAndTripId(AccountType.ESCROW, tripId)
                .orElseGet(() -> accountRepository.save(Account.escrow(tripId)));
    }

    private Account systemAccount(UUID id, AccountType type) {
        return accountRepository.findById(id)
                .orElseGet(() -> accountRepository.save(Account.system(id, type)));
    }

    private long reconciledBalance(Account account) {
        long derived = entryRepository.balanceForAccount(account.getId());
        if (derived != account.getBalanceCents()) {
            account.apply(Math.subtractExact(derived, account.getBalanceCents()));
        }
        return derived;
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amountCents must be greater than zero");
        }
    }

    private static long roundedEightyPercent(long fare) {
        return BigInteger.valueOf(fare).multiply(BigInteger.valueOf(80))
                .add(BigInteger.valueOf(50)).divide(BigInteger.valueOf(100)).longValueExact();
    }

    public enum TripEventType {
        MATCHED, COMPLETED, CANCELLED
    }
}
