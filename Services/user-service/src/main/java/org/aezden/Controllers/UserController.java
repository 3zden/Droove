package org.aezden.Controllers;

import org.aezden.DTO.AuthResponse;
import org.aezden.DTO.UserLoginRequest;
import org.aezden.DTO.UserRegisterRequest;
import org.aezden.DTO.UserResponse;
import org.aezden.Services.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody UserRegisterRequest request) {
        return userService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody UserLoginRequest request) {
        return userService.login(request);
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(@RequestHeader("X-User-Id") UUID userId) {
        return userService.getCurrentUser(userId);
    }
}
