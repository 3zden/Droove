package org.aezden.paymentservice.Dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TopUpRequest(@NotNull @Positive Long amountCents) {
}
