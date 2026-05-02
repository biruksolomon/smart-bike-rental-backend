package com.IoT.smart_bike_rental_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@smartbike.com}")
    private String fromEmail;

    @Value("${app.base-url:http://localhost:9080}")
    private String baseUrl;

    /**
     * Send password reset email to user
     *
     * @param email   User email
     * @param token   Password reset token
     * @param userName User name
     */
    public void sendPasswordResetEmail(String email, String token, String userName) {
        try {
            String resetLink = baseUrl + "/api/auth/reset-password?token=" + token;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Password Reset Request - Smart Bike Rental");
            message.setText(buildPasswordResetEmailBody(userName, resetLink));

            mailSender.send(message);
            log.info("Password reset email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", email, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Build password reset email body
     */
    private String buildPasswordResetEmailBody(String userName, String resetLink) {
        return String.format(
                "Hello %s,\n\n" +
                        "You have requested to reset your password for your Smart Bike Rental account.\n\n" +
                        "Please click the link below to reset your password:\n" +
                        "%s\n\n" +
                        "This link will expire in 15 minutes.\n\n" +
                        "If you did not request this password reset, please ignore this email.\n\n" +
                        "Best regards,\n" +
                        "Smart Bike Rental Team",
                userName, resetLink
        );
    }
}