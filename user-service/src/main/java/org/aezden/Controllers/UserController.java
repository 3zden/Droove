package org.aezden.Controllers;

import org.aezden.DTO.UserLoginRequest;
import org.aezden.DTO.UserRegisterRequest;
import org.aezden.Services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class UserController {

    UserService userService;

    public UserController(UserService userService){
        this.userService = userService;
    }
    @PostMapping("/auth/login")
    public String login(@RequestBody UserLoginRequest userLoginRequest){
        return userService.login(userLoginRequest);
    }
    @PostMapping("/users/register")
    public String register(@RequestBody UserRegisterRequest userRegisterRequest){
        return userService.register(userRegisterRequest);
    }
}
