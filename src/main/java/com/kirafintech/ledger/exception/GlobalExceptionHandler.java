package com.kirafintech.ledger.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientFunds(InsufficientFundsException ex) {
        return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", "INSUFFICIENT_FUNDS", "message", ex.getMessage()));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAccountNotFound(AccountNotFoundException ex) {
        return ResponseEntity.status(404)
                .body(Map.of("error", "ACCOUNT_NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("idempotency_key") || msg.contains("uq_transfers")) {
            return ResponseEntity.status(409)
                    .body(Map.of("error", "DUPLICATE_IDEMPOTENCY_KEY",
                                 "message", "This operation has already been recorded"));
        }
        return ResponseEntity.status(409)
                .body(Map.of("error", "CONFLICT", "message", "Duplicate resource"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage,
                        (a, b) -> a));
        return ResponseEntity.badRequest()
                .body(Map.of("error", "VALIDATION_ERROR", "fields", fields));
    }
}
