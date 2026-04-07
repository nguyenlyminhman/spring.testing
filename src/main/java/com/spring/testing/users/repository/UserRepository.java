package com.spring.testing.users.repository;

import com.spring.testing.users.entities.UserEntity;
import com.spring.testing.utils.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository cho User entity.
 * Spring Data JPA tự generate implementation từ method name.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * Kiểm tra email đã tồn tại chưa (dùng trước khi tạo/cập nhật).
     */
    boolean existsByEmail(String email);

    /**
     * Kiểm tra email tồn tại ở user khác (dùng khi update để tránh conflict với chính mình).
     */
    boolean existsByEmailAndIdNot(String email, Long id);

    /**
     * Lấy danh sách user theo status với paging — dùng cho filter.
     */
    Page<UserEntity> findAllByStatus(UserStatus status, Pageable pageable);

    /**
     * Tìm user theo email (dùng khi cần verify).
     */
    Optional<UserEntity> findByEmail(String email);
}
