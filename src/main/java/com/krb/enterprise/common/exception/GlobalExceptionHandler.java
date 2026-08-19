package com.krb.enterprise.common.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException exception) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", 401);
                response.put("error", "Unauthorized");
                response.put("message", "Invalid email or password.");

                return ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .body(response);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {

                Map<String, Object> errors = new HashMap<>();

                exception.getBindingResult()
                                .getFieldErrors()
                                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

                Map<String, Object> response = new HashMap<>();

                response.put("status", 400);
                response.put("error", "Bad Request");
                response.put("message", "Validation failed.");
                response.put("errors", errors);

                return ResponseEntity
                                .badRequest()
                                .body(response);
        }

        @ExceptionHandler(ApplicationException.class)
        public ResponseEntity<ApiError> handleApplicationException(ApplicationException exception) {

                return ResponseEntity
                                .status(exception.getStatus())
                                .body(new ApiError(
                                                exception.getStatus().value(),
                                                exception.getStatus().getReasonPhrase(),
                                                exception.getMessage()));
        }
}
