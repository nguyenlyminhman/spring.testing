package com.spring.testing.users.controller;

import com.spring.testing.users.dto.request.CreateUserRequest;
import com.spring.testing.users.dto.request.UpdateUserRequest;
import com.spring.testing.users.dto.response.UserResponse;
import com.spring.testing.users.services.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    /**
     * POST /api/v1/users
     * Tạo user mới. Trả 201 Created kèm user vừa tạo.
     */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("POST /api/v1/users - Creating user with email: {}", request.getEmail());
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/users?page=0&size=10&sort=createdAt,desc
     * Lấy danh sách user có paging + sorting.
     * Default: page 0, size 10, sort theo createdAt desc.
     */
    @GetMapping
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        log.info("GET /api/v1/users - page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    /**
     * GET /api/v1/users/{id}
     * Lấy user theo id. Trả 404 nếu không tìm thấy.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        log.info("GET /api/v1/users/{}", id);
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * PUT /api/v1/users/{id}
     * Cập nhật user (partial update — chỉ field nào có giá trị mới được update).
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {

        log.info("PUT /api/v1/users/{}", id);
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    /**
     * DELETE /api/v1/users/{id}
     * Soft delete user (đổi status thành INACTIVE). Trả 204 No Content.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.info("DELETE /api/v1/users/{}", id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}

