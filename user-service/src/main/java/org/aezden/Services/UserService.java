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

    public User register(UserRegisterRequest userRegisterRequest) {
        User tempUser = new User(userRegisterRequest.email(),
                userRegisterRequest.firstName(),
                userRegisterRequest.lastName(),
                userRegisterRequest.password());
        System.out.println(tempUser);
        userRepository.save(tempUser);
        return tempUser;
    }
}
