package org.aezden.DTO;

import org.aezden.Entities.Role;
import org.aezden.Entities.User;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Role role,
        String vehiclePlate
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(),
                user.getLastName(), user.getRole(), user.getVehiclePlate());
    }
}
