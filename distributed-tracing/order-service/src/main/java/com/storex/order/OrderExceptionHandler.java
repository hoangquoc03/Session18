package com.storex.order;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class OrderExceptionHandler {

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> handleUserServiceFailure(RestClientException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "error", "User Service is unavailable",
                        "message", "The order request could not be completed."));
    }
}
