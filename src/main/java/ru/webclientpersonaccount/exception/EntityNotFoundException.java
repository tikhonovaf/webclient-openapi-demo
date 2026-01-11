package ru.webclientpersonaccount.exception;

// 1. Убедитесь, что класс расширяет RuntimeException
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}