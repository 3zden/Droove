package org.aezden.Controllers;

import org.aezden.DTO.UserLoginRequest;
import org.aezden.DTO.UserRegisterRequest;
import org.aezden.Entities.User;
import org.aezden.Services.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {

    UserService userService;

    public UserController(UserService userService){
        this.userService = userService;
    }

    @PostMapping("/users/login")
    public User login(@RequestBody UserLoginRequest userLoginRequest){
        System.out.println("enter login controller");
        return userService.login(userLoginRequest);
    }

    @PostMapping("/users/register")
    public User register(@RequestBody UserRegisterRequest userRegisterRequest){
        System.out.println("enter register controller");
        return userService.register(userRegisterRequest);
    }

    @GetMapping("/users/me")
    public User getCurrentUser(@RequestHeader("User-Email") String email){
        return userService.getCurrentUser(email);
    }
}
