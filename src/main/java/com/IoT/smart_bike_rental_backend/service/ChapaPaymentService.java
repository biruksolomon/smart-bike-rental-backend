package com.IoT.smart_bike_rental_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for handling Chapa payment gateway integration for bike rental rides.
 * Manages payment initialization, verification, and webhook handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChapaPaymentService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${chapa.api-url:https://api.chapa.co/v1}")
    private String chapaApiUrl;

    @Value("${chapa.secret-key}")
    private String chapaSecretKey;

    @Value("${chapa.webhook-secret}")
    private String chapaWebhookSecret;

    @Value("${app.callback-url}")
    private String callbackUrl;

    /**
     * Initialize a payment for a bike ride
     * This pre-authorizes the payment amount with Chapa
     */
    public PaymentInitResponse initializePayment(String txRef, String userEmail, String firstName,
                                                 BigDecimal amount) {
        try {
            log.info("Initializing Chapa payment for ride {} - Amount: {}, User: {}", txRef, amount, userEmail);

            String url = chapaApiUrl + "/initialize";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("amount", amount.toPlainString());
            requestBody.put("currency", "ETB");
            requestBody.put("email", userEmail);
            requestBody.put("first_name", firstName);
            requestBody.put("tx_ref", txRef);
            requestBody.put("return_url", callbackUrl + "/payment/success");
            requestBody.put("customization[title]", "Bike Rental Payment");
            requestBody.put("customization[description]", "Smart Bike Rental Service");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(chapaSecretKey);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            String response = restTemplate.postForObject(url, entity, String.class);
            JsonNode responseJson = objectMapper.readTree(response);

            if ("success".equals(responseJson.get("status").asText())) {
                PaymentInitResponse paymentResponse = new PaymentInitResponse();
                paymentResponse.setTxRef(txRef);
                paymentResponse.setCheckoutUrl(responseJson.get("data").get("checkout_url").asText());
                paymentResponse.setStatus("AUTHORIZED");
                log.info("Chapa payment initialized successfully for txRef: {}", txRef);
                return paymentResponse;
            } else {
                throw new RuntimeException("Chapa initialization failed: " + responseJson.get("message").asText());
            }

        } catch (Exception e) {
            log.error("Error initializing Chapa payment for txRef {}: {}", txRef, e.getMessage());
            throw new RuntimeException("Payment initialization failed: " + e.getMessage());
        }
    }

    /**
     * Verify payment status from Chapa
     * Called by webhook to confirm payment success or failure
     */
    public PaymentVerifyResponse verifyPayment(String txRef) {
        try {
            log.info("Verifying payment status for txRef: {}", txRef);

            String url = chapaApiUrl + "/verify/" + txRef;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(chapaSecretKey);

            HttpEntity<String> entity = new HttpEntity<>(headers);
            String response = restTemplate.postForObject(url, entity, String.class);
            JsonNode responseJson = objectMapper.readTree(response);

            PaymentVerifyResponse verifyResponse = new PaymentVerifyResponse();
            verifyResponse.setTxRef(txRef);
            verifyResponse.setStatus(responseJson.get("data").get("status").asText());
            verifyResponse.setAmount(new BigDecimal(responseJson.get("data").get("amount").asText()));
            verifyResponse.setChargeResponseCode(responseJson.get("data").get("charge_response_code").asText());
            verifyResponse.setChargeResponseMessage(responseJson.get("data").get("charge_response_message").asText());

            log.info("Payment verified for txRef: {} - Status: {}", txRef, verifyResponse.getStatus());
            return verifyResponse;

        } catch (Exception e) {
            log.error("Error verifying payment for txRef {}: {}", txRef, e.getMessage());
            throw new RuntimeException("Payment verification failed: " + e.getMessage());
        }
    }

    /**
     * Validate webhook signature for security
     * Ensures the webhook came from Chapa
     */
    public boolean validateWebhookSignature(String signature, String payload) {
        try {
            // Hash the payload with webhook secret
            // Compare with provided signature
            log.info("Validating webhook signature");
            // Implementation depends on Chapa's signature method
            return true; // TODO: Implement proper signature validation
        } catch (Exception e) {
            log.error("Webhook signature validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Response object for payment initialization
     */
    public static class PaymentInitResponse {
        private String txRef;
        private String checkoutUrl;
        private String status;

        public String getTxRef() { return txRef; }
        public void setTxRef(String txRef) { this.txRef = txRef; }

        public String getCheckoutUrl() { return checkoutUrl; }
        public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    /**
     * Response object for payment verification
     */
    public static class PaymentVerifyResponse {
        private String txRef;
        private String status;
        private BigDecimal amount;
        private String chargeResponseCode;
        private String chargeResponseMessage;

        public String getTxRef() { return txRef; }
        public void setTxRef(String txRef) { this.txRef = txRef; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }

        public String getChargeResponseCode() { return chargeResponseCode; }
        public void setChargeResponseCode(String chargeResponseCode) { this.chargeResponseCode = chargeResponseCode; }

        public String getChargeResponseMessage() { return chargeResponseMessage; }
        public void setChargeResponseMessage(String chargeResponseMessage) { this.chargeResponseMessage = chargeResponseMessage; }
    }
}
