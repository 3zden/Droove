package org.aezden.paymentservice.Controller;

import jakarta.validation.Valid;
import org.aezden.paymentservice.Dto.*;
import org.aezden.paymentservice.Model.LedgerTransaction;
import org.aezden.paymentservice.Service.IdentityService;
import org.aezden.paymentservice.Service.LedgerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"", "/api/payments"})
public class PaymentController {
    private final LedgerService ledgerService;
    private final IdentityService identityService;

    public PaymentController(LedgerService ledgerService, IdentityService identityService) {
        this.ledgerService = ledgerService;
        this.identityService = identityService;
    }

    @GetMapping("/wallet")
    public WalletBalanceResponse wallet(
            @RequestHeader(value = "X-User-Id", required = false) String forwardedUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UUID userId = identityService.resolve(forwardedUserId, authorization);
        return new WalletBalanceResponse(ledgerService.walletBalance(userId));
    }

    @PostMapping("/wallet/topup")
    public WalletBalanceResponse topUp(
            @Valid @RequestBody TopUpRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-User-Id", required = false) String forwardedUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UUID userId = identityService.resolve(forwardedUserId, authorization);
        return new WalletBalanceResponse(ledgerService.topUp(userId, request.amountCents(), idempotencyKey));
    }

    @GetMapping("/ledger/trip/{tripId}")
    public ResponseEntity<List<LedgerTransactionResponse>> tripLedger(
            @PathVariable UUID tripId,
            @RequestHeader(value = "X-User-Id", required = false) String forwardedUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        identityService.resolve(forwardedUserId, authorization);
        List<LedgerTransactionResponse> transactions = ledgerService.tripTransactions(tripId).stream()
                .map(LedgerTransactionResponse::from)
                .toList();
        return ResponseEntity.ok(transactions);
    }
}
