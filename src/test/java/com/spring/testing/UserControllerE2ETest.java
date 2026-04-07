package com.spring.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spring.testing.users.dto.request.CreateUserRequest;
import com.spring.testing.users.dto.request.UpdateUserRequest;
import com.spring.testing.users.entities.UserEntity;
import com.spring.testing.users.repository.UserRepository;
import com.spring.testing.utils.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.assertj.core.api.Assertions.*;


/**
 * E2E / API Test — kiểm tra toàn bộ flow từ HTTP request đến Database.
 *
 * @SpringBootTest: load full context
 * @AutoConfigureMockMvc: inject MockMvc để gửi HTTP request giả lập
 *   → Không cần start server thật, nhưng đi qua đầy đủ Filter, Controller, Service, Repository
 * @Transactional: rollback sau mỗi test để DB sạch
 *
 * Mục tiêu test:
 *   - HTTP status code chính xác (200, 201, 204, 400, 404, 409)
 *   - Response body JSON đúng structure và giá trị
 *   - Validation error trả về đúng field và message
 *   - Các constraint (unique email, not null) được enforce
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("UserController E2E Tests")
class UserControllerE2ETest {

    private static final String BASE_URL = "/api/v1/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    // Seed data dùng chung cho các test cần user có sẵn
    private Long existingUserId;

    @BeforeEach
    void setUp() {
        // Insert trực tiếp qua repository để tránh phụ thuộc vào API create
        UserEntity user = userRepository.save(UserEntity.builder()
                .name("Existing User")
                .email("existing@example.com")
                .status(UserStatus.ACTIVE)
                .build());
        existingUserId = user.getId();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // POST /api/v1/users — Create
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/users")
    class CreateUserE2ETests {

        @Test
        @DisplayName("201 Created: tạo user hợp lệ → response body đúng structure")
        void createUser_shouldReturn201WithUserResponse_whenValidRequest() throws Exception {
            // Given
            CreateUserRequest request = CreateUserRequest.builder()
                    .name("Nguyen Van A")
                    .email("vana@example.com")
                    .build();

            // When & Then
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    // Verify từng field trong response body
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.name").value("Nguyen Van A"))
                    .andExpect(jsonPath("$.email").value("vana@example.com"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        }

        @Test
        @DisplayName("409 Conflict: tạo user với email đã tồn tại")
        void createUser_shouldReturn409_whenEmailAlreadyExists() throws Exception {
            // Given — email đã được insert trong setUp()
            CreateUserRequest request = CreateUserRequest.builder()
                    .name("Another User")
                    .email("existing@example.com") // trùng
                    .build();

            // When & Then
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.error").value("Conflict"))
                    .andExpect(jsonPath("$.message").value(containsString("existing@example.com")));
        }

        @Test
        @DisplayName("400 Bad Request: name bị bỏ trống → validation error với fieldErrors")
        void createUser_shouldReturn400WithFieldErrors_whenNameIsBlank() throws Exception {
            // Given
            CreateUserRequest request = CreateUserRequest.builder()
                    .name("") // blank
                    .email("valid@example.com")
                    .build();

            // When & Then
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.fieldErrors").exists())
                    .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
        }

        @Test
        @DisplayName("400 Bad Request: email sai format → validation error trên field email")
        void createUser_shouldReturn400_whenEmailFormatInvalid() throws Exception {
            // Given
            CreateUserRequest request = CreateUserRequest.builder()
                    .name("Valid Name")
                    .email("not-an-email") // sai format
                    .build();

            // When & Then
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
        }

        @Test
        @DisplayName("400 Bad Request: cả name và email đều lỗi → fieldErrors chứa cả 2 field")
        void createUser_shouldReturn400WithMultipleFieldErrors_whenBothFieldsInvalid() throws Exception {
            // Given
            CreateUserRequest request = CreateUserRequest.builder()
                    .name("") // blank
                    .email("invalid") // sai format
                    .build();

            // When & Then
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty())
                    .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
        }

        @Test
        @DisplayName("400 Bad Request: request body rỗng (không có JSON)")
        void createUser_shouldReturn400_whenRequestBodyMissing() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/v1/users — List with paging
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/users")
    class GetAllUsersE2ETests {

        @Test
        @DisplayName("200 OK: trả về Page structure đúng (content, totalElements, pageable)")
        void getAllUsers_shouldReturn200WithPageStructure() throws Exception {
            mockMvc.perform(get(BASE_URL)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalElements").isNumber())
                    .andExpect(jsonPath("$.totalPages").isNumber())
                    .andExpect(jsonPath("$.size").value(10))
                    .andExpect(jsonPath("$.number").value(0));
        }

        @Test
        @DisplayName("200 OK: user từ setUp() xuất hiện trong danh sách")
        void getAllUsers_shouldContainExistingUser() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].email",
                            hasItem("existing@example.com")));
        }

        @Test
        @DisplayName("200 OK: soft-deleted user không xuất hiện trong danh sách")
        void getAllUsers_shouldNotContainSoftDeletedUser() throws Exception {
            // Given — soft delete user từ setUp
            mockMvc.perform(delete(BASE_URL + "/" + existingUserId))
                    .andExpect(status().isNoContent());

            // When & Then
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].email",
                            not(hasItem("existing@example.com"))));
        }

        @Test
        @DisplayName("200 OK: default paging — size=10, sort=createdAt desc")
        void getAllUsers_shouldUseDefaultPaging_whenNoParamsProvided() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(10));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // GET /api/v1/users/{id} — Get by ID
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/users/{id}")
    class GetUserByIdE2ETests {

        @Test
        @DisplayName("200 OK: lấy user theo id tồn tại")
        void getUserById_shouldReturn200_whenUserExists() throws Exception {
            mockMvc.perform(get(BASE_URL + "/" + existingUserId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(existingUserId))
                    .andExpect(jsonPath("$.email").value("existing@example.com"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("404 Not Found: id không tồn tại → error response đúng format")
        void getUserById_shouldReturn404_whenUserNotFound() throws Exception {
            mockMvc.perform(get(BASE_URL + "/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value(containsString("999999")))
                    .andExpect(jsonPath("$.path").value("/api/v1/users/999999"));
        }

        @Test
        @DisplayName("404 Not Found: user đã bị soft delete → không thể lấy")
        void getUserById_shouldReturn404_whenUserSoftDeleted() throws Exception {
            // Soft delete trước
            mockMvc.perform(delete(BASE_URL + "/" + existingUserId))
                    .andExpect(status().isNoContent());

            // Sau đó lấy → phải 404
            mockMvc.perform(get(BASE_URL + "/" + existingUserId))
                    .andExpect(status().isNotFound());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PUT /api/v1/users/{id} — Update
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PUT /api/v1/users/{id}")
    class UpdateUserE2ETests {

        @Test
        @DisplayName("200 OK: update name và email thành công")
        void updateUser_shouldReturn200WithUpdatedData() throws Exception {
            // Given
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .name("Updated Name")
                    .email("updated@example.com")
                    .build();

            // When & Then
            mockMvc.perform(put(BASE_URL + "/" + existingUserId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(existingUserId))
                    .andExpect(jsonPath("$.name").value("Updated Name"))
                    .andExpect(jsonPath("$.email").value("updated@example.com"));
        }

        @Test
        @DisplayName("200 OK: partial update — chỉ update name, email giữ nguyên")
        void updateUser_shouldKeepUnchangedFields_whenPartialRequest() throws Exception {
            // Given — chỉ update name
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .name("Only Name Changed")
                    .build();

            // When & Then
            mockMvc.perform(put(BASE_URL + "/" + existingUserId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Only Name Changed"))
                    .andExpect(jsonPath("$.email").value("existing@example.com")); // không đổi
        }

        @Test
        @DisplayName("409 Conflict: update email thành email của user khác")
        void updateUser_shouldReturn409_whenNewEmailBelongsToAnotherUser() throws Exception {
            // Given — tạo thêm user có email khác
            userRepository.save(UserEntity.builder()
                    .name("Other User")
                    .email("other@example.com")
                    .status(UserStatus.ACTIVE)
                    .build());

            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email("other@example.com") // email của user khác
                    .build();

            // When & Then
            mockMvc.perform(put(BASE_URL + "/" + existingUserId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("404 Not Found: update user không tồn tại")
        void updateUser_shouldReturn404_whenUserNotFound() throws Exception {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .name("New Name")
                    .build();

            mockMvc.perform(put(BASE_URL + "/999999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("400 Bad Request: email sai format trong update request")
        void updateUser_shouldReturn400_whenEmailFormatInvalidOnUpdate() throws Exception {
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email("not-valid-email")
                    .build();

            mockMvc.perform(put(BASE_URL + "/" + existingUserId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.email").isNotEmpty());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DELETE /api/v1/users/{id} — Soft Delete
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("DELETE /api/v1/users/{id}")
    class DeleteUserE2ETests {

        @Test
        @DisplayName("204 No Content: soft delete user tồn tại → không có response body")
        void deleteUser_shouldReturn204NoContent_whenUserExists() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/" + existingUserId))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string("")); // không có body
        }

        @Test
        @DisplayName("404 Not Found: xóa user không tồn tại")
        void deleteUser_shouldReturn404_whenUserNotFound() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/999999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Soft delete: sau khi xóa, GET by id trả 404 nhưng record vẫn còn trong DB")
        void deleteUser_shouldSoftDelete_rowStillExistsInDb() throws Exception {
            // When — delete
            mockMvc.perform(delete(BASE_URL + "/" + existingUserId))
                    .andExpect(status().isNoContent());

            // API trả 404 (user không visible)
            mockMvc.perform(get(BASE_URL + "/" + existingUserId))
                    .andExpect(status().isNotFound());

            // Nhưng DB vẫn còn record với status INACTIVE
            assertThat(userRepository.findById(existingUserId)).isPresent();
            assertThat(userRepository.findById(existingUserId).get().getStatus())
                    .isEqualTo(UserStatus.INACTIVE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Full Scenario — End to End
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full User Lifecycle Scenario")
    class FullLifecycleTests {

        @Test
        @DisplayName("Lifecycle đầy đủ: Create → Get → Update → Delete → Get (404)")
        void fullLifecycle_createUpdateDelete() throws Exception {
            // 1. Create
            CreateUserRequest createRequest = CreateUserRequest.builder()
                    .name("Lifecycle User")
                    .email("lifecycle@example.com")
                    .build();

            String createResponse = mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long newUserId = objectMapper.readTree(createResponse).get("id").asLong();

            // 2. Get — verify tồn tại
            mockMvc.perform(get(BASE_URL + "/" + newUserId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Lifecycle User"));

            // 3. Update
            mockMvc.perform(put(BASE_URL + "/" + newUserId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    UpdateUserRequest.builder().name("Updated Lifecycle User").build())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated Lifecycle User"));

            // 4. Delete (soft)
            mockMvc.perform(delete(BASE_URL + "/" + newUserId))
                    .andExpect(status().isNoContent());

            // 5. Get after delete → 404
            mockMvc.perform(get(BASE_URL + "/" + newUserId))
                    .andExpect(status().isNotFound());
        }
    }
}