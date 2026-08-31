package org.aezden.paymentservice.Exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException() {
        super("A valid user identity is required");
    }
}
