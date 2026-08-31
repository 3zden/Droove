package org.aezden.paymentservice;

import org.aezden.paymentservice.Exception.InsufficientFundsException;
import org.aezden.paymentservice.Model.*;
import org.aezden.paymentservice.Repository.*;
import org.aezden.paymentservice.Service.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class LedgerServiceTest {
    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private LedgerTransactionRepository transactionRepository;
    @Autowired
    private LedgerEntryRepository entryRepository;

    @Test
    void topUpIsIdempotentAndCreatesBalancedEntries() {
        UUID rider = UUID.randomUUID();

        assertThat(ledgerService.topUp(rider, 5000, "deposit-1")).isEqualTo(5000);
        assertThat(ledgerService.topUp(rider, 5000, "deposit-1")).isEqualTo(5000);

        assertThat(ledgerService.walletBalance(rider)).isEqualTo(5000);
        assertThat(transactionRepository.findByIdempotencyKey("TOPUP:" + rider + ":deposit-1")).isPresent();
        assertThat(entryRepository.findAll().stream().mapToLong(LedgerEntry::getAmountCents).sum()).isZero();
    }

    @Test
    void holdCompleteSplitsOddFareWithoutLosingACent() {
        UUID rider = UUID.randomUUID();
        UUID driver = UUID.randomUUID();
        UUID trip = UUID.randomUUID();

        ledgerService.topUp(rider, 2351, "deposit-" + rider);
        ledgerService.handle(LedgerService.TripEventType.MATCHED, trip, rider, driver, 2351);
        ledgerService.handle(LedgerService.TripEventType.COMPLETED, trip, rider, driver, 2351);
        ledgerService.handle(LedgerService.TripEventType.COMPLETED, trip, rider, driver, 2351);

        assertThat(ledgerService.walletBalance(rider)).isZero();
        assertThat(ledgerService.walletBalance(driver)).isEqualTo(1881);
        assertThat(transactionRepository.findAllByTripIdOrderByCreatedAtAsc(trip)).hasSize(2);
    }

    @Test
    void holdCannotOverdrawWalletAndCancellationWithoutHoldIsNoOp() {
        UUID rider = UUID.randomUUID();
        UUID trip = UUID.randomUUID();

        assertThatThrownBy(() -> ledgerService.handle(
                LedgerService.TripEventType.MATCHED, trip, rider, UUID.randomUUID(), 700))
                .isInstanceOf(InsufficientFundsException.class);

        ledgerService.handle(LedgerService.TripEventType.CANCELLED, UUID.randomUUID(), rider, null, 0);
        assertThat(ledgerService.walletBalance(rider)).isZero();
    }
}
