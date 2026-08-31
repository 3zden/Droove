package org.aezden.paymentservice.Exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException() {
        super("Insufficient wallet funds");
    }
}
