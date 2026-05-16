package com.IoT.smart_bike_rental_backend.controller;

import com.IoT.smart_bike_rental_backend.model.Ride;
import com.IoT.smart_bike_rental_backend.repository.Riderepository;
import com.IoT.smart_bike_rental_backend.service.ChapaPaymentService;
import com.IoT.smart_bike_rental_backend.service.ChapaPaymentService.WebhookParseResult;
import com.yaphet.chapa.model.VerifyResponseData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PaymentWebhookController
 *
 * Receives payment status notifications from Chapa and updates the Ride accordingly.
 *
 * Endpoint: POST /api/webhooks/payment/chapa
 *
 * Processing order (NEVER skip a step):
 *
 *   1. verifyWebhookSignature(payload, signature)
 *        └─ HMAC-SHA256 — rejects tampered or spoofed requests
 *
 *   2. parseWebhookPayment(payload)
 *        └─ Returns WebhookParseResult(event, status, txRef)
 *
 *   3. Guard: txRef must start with "BIKE-RIDE-"
 *        └─ Ignores webhooks from other Chapa integrations sharing the same account
 *
 *   4. Look up Ride by chapaTxRef
 *
 *   5. Branch on WebhookParseResult:
 *        isPaymentSuccess() → verifyTransaction(txRef) → mark COMPLETED
 *        isPaymentPending() → mark PENDING_VERIFICATION
 *        isPaymentFailure() → mark PAYMENT_FAILED
 */
@RestController
@RequestMapping("/api/webhooks/payment")
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookController {

    private final ChapaPaymentService chapaPaymentService;
    private final Riderepository rideRepository;

    /**
     * Chapa webhook endpoint.
     *
     * Chapa sends the raw JSON body and includes the HMAC-SHA256 signature in
     * the {@code x-chapa-signature} header (some environments use {@code Authorization}).
     * Both header names are accepted here.
     *
     * @param chapaSignature Header: {@code x-chapa-signature} (preferred) or {@code Authorization}.
     * @param payload   Raw JSON body from Chapa.
     * @return          Always 200 OK (Chapa retries on non-2xx — we log errors internally).
     */
    @PostMapping("/chapa")
    public ResponseEntity<Map<String, String>> handleChapaWebhook(
            @RequestHeader(value = "x-chapa-signature", required = false) String chapaSignature,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody String payload) {

        // Prefer x-chapa-signature; fall back to Authorization header
        String signature = (chapaSignature != null) ? chapaSignature : authHeader;

        log.info("Received Chapa webhook notification");

        // ── Step 1: Verify signature ──────────────────────────────────────────
        if (!chapaPaymentService.verifyWebhookSignature(payload, signature)) {
            log.warn("Webhook rejected — invalid signature");
            // Return 200 to prevent Chapa from retrying a permanently invalid request
            return ResponseEntity.ok(response("rejected", "Invalid webhook signature"));
        }

        // ── Step 2: Parse the payload ─────────────────────────────────────────
        WebhookParseResult parsed;
        try {
            parsed = chapaPaymentService.parseWebhookPayment(payload);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload: {}", e.getMessage());
            return ResponseEntity.ok(response("error", "Payload parsing failed"));
        }

        String txRef = parsed.txRef();

        // ── Step 3: Guard — only process bike-ride payments ───────────────────
        if (!txRef.startsWith("BIKE-RIDE-")) {
            log.warn("Webhook ignored — txRef '{}' is not a bike ride payment", txRef);
            return ResponseEntity.ok(response("ignored", "Not a bike ride transaction"));
        }

        // ── Step 4: Look up the Ride ──────────────────────────────────────────
        var rideOptional = rideRepository.findByChapaTxRef(txRef);
        if (rideOptional.isEmpty()) {
            log.warn("Ride not found for txRef: {}", txRef);
            return ResponseEntity.ok(response("error", "Ride not found for txRef: " + txRef));
        }

        Ride ride = rideOptional.get();

        // ── Step 5: Dispatch on event type ────────────────────────────────────
        if (parsed.isPaymentSuccess()) {
            handlePaymentSuccess(ride, txRef);
        } else if (parsed.isPaymentFailure()) {
            handlePaymentFailure(ride, txRef, parsed.status());
        } else {
            // Treat anything else (e.g. "pending") as pending verification
            handlePaymentPending(ride, txRef);
        }

        return ResponseEntity.ok(response("success", "Webhook processed successfully"));
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    /**
     * Handles a successful payment event.
     *
     * Always re-verifies with Chapa before marking the ride COMPLETED.
     * This prevents a replay attack where someone posts a fake "charge.success"
     * webhook — the re-verification call to Chapa confirms the money actually moved.
     */
    private void handlePaymentSuccess(Ride ride, String txRef) {
        try {
            log.info("Payment success event received — rideId: {}, txRef: {}", ride.getId(), txRef);

            // Re-verify with Chapa SDK (GET /transaction/verify/{txRef})
            VerifyResponseData verification = chapaPaymentService.verifyTransaction(txRef);
            String verifiedStatus = verification.getData() != null
                    ? verification.getData().getStatus()
                    : "unknown";

            if ("success".equalsIgnoreCase(verifiedStatus)) {
                ride.setPaymentStatus("COMPLETED");
                ride.setChapaChargeId(txRef); // txRef serves as the unique Chapa charge identifier
                rideRepository.save(ride);
                log.info("Ride {} marked COMPLETED — txRef: {}", ride.getId(), txRef);
            } else {
                log.warn("Chapa re-verification returned '{}' for txRef: {} — marking PAYMENT_FAILED",
                        verifiedStatus, txRef);
                ride.setPaymentStatus("PAYMENT_FAILED");
                rideRepository.save(ride);
            }

        } catch (Exception e) {
            log.error("Error handling payment success for rideId {}: {}", ride.getId(), e.getMessage());
            ride.setPaymentStatus("PAYMENT_FAILED");
            rideRepository.save(ride);
        }
    }

    /**
     * Handles a payment still in progress.
     * Sets status to PENDING_VERIFICATION so an admin or scheduled job can follow up.
     */
    private void handlePaymentPending(Ride ride, String txRef) {
        log.info("Payment pending for rideId: {}, txRef: {}", ride.getId(), txRef);
        ride.setPaymentStatus("PENDING_VERIFICATION");
        rideRepository.save(ride);
    }

    /**
     * Handles a failed or cancelled payment.
     */
    private void handlePaymentFailure(Ride ride, String txRef, String failureStatus) {
        log.error("Payment failed for rideId: {}, txRef: {}, status: {}",
                ride.getId(), txRef, failureStatus);
        ride.setPaymentStatus("PAYMENT_FAILED");
        rideRepository.save(ride);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, String> response(String status, String message) {
        return Map.of("status", status, "message", message);
    }
}