package com.spring.testing.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String email) {
        super(String.format("Email already exists: %s", email));
    }
}
