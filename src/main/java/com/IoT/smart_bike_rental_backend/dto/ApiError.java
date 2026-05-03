package com.IoT.smart_bike_rental_backend.dto;

import java.time.LocalDateTime;

public class ApiError {
    private boolean success;
    private String message;
    private String error;
    private int status;
    private LocalDateTime timestamp;
    private String path;

    public ApiError() {
        this.success = false;
        this.timestamp = LocalDateTime.now();
    }

    public ApiError(String message, String error, int status) {
        this.success = false;
        this.message = message;
        this.error = error;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}