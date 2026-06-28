package org.aezden.DTO;

public record UserLoginRequest(
        String email,
        String password
) {
}
