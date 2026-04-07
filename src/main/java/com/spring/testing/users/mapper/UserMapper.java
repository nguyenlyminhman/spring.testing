package com.spring.testing.users.mapper;

import com.spring.testing.users.dto.request.CreateUserRequest;
import com.spring.testing.users.dto.request.UpdateUserRequest;
import com.spring.testing.users.dto.response.UserResponse;
import com.spring.testing.users.entities.UserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct mapper — tự động generate implementation lúc compile.
 * componentModel = "spring" để inject như Spring Bean.
 *
 * updateUserFromRequest: chỉ map field không null (NullValuePropertyMappingStrategy.IGNORE)
 * để support partial update — field nào không gửi lên thì giữ nguyên giá trị cũ.
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserMapper {

    /**
     * Tạo entity từ CreateUserRequest.
     * id, createdAt, updatedAt được bỏ qua (JPA tự generate).
     * status mặc định ACTIVE theo @Builder.Default trong entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    UserEntity toEntity(CreateUserRequest request);

    /**
     * Convert entity thành UserResponse trả về cho client.
     * Tất cả field đều map 1-1 theo tên.
     */
    UserResponse toResponse(UserEntity user);

    /**
     * Partial update: chỉ merge các field không null từ request vào entity.
     * @MappingTarget chỉ định target là entity đã tồn tại (không tạo mới).
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(UpdateUserRequest request, @MappingTarget UserEntity user);
}
