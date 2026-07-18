package org.aezden.DTO;

public record UserRegisterRequest(
        String firstName,
        String lastName,
        String email,
        String password
) {
}
