package org.aezden.DTO;

public record AuthResponse(String accessToken, UserResponse user) {
}
