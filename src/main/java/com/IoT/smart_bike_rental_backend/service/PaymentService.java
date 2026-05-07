package com.IoT.smart_bike_rental_backend.service;

import com.IoT.smart_bike_rental_backend.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * PaymentService - Handles payment simulation
 *
 * In production, this would integrate with:
 * - Stripe
 * - PayPal
 * - Mobile money providers
 * - etc.
 *
 * For the IoT project, payments are simulated (always succeed)
 */
@Service
@Slf4j
public class PaymentService {

    /**
     * Process payment for starting a ride
     * Currently simulated - always succeeds
     *
     * @param user The user making the payment
     * @param bikeId The bike being rented
     * @return PaymentResult with success status and transaction ID
     */
    public PaymentResult processPayment(User user, String bikeId) {
        log.info("Processing payment for user {} renting bike {}", user.getEmail(), bikeId);

        // Simulate payment processing delay
        simulateProcessingDelay();

        // In production: Call Stripe/PayPal API here
        // For now: Always succeed with a generated transaction ID
        String transactionId = generateTransactionId();

        log.info("Payment SUCCESS for user {} - Transaction ID: {}", user.getEmail(), transactionId);

        return PaymentResult.success(transactionId, "Payment authorized");
    }

    /**
     * Process final payment after ride ends
     *
     * @param user The user
     * @param amount The final ride cost
     * @param preAuthTransactionId The pre-authorization transaction ID
     * @return PaymentResult
     */
    public PaymentResult capturePayment(User user, Double amount, String preAuthTransactionId) {
        log.info("Capturing payment of ${} for user {} (pre-auth: {})",
                amount, user.getEmail(), preAuthTransactionId);

        simulateProcessingDelay();

        // In production: Capture the pre-authorized amount
        String captureId = generateTransactionId();

        log.info("Payment CAPTURED: ${} - Capture ID: {}", amount, captureId);

        return PaymentResult.success(captureId, String.format("Payment of $%.2f captured", amount));
    }

    /**
     * Refund a payment (if ride failed to start)
     *
     * @param transactionId The original transaction ID
     * @return PaymentResult
     */
    public PaymentResult refundPayment(String transactionId) {
        log.info("Refunding payment for transaction: {}", transactionId);

        simulateProcessingDelay();

        // In production: Call payment provider's refund API
        String refundId = "REFUND-" + generateTransactionId();

        log.info("Refund PROCESSED - Refund ID: {}", refundId);

        return PaymentResult.success(refundId, "Payment refunded");
    }

    /**
     * Simulate network/processing delay
     */
    private void simulateProcessingDelay() {
        try {
            // Small delay to simulate real payment processing
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Generate a unique transaction ID
     */
    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Payment result class
     */
    public static class PaymentResult {
        private final boolean success;
        private final String transactionId;
        private final String message;

        private PaymentResult(boolean success, String transactionId, String message) {
            this.success = success;
            this.transactionId = transactionId;
            this.message = message;
        }

        public static PaymentResult success(String transactionId, String message) {
            return new PaymentResult(true, transactionId, message);
        }

        public static PaymentResult failure(String message) {
            return new PaymentResult(false, null, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public String getMessage() {
            return message;
        }
    }
}
