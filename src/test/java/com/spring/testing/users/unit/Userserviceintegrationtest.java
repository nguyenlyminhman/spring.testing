package com.spring.testing.users.unit;

import com.spring.testing.exception.DuplicateEmailException;
import com.spring.testing.exception.ResourceNotFoundException;
import com.spring.testing.users.dto.request.CreateUserRequest;
import com.spring.testing.users.dto.request.UpdateUserRequest;
import com.spring.testing.users.dto.response.UserResponse;
import com.spring.testing.users.repository.UserRepository;
import com.spring.testing.users.services.IUserService;
import com.spring.testing.utils.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration Test: Service + Repository + H2 Database.
 *
 * @SpringBootTest: load full Spring context (Service, Repository, Mapper, JPA, H2)
 * @Transactional: mỗi test method rollback sau khi chạy xong
 *   → không cần @BeforeEach xóa data thủ công
 *   → test không ảnh hưởng lẫn nhau
 *
 * Mục tiêu:
 *   - Verify toàn bộ flow Service → Repository → DB hoạt động đúng
 *   - Verify MapStruct mapper không bị lỗi runtime
 *   - Verify JPA Auditing thực sự hoạt động khi save qua service
 *   - Verify transaction behavior (rollback, constraint)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("UserService Integration Tests (Service + DB)")
class UserServiceIntegrationTest {

    @Autowired
    private IUserService userService;

    @Autowired
    private UserRepository userRepository;

    // Dùng để kiểm tra state DB trực tiếp sau khi service thực hiện thao tác
    private Long existingUserId;

    @BeforeEach
    void setUp() {
        // Tạo 1 user có sẵn để dùng trong các test update/delete
        UserResponse created = userService.createUser(CreateUserRequest.builder()
                .name("Setup User")
                .email("setup@example.com")
                .build());
        existingUserId = created.getId();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // createUser — full flow
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createUser() — full flow")
    class CreateUserIntegrationTests {

        @Test
        @DisplayName("Tạo user thành công: dữ liệu được persist thật vào H2")
        void createUser_shouldPersistUserInDatabase() {
            // Given
            CreateUserRequest request = CreateUserRequest.builder()
                    .name("Nguyen Van A")
                    .email("vana@example.com")
                    .build();

            // When
            UserResponse response = userService.createUser(request);

            // Then — verify response
            assertThat(response.getId()).isNotNull();
            assertThat(response.getName()).isEqualTo("Nguyen Van A");
            assertThat(response.getEmail()).isEqualTo("vana@example.com");
            assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);

            // Verify dữ liệu thật trong DB
            assertThat(userRepository.findById(response.getId())).isPresent();
            assertThat(userRepository.existsByEmail("vana@example.com")).isTrue();
        }

        @Test
        @DisplayName("JPA Auditing: createdAt và updatedAt được tự động set")
        void createUser_shouldAutoSetAuditFields() {
            // When
            UserResponse response = userService.createUser(CreateUserRequest.builder()
                    .name("Audit Test User")
                    .email("audit@example.com")
                    .build());

            // Then
            assertThat(response.getCreatedAt()).isNotNull();
            assertThat(response.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Tạo user với email trùng → DuplicateEmailException, không có gì được lưu")
        void createUser_shouldThrowAndRollback_whenEmailDuplicate() {
            // Given — tạo user đầu tiên
            userService.createUser(CreateUserRequest.builder()
                    .name("First User")
                    .email("duplicate@example.com")
                    .build());

            long countBefore = userRepository.count();

            // When & Then
            assertThatThrownBy(() -> userService.createUser(CreateUserRequest.builder()
                    .name("Second User")
                    .email("duplicate@example.com") // email trùng
                    .build()))
                    .isInstanceOf(DuplicateEmailException.class);

            // Verify count không tăng (không có user mới được lưu)
            assertThat(userRepository.count()).isEqualTo(countBefore);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getAllUsers — paging
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAllUsers() — paging & filtering")
    class GetAllUsersIntegrationTests {

        @Test
        @DisplayName("Chỉ trả về ACTIVE users: soft-deleted user không xuất hiện trong list")
        void getAllUsers_shouldExcludeSoftDeletedUsers() {
            // Given — thêm thêm 1 user rồi soft delete
            UserResponse toDelete = userService.createUser(CreateUserRequest.builder()
                    .name("To Delete")
                    .email("todelete@example.com")
                    .build());
            userService.deleteUser(toDelete.getId());

            // When
            Page<UserResponse> result = userService.getAllUsers(PageRequest.of(0, 10));

            // Then — chỉ thấy user ACTIVE (user từ setUp + user vừa tạo nhưng chưa xóa)
            assertThat(result.getContent())
                    .extracting(UserResponse::getEmail)
                    .doesNotContain("todelete@example.com");
        }

        @Test
        @DisplayName("Sorting theo name ASC hoạt động đúng")
        void getAllUsers_shouldReturnUsersSortedByName() {
            // Given — tạo thêm 2 users có tên sort theo alphabet
            userService.createUser(CreateUserRequest.builder().name("Zebra User").email("z@example.com").build());
            userService.createUser(CreateUserRequest.builder().name("Alpha User").email("a@example.com").build());

            // When
            Page<UserResponse> result = userService.getAllUsers(
                    PageRequest.of(0, 10, Sort.by("name").ascending()));

            // Then — "Alpha User" phải đứng trước "Zebra User"
            java.util.List<String> names = result.getContent()
                    .stream()
                    .map(UserResponse::getName)
                    .toList();
            assertThat(names.indexOf("Alpha User")).isLessThan(names.indexOf("Zebra User"));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // updateUser — partial update
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateUser() — partial update")
    class UpdateUserIntegrationTests {

        @Test
        @DisplayName("Update chỉ name: email và status không thay đổi")
        void updateUser_shouldOnlyUpdateName_whenOnlyNameProvided() {
            // Given
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .name("Updated Name")
                    // email và status không set
                    .build();

            // When
            UserResponse response = userService.updateUser(existingUserId, request);

            // Then
            assertThat(response.getName()).isEqualTo("Updated Name");
            assertThat(response.getEmail()).isEqualTo("setup@example.com"); // giữ nguyên
            assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE);  // giữ nguyên
        }

        @Test
        @DisplayName("Update status thành INACTIVE: không phải soft delete, chỉ đổi status")
        void updateUser_shouldUpdateStatusToInactive() {
            // Given
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .status(UserStatus.INACTIVE)
                    .build();

            // When
            UserResponse response = userService.updateUser(existingUserId, request);

            // Then — user vẫn tìm thấy qua id trực tiếp
            assertThat(response.getStatus()).isEqualTo(UserStatus.INACTIVE);
            assertThat(userRepository.findById(existingUserId)).isPresent();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // deleteUser — soft delete
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteUser() — soft delete behavior")
    class DeleteUserIntegrationTests {

        @Test
        @DisplayName("Soft delete: row vẫn tồn tại trong DB nhưng status = INACTIVE")
        void deleteUser_shouldKeepRowInDatabase_withInactiveStatus() {
            // When
            userService.deleteUser(existingUserId);

            // Then — row vẫn còn trong DB
            assertThat(userRepository.findById(existingUserId)).isPresent();

            // Nhưng status đã là INACTIVE
            assertThat(userRepository.findById(existingUserId).get().getStatus())
                    .isEqualTo(UserStatus.INACTIVE);
        }

        @Test
        @DisplayName("Sau khi soft delete, getUserById() ném ResourceNotFoundException")
        void deleteUser_shouldMakeUserInvisibleViaGetById() {
            // When
            userService.deleteUser(existingUserId);

            // Then — service coi INACTIVE user như đã xóa
            assertThatThrownBy(() -> userService.getUserById(existingUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Xóa 2 lần (double delete): lần 2 ném ResourceNotFoundException")
        void deleteUser_shouldThrow_whenDeletedTwice() {
            // Given
            userService.deleteUser(existingUserId); // lần 1

            // When & Then — lần 2 phải thất bại vì user đã INACTIVE
            assertThatThrownBy(() -> userService.deleteUser(existingUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}