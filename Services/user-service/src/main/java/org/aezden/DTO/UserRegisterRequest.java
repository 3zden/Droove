package org.aezden.DTO;

import org.aezden.Entities.Role;

public record UserRegisterRequest(
        String firstName,
        String lastName,
        String email,
        String password,
        Role role,
        String vehiclePlate
) {
}
