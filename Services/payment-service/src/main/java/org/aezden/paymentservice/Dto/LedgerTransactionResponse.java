package org.aezden.paymentservice.Dto;

import org.aezden.paymentservice.Model.LedgerTransaction;
import org.aezden.paymentservice.Model.LedgerTransactionType;

import java.time.Instant;
import java.util.UUID;

public record LedgerTransactionResponse(
        UUID id,
        LedgerTransactionType type,
        UUID tripId,
        Instant createdAt,
        long amountCents
) {
    public static LedgerTransactionResponse from(LedgerTransaction transaction) {
        return new LedgerTransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getTripId(),
                transaction.getCreatedAt(),
                transaction.getAmountCents());
    }
}
