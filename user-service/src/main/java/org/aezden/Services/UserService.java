package org.aezden.Services;

import org.aezden.DTO.UserLoginRequest;
import org.aezden.DTO.UserRegisterRequest;
import org.aezden.Entities.User;
import org.aezden.Repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    UserRepository userRepository;

    public UserService(UserRepository userRepository){
        this.userRepository = userRepository;
    }

    public String login(UserLoginRequest userLoginRequest) {
        if (userRepository.existsUserByEmail(userLoginRequest.email())){
             User user = userRepository.findByEmail(userLoginRequest.email());
        }
        return "";
    }

    public String register(UserRegisterRequest userRegisterRequest) {
        userRepository.save(new User(userRegisterRequest.email(),
                userRegisterRequest.firstName(),
                userRegisterRequest.lastName(),
                userRegisterRequest.password()));
        return "";
    }
}
