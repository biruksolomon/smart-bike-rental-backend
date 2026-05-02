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

    @Value("${spring.mail.from}")
    private String fromEmail;



    /**
     * Send password reset code via email to user
     *
     * @param email    User email
     * @param code     6-digit reset code
     * @param userName User name
     */
    public void sendPasswordResetEmail(String email, String code, String userName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Password Reset Code - Smart Bike Rental");
            message.setText(buildPasswordResetEmailBody(userName, code));

            mailSender.send(message);
            log.info("Password reset code email sent to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", email, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Build password reset email body with reset code
     */
    private String buildPasswordResetEmailBody(String userName, String resetCode) {
        return String.format(
                "Hello %s,\n\n" +
                        "You have requested to reset your password for your Smart Bike Rental account.\n\n" +
                        "Please use the following 6-digit code to reset your password:\n\n" +
                        "════════════════════\n" +
                        "     %s\n" +
                        "════════════════════\n\n" +
                        "This code will expire in 15 minutes.\n\n" +
                        "If you did not request this password reset, please ignore this email.\n\n" +
                        "Best regards,\n" +
                        "Smart Bike Rental Team",
                userName, resetCode
        );
    }
}
