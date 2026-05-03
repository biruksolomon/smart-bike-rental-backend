package com.IoT.smart_bike_rental_backend.exception;

import com.IoT.smart_bike_rental_backend.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BikeNotFoundException.class)
    public ResponseEntity<ApiError> handleBikeNotFound(BikeNotFoundException ex, WebRequest request) {
        ApiError error = new ApiError("Bike not found", ex.getMessage(), HttpStatus.NOT_FOUND.value());
        error.setPath(request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(RideNotFoundException.class)
    public ResponseEntity<ApiError> handleRideNotFound(RideNotFoundException ex, WebRequest request) {
        ApiError error = new ApiError("Ride not found", ex.getMessage(), HttpStatus.NOT_FOUND.value());
        error.setPath(request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex, WebRequest request) {
        ApiError error = new ApiError("User not found", ex.getMessage(), HttpStatus.NOT_FOUND.value());
        error.setPath(request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BikeNotAvailableException.class)
    public ResponseEntity<ApiError> handleBikeNotAvailable(BikeNotAvailableException ex, WebRequest request) {
        ApiError error = new ApiError("Bike not available", ex.getMessage(), HttpStatus.CONFLICT.value());
        error.setPath(request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidVerificationCodeException.class)
    public ResponseEntity<ApiError> handleInvalidVerificationCode(InvalidVerificationCodeException ex, WebRequest request) {
        ApiError error = new ApiError("Invalid verification code", ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        error.setPath(request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleRuntimeException(RuntimeException ex, WebRequest request) {
        ApiError error = new ApiError("Internal server error", ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.setPath(request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGlobalException(Exception ex, WebRequest request) {
        ApiError error = new ApiError("An error occurred", ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        error.setPath(request.getDescription(false).replace("uri=", ""));
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
