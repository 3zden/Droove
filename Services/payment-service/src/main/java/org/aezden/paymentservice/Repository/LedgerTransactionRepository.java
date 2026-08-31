package org.aezden.paymentservice.Repository;

import org.aezden.paymentservice.Model.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
    Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);

    List<LedgerTransaction> findAllByTripIdOrderByCreatedAtAsc(UUID tripId);
}
