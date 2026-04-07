package com.spring.testing;

import com.spring.testing.users.entities.UserEntity;
import com.spring.testing.users.repository.UserRepository;
import com.spring.testing.utils.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository Integration Tests")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    /**
     * TestEntityManager: dùng để persist dữ liệu test trực tiếp vào DB
     * mà không đi qua service layer, đảm bảo test độc lập.
     */
    @Autowired
    private TestEntityManager entityManager;

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private UserEntity persistedActiveUser;
    private UserEntity persistedInactiveUser;

    @BeforeEach
    void setUp() {
        // Persist trực tiếp qua TestEntityManager để bypass service logic
        persistedActiveUser = entityManager.persistAndFlush(UserEntity.builder()
                .name("Nguyen Van A")
                .email("vana@example.com")
                .status(UserStatus.ACTIVE)
                .build());

        persistedInactiveUser = entityManager.persistAndFlush(UserEntity.builder()
                .name("Tran Thi B")
                .email("b@example.com")
                .status(UserStatus.INACTIVE)
                .build());

        // Clear persistence context để đảm bảo các query sau không đọc từ cache
        entityManager.clear();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JPA Mapping & Auditing
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("JPA Mapping & Auditing")
    class MappingTests {

        @Test
        @DisplayName("Persist user: createdAt và updatedAt tự động được điền bởi JPA Auditing")
        void persist_shouldAutoPopulateAuditFields() {
            // Given
            UserEntity newUser = UserEntity.builder()
                    .name("Le Van C")
                    .email("levanc@example.com")
                    .status(UserStatus.ACTIVE)
                    .build();

            // When
            UserEntity saved = entityManager.persistAndFlush(newUser);

            // Then
            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
            // createdAt và updatedAt bằng nhau khi vừa tạo
            assertThat(saved.getCreatedAt()).isEqualTo(saved.getUpdatedAt());
        }

        @Test
        @DisplayName("Persist user: status mặc định là ACTIVE khi không set")
        void persist_shouldDefaultStatusToActive_whenNotSet() {
            // Given
            UserEntity newUser = UserEntity.builder()
                    .name("Default Status User")
                    .email("default@example.com")
                    // Không set status
                    .build();

            // When
            UserEntity saved = entityManager.persistAndFlush(newUser);
            entityManager.clear();
            UserEntity found = userRepository.findById(saved.getId()).orElseThrow();

            // Then
            assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("Persist user: enum UserStatus được lưu dưới dạng String trong DB")
        void persist_shouldStoreStatusAsString() {
            // Verify bằng cách load lại từ DB và kiểm tra enum đọc được
            UserEntity found = userRepository.findById(persistedActiveUser.getId()).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);

            UserEntity foundInactive = userRepository.findById(persistedInactiveUser.getId()).orElseThrow();
            assertThat(foundInactive.getStatus()).isEqualTo(UserStatus.INACTIVE);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // existsByEmail
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("existsByEmail()")
    class ExistsByEmailTests {

        @Test
        @DisplayName("Email đã tồn tại → trả về true")
        void existsByEmail_shouldReturnTrue_whenEmailExists() {
            assertThat(userRepository.existsByEmail("vana@example.com")).isTrue();
        }

        @Test
        @DisplayName("Email chưa tồn tại → trả về false")
        void existsByEmail_shouldReturnFalse_whenEmailNotExists() {
            assertThat(userRepository.existsByEmail("notexist@example.com")).isFalse();
        }

        @Test
        @DisplayName("Email case-sensitive: 'VANA@example.com' khác 'vana@example.com'")
        void existsByEmail_shouldBeCaseSensitive() {
            // H2 default là case-sensitive; hành vi này phụ thuộc DB configuration
            // Test này document behavior hiện tại
            assertThat(userRepository.existsByEmail("VANA@EXAMPLE.COM")).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // existsByEmailAndIdNot
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("existsByEmailAndIdNot()")
    class ExistsByEmailAndIdNotTests {

        @Test
        @DisplayName("Email thuộc user khác → trả về true (không cho phép update)")
        void existsByEmailAndIdNot_shouldReturnTrue_whenEmailBelongsToAnotherUser() {
            // Email của vana, kiểm tra từ góc nhìn của user khác (id=999)
            boolean result = userRepository.existsByEmailAndIdNot("vana@example.com", 999L);
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Email chính là của user đang update → trả về false (cho phép giữ nguyên email)")
        void existsByEmailAndIdNot_shouldReturnFalse_whenEmailBelongsToSameUser() {
            // vana đang update nhưng giữ nguyên email của mình → không bị chặn
            boolean result = userRepository.existsByEmailAndIdNot(
                    "vana@example.com", persistedActiveUser.getId());
            assertThat(result).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findAllByStatus (paging)
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAllByStatus() with paging")
    class FindAllByStatusTests {

        @Test
        @DisplayName("Chỉ trả về ACTIVE users, bỏ qua INACTIVE users")
        void findAllByStatus_shouldReturnOnlyActiveUsers() {
            // Given — đã có 1 ACTIVE và 1 INACTIVE trong setUp()
            PageRequest pageable = PageRequest.of(0, 10);

            // When
            Page<UserEntity> result = userRepository.findAllByStatus(UserStatus.ACTIVE, pageable);

            // Then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getEmail()).isEqualTo("vana@example.com");
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Paging đúng: page 0 size 1 với 2 ACTIVE users → chỉ trả về 1")
        void findAllByStatus_shouldRespectPagingParameters() {
            // Thêm 1 ACTIVE user nữa
            entityManager.persistAndFlush(UserEntity.builder()
                    .name("Le Van C")
                    .email("levanc@example.com")
                    .status(UserStatus.ACTIVE)
                    .build());
            entityManager.clear();

            // When — yêu cầu page 0, mỗi page 1 item
            Page<UserEntity> page0 = userRepository.findAllByStatus(UserStatus.ACTIVE, PageRequest.of(0, 1));
            Page<UserEntity> page1 = userRepository.findAllByStatus(UserStatus.ACTIVE, PageRequest.of(1, 1));

            // Then
            assertThat(page0.getContent()).hasSize(1);
            assertThat(page1.getContent()).hasSize(1);
            assertThat(page0.getTotalElements()).isEqualTo(2);
            assertThat(page0.getTotalPages()).isEqualTo(2);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Unique constraint
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Unique email constraint")
    class UniqueConstraintTests {

        @Test
        @DisplayName("Persist 2 user cùng email → ném exception (unique constraint vi phạm)")
        void persist_shouldThrowException_whenDuplicateEmail() {
            // Given — email đã tồn tại từ setUp()
            UserEntity duplicateEmailUser = UserEntity.builder()
                    .name("Different Name")
                    .email("vana@example.com") // trùng email
                    .status(UserStatus.ACTIVE)
                    .build();

            // When & Then — H2 sẽ ném constraint violation
            assertThatThrownBy(() -> entityManager.persistAndFlush(duplicateEmailUser))
                    .isInstanceOf(Exception.class); // PersistenceException hoặc ConstraintViolationException
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // findByEmail
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByEmail()")
    class FindByEmailTests {

        @Test
        @DisplayName("Tìm user theo email tồn tại → trả về Optional có giá trị")
        void findByEmail_shouldReturnUser_whenEmailExists() {
            Optional<UserEntity> result = userRepository.findByEmail("vana@example.com");

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Nguyen Van A");
        }

        @Test
        @DisplayName("Tìm user theo email không tồn tại → trả về Optional.empty()")
        void findByEmail_shouldReturnEmpty_whenEmailNotExists() {
            Optional<UserEntity> result = userRepository.findByEmail("ghost@example.com");

            assertThat(result).isEmpty();
        }
    }
}
