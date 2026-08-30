package com.globalbank.orderservice.api;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.globalbank.orderservice.core.domain.OrderNotFoundException;
import com.globalbank.orderservice.core.domain.OrderNotCancellableException;
import com.globalbank.orderservice.core.domain.PaymentAlreadyCapturedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody(404, "ORDER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(OrderNotCancellableException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotCancellable(OrderNotCancellableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody(409, "ORDER_NOT_CANCELLABLE", ex.getMessage()));
    }

    @ExceptionHandler(PaymentAlreadyCapturedException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentCaptured(PaymentAlreadyCapturedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody(409, "PAYMENT_ALREADY_CAPTURED", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(errorBody(400, "INVALID_REASON_CODE", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof UnrecognizedPropertyException) {
            return ResponseEntity.badRequest()
                    .body(errorBody(400, "UNKNOWN_FIELDS", "Request contains unrecognised fields"));
        }
        if (cause instanceof InvalidFormatException ife
                && ife.getTargetType() != null
                && ife.getTargetType().isEnum()) {
            return ResponseEntity.badRequest()
                    .body(errorBody(400, "INVALID_REASON_CODE", "Invalid reason code value"));
        }
        return ResponseEntity.badRequest()
                .body(errorBody(400, "INVALID_REQUEST", "Request body is invalid"));
    }

    private Map<String, Object> errorBody(int status, String code, String message) {
        return Map.of("status", status, "code", code, "message", message);
    }
}
