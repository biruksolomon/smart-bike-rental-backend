package com.IoT.smart_bike_rental_backend.controller;

import com.IoT.smart_bike_rental_backend.model.Ride;
import com.IoT.smart_bike_rental_backend.repository.Riderepository;
import com.IoT.smart_bike_rental_backend.service.ChapaPaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Webhook endpoint for Chapa payment notifications for bike rides.
 * Receives payment status updates from Chapa and updates ride payment status accordingly.
 */
@RestController
@RequestMapping("/api/webhooks/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private final ChapaPaymentService chapaPaymentService;
    private final Riderepository rideRepository;
    private final ObjectMapper objectMapper;

    /**
     * Webhook endpoint that receives payment status from Chapa
     * Called when payment succeeds or fails
     */
    @PostMapping("/chapa")
    public ResponseEntity<Map<String, String>> handleChapaWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody String payload) {

        try {
            log.info("Received Chapa webhook notification");

            // Parse webhook payload
            JsonNode webhookData = objectMapper.readTree(payload);
            String txRef = webhookData.get("tx_ref").asText();
            String status = webhookData.get("status").asText();

            log.info("Processing payment webhook - TxRef: {}, Status: {}", txRef, status);

            // Check if this is a bike ride payment (starts with BIKE-RIDE-)
            if (!txRef.startsWith("BIKE-RIDE-")) {
                log.warn("Webhook received for non-bike-ride transaction: {}", txRef);
                return ResponseEntity.ok(createResponse("success", "Webhook received but not processed"));
            }

            // Find the ride by Chapa transaction reference
            var rideOptional = rideRepository.findByChapaTxRef(txRef);
            if (rideOptional.isEmpty()) {
                log.warn("Ride not found for txRef: {}", txRef);
                return ResponseEntity.ok(createResponse("error", "Ride not found"));
            }

            Ride ride = rideOptional.get();

            // Handle payment based on status
            if ("success".equalsIgnoreCase(status)) {
                handlePaymentSuccess(ride, txRef);
            } else if ("pending".equalsIgnoreCase(status)) {
                handlePaymentPending(ride, txRef);
            } else {
                handlePaymentFailure(ride, txRef, status);
            }

            return ResponseEntity.ok(createResponse("success", "Webhook processed successfully"));

        } catch (Exception e) {
            log.error("Error processing Chapa webhook: {}", e.getMessage(), e);
            return ResponseEntity.ok(createResponse("error", "Webhook processing failed: " + e.getMessage()));
        }
    }

    /**
     * Handle successful payment
     */
    private void handlePaymentSuccess(Ride ride, String txRef) {
        try {
            log.info("Payment successful for ride {} - TxRef: {}", ride.getId(), txRef);

            // Verify with Chapa that payment actually succeeded
            ChapaPaymentService.PaymentVerifyResponse verification = chapaPaymentService.verifyPayment(txRef);

            if ("success".equalsIgnoreCase(verification.getStatus())) {
                ride.setPaymentStatus("COMPLETED");
                ride.setChapaChargeId(verification.getTxRef());
                rideRepository.save(ride);
                log.info("Ride {} payment marked as COMPLETED", ride.getId());
            } else {
                log.warn("Verification returned non-success status: {}", verification.getStatus());
                ride.setPaymentStatus("PAYMENT_FAILED");
                rideRepository.save(ride);
            }
        } catch (Exception e) {
            log.error("Error handling payment success for ride {}: {}", ride.getId(), e.getMessage());
            ride.setPaymentStatus("PAYMENT_FAILED");
            rideRepository.save(ride);
        }
    }

    /**
     * Handle pending payment (payment in progress)
     */
    private void handlePaymentPending(Ride ride, String txRef) {
        log.info("Payment pending for ride {} - TxRef: {}", ride.getId(), txRef);
        ride.setPaymentStatus("PENDING_VERIFICATION");
        rideRepository.save(ride);
    }

    /**
     * Handle failed payment
     */
    private void handlePaymentFailure(Ride ride, String txRef, String failureStatus) {
        log.error("Payment failed for ride {} - TxRef: {}, Status: {}", ride.getId(), txRef, failureStatus);
        ride.setPaymentStatus("PAYMENT_FAILED");
        rideRepository.save(ride);
    }

    /**
     * Helper method to create standardized response
     */
    private Map<String, String> createResponse(String status, String message) {
        Map<String, String> response = new HashMap<>();
        response.put("status", status);
        response.put("message", message);
        return response;
    }
}
