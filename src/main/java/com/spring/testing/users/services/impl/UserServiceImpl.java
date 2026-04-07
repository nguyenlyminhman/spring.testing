package com.spring.testing.users.services.impl;

import com.spring.testing.exception.DuplicateEmailException;
import com.spring.testing.exception.ResourceNotFoundException;
import com.spring.testing.users.dto.request.CreateUserRequest;
import com.spring.testing.users.dto.request.UpdateUserRequest;
import com.spring.testing.users.dto.response.UserResponse;
import com.spring.testing.users.entities.UserEntity;
import com.spring.testing.users.mapper.UserMapper;
import com.spring.testing.users.repository.UserRepository;
import com.spring.testing.users.services.IUserService;
import com.spring.testing.utils.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        try {
            log.debug("Creating user with email: {}", request.getEmail());

            // Kiểm tra email duplicate trước khi tạo
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateEmailException(request.getEmail());
            }

            UserEntity user = userMapper.toEntity(request);
            UserEntity savedUser = userRepository.save(user);

            log.info("User created successfully with id: {}", savedUser.getId());
            return userMapper.toResponse(savedUser);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        log.debug("Fetching all users, page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());

        // Chỉ trả về ACTIVE users trong list — INACTIVE (soft deleted) bị ẩn
        Page<UserEntity> s = userRepository.findAllByStatus(UserStatus.ACTIVE, pageable);
        Page<UserResponse> rs = userRepository.findAllByStatus(UserStatus.ACTIVE, pageable)
                .map(userMapper::toResponse);

        return rs;
    }

    @Override
    public UserResponse getUserById(Long id) {
        log.debug("Fetching user by id: {}", id);

        UserEntity user = findActiveUserById(id);
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        log.debug("Updating user id: {}", id);

        UserEntity user = findActiveUserById(id);

        // Kiểm tra email mới có bị trùng với user khác không
        if (request.getEmail() != null && userRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
            throw new DuplicateEmailException(request.getEmail());
        }

        // MapStruct merge chỉ các field không null vào entity
        userMapper.updateEntityFromRequest(request, user);
        UserEntity updatedUser = userRepository.save(user);

        log.info("User id: {} updated successfully", id);
        return userMapper.toResponse(updatedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        log.debug("Soft deleting user id: {}", id);

        UserEntity user = findActiveUserById(id);

        // Soft delete: chỉ đổi status, không xóa row
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);

        log.info("User id: {} soft deleted (status set to INACTIVE)", id);
    }

    /**
     * Helper: tìm user ACTIVE theo id, ném 404 nếu không tìm thấy hoặc đã bị soft delete.
     */
    private UserEntity findActiveUserById(Long id) {
        return userRepository.findById(id)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
