package com.spring.testing.users.services;

import com.spring.testing.users.dto.request.CreateUserRequest;
import com.spring.testing.users.dto.request.UpdateUserRequest;
import com.spring.testing.users.dto.response.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IUserService {

    UserResponse createUser(CreateUserRequest request);

    Page<UserResponse> getAllUsers(Pageable pageable);

    UserResponse getUserById(Long id);

    UserResponse updateUser(Long id, UpdateUserRequest request);

    void deleteUser(Long id);
}
