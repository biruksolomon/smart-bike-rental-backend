package com.IoT.smart_bike_rental_backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaphet.chapa.Chapa;
import com.yaphet.chapa.model.Customization;
import com.yaphet.chapa.model.InitializeResponseData;
import com.yaphet.chapa.model.PostData;
import com.yaphet.chapa.model.VerifyResponseData;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * ChapaPaymentService
 *
 * Handles all Chapa payment gateway operations for the Smart Bike Rental system.
 *
 * Architecture overview:
 * ┌────────────────────────────────────────────────────────────┐
 * │                   ChapaPaymentService                      │
 * │                                                            │
 * │  @PostConstruct init()                                     │
 * │    └─ Reads ${chapa.secret-key} → new Chapa(secretKey)     │
 * │                                                            │
 * │  initializePayment(txRef, email, firstName, amount)        │
 * │    └─ Builds PostData + Customization (SDK objects)        │
 * │    └─ chapa.initialize(postData) → InitializeResponseData  │
 * │                                                            │
 * │  verifyTransaction(txRef)                                  │
 * │    └─ chapa.verify(txRef) → VerifyResponseData             │
 * │                                                            │
 * │  verifyWebhookSignature(payload, signature)                │
 * │    └─ HMAC-SHA256(payload, webhookSecret) == signature     │
 * │                                                            │
 * │  parseWebhookPayment(payload)                              │
 * │    └─ Returns WebhookParseResult(event, status, txRef)     │
 * └────────────────────────────────────────────────────────────┘
 *
 * Key design decision — SDK vs raw HTTP:
 *   The official Chapa SDK (io.github.yaphet17:Chapa) wraps the REST API.
 *   initializePayment() and verifyTransaction() use the SDK's PostData /
 *   chapa.initialize() and chapa.verify() methods — avoiding manual JSON bodies
 *   and Authorization header management for those core operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapaPaymentService {

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    /** Shared RestTemplate (timeouts configured in RestClientConfig). */
    private final RestTemplate restTemplate;

    /** Jackson ObjectMapper shared bean from RestClientConfig. */
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Configuration — values injected from application.yml / env vars
    // -------------------------------------------------------------------------

    /**
     * Your Chapa secret key.
     *
     * application.yml   → chapa.secret-key: CHASECK_TEST-0Td4aInPwTC3s4rSLoM3xjcPQ1YV0hmX
     * Docker / Render   → chapa.secret-key: ${CHAPA_SECRET_KEY}
     *
     * The Chapa SDK uses this key to sign every outgoing API request
     * once the Chapa instance is built in @PostConstruct.
     */
    @Value("${chapa.secret-key}")
    private String chapaSecretKey;

    /**
     * HMAC-SHA256 secret for verifying inbound webhooks from Chapa.
     *
     * application.yml   → chapa.webhook-secret: CHAPA_WEBHOOK_SECRET_2024_...
     * Docker / Render   → chapa.webhook-secret: ${CHAPA_WEBHOOK_SECRET}
     */
    @Value("${chapa.webhook-secret}")
    private String chapaWebhookSecret;

    /**
     * Base callback URL — Chapa appends "/payment/success" to redirect the user after payment.
     *
     * application.yml → app.callback-url: http://localhost:7080/api/payment/callback
     * Docker          → app.callback-url: ${APP_CALLBACK_URL:http://app:9080/api/payment/callback}
     * Render          → app.callback-url: ${APP_CALLBACK_URL}
     */
    @Value("${app.callback-url}")
    private String callbackUrl;

    // -------------------------------------------------------------------------
    // Chapa SDK instance
    // -------------------------------------------------------------------------

    /**
     * The single shared Chapa SDK instance for this service.
     *
     * Why volatile?
     *   @PostConstruct runs on the startup thread. Multiple request threads will
     *   read this field later; volatile guarantees they see the initialized value
     *   without needing full synchronization on every call.
     *
     * Why not a Spring @Bean?
     *   The Chapa constructor needs chapaSecretKey, which is only available after
     *   @Value injection — that happens post-constructor but pre-publish.
     *   @PostConstruct is the correct lifecycle hook for this initialization.
     */
    private volatile Chapa chapa;

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    /**
     * Builds the Chapa SDK instance after all @Value fields are injected.
     *
     * Fails fast: missing or blank secret key causes the application context to
     * refuse to start, preventing silent NullPointerExceptions at runtime.
     */
    @PostConstruct
    public void init() {
        if (chapaSecretKey == null || chapaSecretKey.isBlank()) {
            throw new IllegalStateException(
                    "Chapa secret key is not configured. " +
                            "Set chapa.secret-key in application.yml or the CHAPA_SECRET_KEY environment variable."
            );
        }
        this.chapa = new Chapa(chapaSecretKey);
        log.info("Chapa SDK initialized successfully.");
    }

    // -------------------------------------------------------------------------
    // Payment initialization  ← THE CORE METHOD
    // -------------------------------------------------------------------------

    /**
     * Initializes a Chapa payment for a bike ride and returns the checkout URL.
     *
     * Called by {@link BookingService#processBooking(Long, String)} after the
     * user and bike have been validated and the unique txRef has been generated.
     *
     * Flow:
     *   BookingService
     *     → generateTransactionReference(userId, bikeId)   // "BIKE-RIDE-{userId}-{bikeId}-{ms}"
     *     → initializePayment(txRef, email, firstName, 100.00)
     *         → PostData + Customization built here
     *         → chapa.initialize(postData)                 // SDK → Chapa API
     *     ← InitializeResponseData (contains checkoutUrl)
     *     → rideService.startRide(...)                     // MQTT UNLOCK
     *     → return BookingResult.success(..., checkoutUrl, txRef)
     *
     * Why PostData and not a raw Map / JSON string?
     *   Using the SDK's PostData model means the library handles:
     *     • Serializing to the correct JSON field names (tx_ref, callback_url, etc.)
     *     • Attaching Authorization: Bearer {secretKey} to the request
     *     • Deserializing the response into InitializeResponseData
     *   Manual JSON construction (the old approach) is error-prone and couples us
     *   to Chapa's raw API format instead of the SDK contract.
     *
     * @param txRef      Unique transaction reference stored on the {@link com.IoT.smart_bike_rental_backend.model.Ride}.
     *                   Format: "BIKE-RIDE-{userId}-{bikeId}-{epochMs}"
     * @param userEmail  Rider's email (shown on Chapa checkout page and receipt).
     * @param firstName  Rider's first name (shown on Chapa checkout page).
     * @param amount     Amount in ETB to charge. Must be greater than zero.
     * @return           {@link InitializeResponseData} — call {@code .getData().getCheckoutUrl()}
     *                   to get the URL to redirect the user to.
     * @throws RuntimeException wrapping any SDK or network exception.
     */
    public InitializeResponseData initializePayment(String txRef,
                                                    String userEmail,
                                                    String firstName,
                                                    BigDecimal amount) {
        log.info("Initializing Chapa payment – txRef: {}, user: {}, amount: {} ETB",
                txRef, userEmail, amount);

        // 1. Customization: branding shown on the Chapa-hosted payment page
        Customization customization = new Customization()
                .setTitle("Smart Bike")
                .setDescription("Pay for your bike ride securely via Chapa")
                .setLogo("https://smartbike.com/logo.png"); // TODO: replace with real logo URL

        // 2. PostData — the SDK's typed model for the initialize request body.
        //
        //    This maps to the following JSON that Chapa expects at
        //    POST https://api.chapa.co/v1/transaction/initialize :
        //
        //    {
        //      "amount":       "100.00",
        //      "currency":     "ETB",
        //      "email":        "rider@example.com",
        //      "first_name":   "Abebe",
        //      "tx_ref":       "BIKE-RIDE-1-B001-1718000000000",
        //      "callback_url": "http://localhost:7080/api/payment/callback/payment/success",
        //      "customization": {
        //        "title":       "Smart Bike Rental",
        //        "description": "Pay for your bike ride securely via Chapa",
        //        "logo":        "https://smartbike.com/logo.png"
        //      }
        //    }
        //
        //    The SDK serializes this and adds the Authorization header internally.
        PostData postData = new PostData()
                .setAmount(amount)
                .setCurrency("ETB")
                .setEmail(userEmail)
                .setFirstName(firstName)
                // User.lastName is not modelled separately in this project's User entity;
                // first name alone is acceptable for Chapa.
                .setTxRef(txRef)
                .setCallbackUrl(callbackUrl + "/payment/success")
                .setCustomization(customization);

        // 3. Call the SDK — one line replaces the entire manual HTTP dance
        try {
            InitializeResponseData response = chapa.initialize(postData);
            log.info("Chapa payment initialized – txRef: {}, checkoutUrl: {}",
                    txRef,
                    response.getData() != null ? response.getData().getCheckOutUrl() : "N/A");
            return response;
        } catch (Throwable e) {
            log.error("Chapa initialization failed – txRef: {}, error: {}", txRef, e.getMessage());
            throw new RuntimeException("Payment initialization failed for txRef: " + txRef, e);
        }
    }

    // -------------------------------------------------------------------------
    // Transaction verification
    // -------------------------------------------------------------------------

    /**
     * Verifies a completed Chapa transaction by txRef.
     *
     * Called by {@link com.IoT.smart_bike_rental_backend.controller.PaymentWebhookController}
     * inside handlePaymentSuccess() to confirm the payment actually completed on
     * Chapa's side before marking the Ride as COMPLETED in the database.
     *
     * The SDK calls GET https://api.chapa.co/v1/transaction/verify/{txRef}.
     *
     * @param txRef Transaction reference to verify (must be non-null and non-blank).
     * @return      {@link VerifyResponseData} — check {@code .getData().getStatus()} for "success".
     * @throws RuntimeException on SDK or network failure.
     */
    public VerifyResponseData verifyTransaction(String txRef) {
        if (txRef == null || txRef.isBlank()) {
            throw new IllegalArgumentException("Transaction reference cannot be null or empty");
        }
        log.info("Verifying Chapa transaction – txRef: {}", txRef);
        try {
            VerifyResponseData response = chapa.verify(txRef);
            log.info("Chapa verification result – txRef: {}, status: {}",
                    txRef,
                    response.getData() != null ? response.getData().getStatus() : "unknown");
            return response;
        } catch (Throwable e) {
            log.error("Chapa verification failed – txRef: {}, error: {}", txRef, e.getMessage());
            throw new RuntimeException("Transaction verification failed for txRef: " + txRef, e);
        }
    }

    // -------------------------------------------------------------------------
    // Webhook signature verification
    // -------------------------------------------------------------------------

    /**
     * Verifies that an inbound webhook request genuinely came from Chapa.
     *
     * Chapa signs each webhook payload with HMAC-SHA256 using your webhook secret
     * and sends the hex digest in the request header (typically {@code x-chapa-signature}
     * or {@code Authorization}). This method recomputes the HMAC and compares.
     *
     * Always call this BEFORE processing any webhook business logic.
     * See {@link com.IoT.smart_bike_rental_backend.controller.PaymentWebhookController}.
     *
     * @param payload   Raw JSON body of the webhook request (must not be null).
     * @param signature Signature value from the request header.
     * @return          {@code true} if valid; {@code false} if invalid or on error.
     */
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (payload == null || payload.isBlank()) {
            log.warn("Webhook payload is null or empty — rejecting");
            return false;
        }
        if (signature == null || signature.isBlank()) {
            log.warn("Webhook signature header is missing — rejecting");
            return false;
        }
        if (chapaWebhookSecret == null || chapaWebhookSecret.isBlank()) {
            log.error("chapa.webhook-secret is not configured — rejecting all webhooks");
            return false;
        }
        try {
            String expected = hmacSha256Hex(payload, chapaWebhookSecret);
            boolean valid = expected.equals(signature.trim());
            if (!valid) {
                log.warn("Webhook signature mismatch — expected: {}, received: {}", expected, signature);
            }
            return valid;
        } catch (Exception e) {
            log.error("Webhook signature verification threw an exception", e);
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Webhook payload parsing
    // -------------------------------------------------------------------------

    /**
     * Parses a Chapa webhook JSON body into a typed {@link WebhookParseResult}.
     *
     * Expected Chapa webhook JSON (payment events):
     * <pre>
     * {
     *   "event":  "charge.success",         // or "charge.failed" / "charge.cancelled"
     *   "status": "success",                // or "failed"
     *   "tx_ref": "BIKE-RIDE-1-B001-..."    // matches Ride.chapaTxRef
     * }
     * </pre>
     *
     * Called by PaymentWebhookController after signature verification passes.
     * The controller then uses the returned txRef to look up the Ride and
     * calls verifyTransaction() before persisting any state change.
     *
     * @param payload Raw JSON string from the webhook body.
     * @return        Parsed {@link WebhookParseResult}.
     * @throws RuntimeException if the JSON cannot be parsed.
     */
    public WebhookParseResult parseWebhookPayment(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Webhook payload cannot be null or empty");
        }
        try {
            JsonNode data = objectMapper.readTree(payload);
            String event  = data.path("event").asText();
            String status = data.path("status").asText();
            String txRef  = data.path("tx_ref").asText();
            log.info("Parsed webhook — event: {}, status: {}, txRef: {}", event, status, txRef);
            return new WebhookParseResult(event, status, txRef);
        } catch (Exception e) {
            log.error("Failed to parse webhook payload: {}", e.getMessage());
            throw new RuntimeException("Webhook payload parsing failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Result type for webhook parsing
    // -------------------------------------------------------------------------

    /**
     * Immutable record carrying the parsed fields from a Chapa webhook payload.
     *
     * @param event  Chapa event name, e.g. {@code "charge.success"}.
     * @param status Payment status string, e.g. {@code "success"}.
     * @param txRef  Transaction reference — matches the value stored in {@code Ride.chapaTxRef}.
     */
    public record WebhookParseResult(String event, String status, String txRef) {

        /** @return true when this represents a successful payment. */
        public boolean isPaymentSuccess() {
            return "charge.success".equals(event) && "success".equalsIgnoreCase(status);
        }

        /** @return true when the payment failed or was cancelled. */
        public boolean isPaymentFailure() {
            return "charge.failed".equals(event) || "charge.cancelled".equals(event);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Computes HMAC-SHA256 over {@code data} using {@code key} and returns
     * the result as a lowercase hexadecimal string.
     *
     * @param data The message to sign (webhook JSON body).
     * @param key  The HMAC secret key (chapa.webhook-secret).
     * @return     Lowercase hex-encoded HMAC-SHA256 digest.
     */
    private String hmacSha256Hex(String data, String key)
            throws NoSuchAlgorithmException, InvalidKeyException {

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}