package org.aezden.paymentservice.Repository;

import jakarta.persistence.LockModeType;
import org.aezden.paymentservice.Model.Account;
import org.aezden.paymentservice.Model.AccountType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByTypeAndOwnerId(AccountType type, UUID ownerId);

    Optional<Account> findByTypeAndTripId(AccountType type, UUID tripId);

    Optional<Account> findByType(AccountType type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
