package org.aezden.paymentservice.Repository;

import org.aezden.paymentservice.Model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findAllByTransactionId(UUID transactionId);

    @Query("select coalesce(sum(e.amountCents), 0) from LedgerEntry e where e.account.id = :accountId")
    long balanceForAccount(@Param("accountId") UUID accountId);
}
