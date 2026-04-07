package com.spring.testing;

import com.spring.testing.exception.DuplicateEmailException;
import com.spring.testing.exception.ResourceNotFoundException;
import com.spring.testing.users.dto.request.CreateUserRequest;
import com.spring.testing.users.dto.request.UpdateUserRequest;
import com.spring.testing.users.dto.response.UserResponse;
import com.spring.testing.users.entities.UserEntity;
import com.spring.testing.users.mapper.UserMapper;
import com.spring.testing.users.repository.UserRepository;
import com.spring.testing.users.services.impl.UserServiceImpl;
import com.spring.testing.utils.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl Unit Tests")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    // ── Test fixtures ─────────────────────────────────────────────────────────

    private UserEntity activeUser;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        activeUser = UserEntity.builder()
                .id(1L)
                .name("Nguyen Van A")
                .email("vana@example.com")
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userResponse = UserResponse.builder()
                .id(1L)
                .name("Nguyen Van A")
                .email("vana@example.com")
                .status(UserStatus.ACTIVE)
                .createdAt(activeUser.getCreatedAt())
                .updatedAt(activeUser.getUpdatedAt())
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // createUser
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createUser()")
    class CreateUserTests {

        @Test
        @DisplayName("Happy path: tạo user thành công với email chưa tồn tại")
        void createUser_shouldReturnUserResponse_whenEmailIsUnique() {
            // Given
            CreateUserRequest request = CreateUserRequest.builder()
                    .name("Nguyen Van A")
                    .email("vana@example.com")
                    .build();

            given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
            given(userMapper.toEntity(request)).willReturn(activeUser);
            given(userRepository.save(activeUser)).willReturn(activeUser);
            given(userMapper.toResponse(activeUser)).willReturn(userResponse);

            // When
            UserResponse result = userService.createUser(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getEmail()).isEqualTo("vana@example.com");
            assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);

            // Verify đúng thứ tự và đúng số lần gọi
            then(userRepository).should(times(1)).existsByEmail(request.getEmail());
            then(userRepository).should(times(1)).save(activeUser);
            then(userMapper).should(times(1)).toEntity(request);
            then(userMapper).should(times(1)).toResponse(activeUser);
        }

        @Test
        @DisplayName("Email trùng: ném DuplicateEmailException, không gọi save()")
        void createUser_shouldThrowDuplicateEmailException_whenEmailAlreadyExists() {
            // Given
            CreateUserRequest request = CreateUserRequest.builder()
                    .name("Nguyen Van B")
                    .email("vana@example.com") // email đã tồn tại
                    .build();

            given(userRepository.existsByEmail(request.getEmail())).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.createUser(request))
                    .isInstanceOf(DuplicateEmailException.class)
                    .hasMessageContaining("vana@example.com");

            // Quan trọng: save() không được gọi khi email đã tồn tại
            then(userRepository).should(never()).save(any());
            then(userMapper).should(never()).toEntity(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getAllUsers
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAllUsers()")
    class GetAllUsersTests {

        @Test
        @DisplayName("Happy path: trả về Page<UserResponse> chỉ chứa ACTIVE users")
        void getAllUsers_shouldReturnPageOfActiveUsers() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<UserEntity> userPage = new PageImpl<>(List.of(activeUser), pageable, 1);

            given(userRepository.findAllByStatus(UserStatus.ACTIVE, pageable)).willReturn(userPage);
            given(userMapper.toResponse(activeUser)).willReturn(userResponse);

            // When
            Page<UserResponse> result = userService.getAllUsers(pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("Không có user nào: trả về Page rỗng")
        void getAllUsers_shouldReturnEmptyPage_whenNoUsersExist() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            given(userRepository.findAllByStatus(UserStatus.ACTIVE, pageable))
                    .willReturn(Page.empty(pageable));

            // When
            Page<UserResponse> result = userService.getAllUsers(pageable);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // getUserById
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getUserById()")
    class GetUserByIdTests {

        @Test
        @DisplayName("Happy path: tìm thấy ACTIVE user theo id")
        void getUserById_shouldReturnUserResponse_whenUserExistsAndActive() {
            // Given
            given(userRepository.findById(1L)).willReturn(Optional.of(activeUser));
            given(userMapper.toResponse(activeUser)).willReturn(userResponse);

            // When
            UserResponse result = userService.getUserById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            then(userRepository).should(times(1)).findById(1L);
        }

        @Test
        @DisplayName("Không tìm thấy: id không tồn tại → ném ResourceNotFoundException")
        void getUserById_shouldThrowResourceNotFoundException_whenUserNotFound() {
            // Given
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.getUserById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User")
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("User đã bị soft delete (INACTIVE) → ném ResourceNotFoundException")
        void getUserById_shouldThrowResourceNotFoundException_whenUserIsInactive() {
            // Given — user tồn tại trong DB nhưng đã bị soft delete
            UserEntity inactiveUser = UserEntity.builder()
                    .id(2L)
                    .name("Deleted User")
                    .email("deleted@example.com")
                    .status(UserStatus.INACTIVE) // đã bị xóa mềm
                    .build();

            given(userRepository.findById(2L)).willReturn(Optional.of(inactiveUser));

            // When & Then
            // Service phải coi INACTIVE user như không tồn tại
            assertThatThrownBy(() -> userService.getUserById(2L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User")
                    .hasMessageContaining("2");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // updateUser
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateUser()")
    class UpdateUserTests {

        @Test
        @DisplayName("Happy path: update name thành công")
        void updateUser_shouldReturnUpdatedUserResponse_whenValidRequest() {
            // Given
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .name("Nguyen Van B")
                    .build();

            UserResponse updatedResponse = UserResponse.builder()
                    .id(1L)
                    .name("Nguyen Van B")
                    .email("vana@example.com")
                    .status(UserStatus.ACTIVE)
                    .build();

            given(userRepository.findById(1L)).willReturn(Optional.of(activeUser));
            given(userRepository.save(activeUser)).willReturn(activeUser);
            given(userMapper.toResponse(activeUser)).willReturn(updatedResponse);

            // When
            UserResponse result = userService.updateUser(1L, request);

            // Then
            assertThat(result.getName()).isEqualTo("Nguyen Van B");
            // verify mapper được gọi để merge dữ liệu
            then(userMapper).should(times(1)).updateEntityFromRequest(request, activeUser);
        }

        @Test
        @DisplayName("Update email trùng với user khác → ném DuplicateEmailException")
        void updateUser_shouldThrowDuplicateEmailException_whenEmailBelongsToAnotherUser() {
            // Given
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .email("other@example.com")
                    .build();

            given(userRepository.findById(1L)).willReturn(Optional.of(activeUser));
            // Email này đã thuộc về user khác (idNot = 1L)
            given(userRepository.existsByEmailAndIdNot("other@example.com", 1L)).willReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.updateUser(1L, request))
                    .isInstanceOf(DuplicateEmailException.class)
                    .hasMessageContaining("other@example.com");

            then(userRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("Update user không tồn tại → ném ResourceNotFoundException")
        void updateUser_shouldThrowResourceNotFoundException_whenUserNotFound() {
            // Given
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.updateUser(99L, new UpdateUserRequest()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // deleteUser (soft delete)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteUser()")
    class DeleteUserTests {

        @Test
        @DisplayName("Happy path: soft delete — status đổi thành INACTIVE, không xóa vật lý")
        void deleteUser_shouldSetStatusInactive_whenUserExists() {
            // Given
            given(userRepository.findById(1L)).willReturn(Optional.of(activeUser));
            given(userRepository.save(any(UserEntity.class))).willReturn(activeUser);

            // When
            userService.deleteUser(1L);

            // Then — verify status đã được đổi thành INACTIVE trước khi save
            assertThat(activeUser.getStatus()).isEqualTo(UserStatus.INACTIVE);
            then(userRepository).should(times(1)).save(activeUser);
        }

        @Test
        @DisplayName("Xóa user không tồn tại → ném ResourceNotFoundException")
        void deleteUser_shouldThrowResourceNotFoundException_whenUserNotFound() {
            // Given
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.deleteUser(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99");

            then(userRepository).should(never()).save(any());
        }
    }
}
