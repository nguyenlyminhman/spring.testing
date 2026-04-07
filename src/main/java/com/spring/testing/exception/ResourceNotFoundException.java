package com.spring.testing.exception;

/**
 * Ném khi không tìm thấy resource theo id.
 * GlobalExceptionHandler sẽ map exception này thành HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Long id) {
        super(String.format("%s not found with id: %d", resourceName, id));
    }
}
