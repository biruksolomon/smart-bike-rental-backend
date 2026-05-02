package com.IoT.smart_bike_rental_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PasswordResetConfirm {
    private String email;
    private String code;
    private String newPassword;
}
