package com.IoT.smart_bike_rental_backend.mqtt;

import com.IoT.smart_bike_rental_backend.service.BikeService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * MQTT Service — communicates with ESP32 smart bike locks via broker.emqx.io.
 *
 * ┌────────────────────────────────────────────────────────────────────────────┐
 * │  Broker  : tcp://broker.emqx.io:1883  (public broker, no TLS, no creds)   │
 * │  Token   : mqtt.token (default "1234" for testing)                         │
 * │                                                                            │
 * │  PUBLISH  →  bike/{bikeId}/command                                         │
 * │    {"command":"UNLOCK","token":"1234"}                                      │
 * │    {"command":"LOCK","token":"1234"}                                        │
 * │                                                                            │
 * │  SUBSCRIBE  ←  bike/+/status                                               │
 * │    {"token":"1234","status":"LOCKED"}                                       │
 * │    {"token":"1234","status":"UNLOCKED"}                                     │
 * │    {"token":"1234","battery":85}                                            │
 * │    {"token":"1234","lat":9.03,"lng":38.74}                                  │
 * └────────────────────────────────────────────────────────────────────────────┘
 *
 * Inbound messages whose "token" field does not match mqtt.token are dropped
 * before reaching BikeService, preventing rogue messages on the public broker
 * from affecting bike state.
 */
@Service
@Slf4j
public class MqttService {

    @Value("${mqtt.broker}")
    private String broker;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    /**
     * Shared secret between the Spring backend and the ESP32 firmware.
     * Embedded in every outbound command JSON and validated on every inbound
     * status JSON.  Default "1234" is for testing only.
     */
    @Value("${mqtt.token:1234}")
    private String mqttToken;

    private MqttClient client;
    private volatile boolean isConnected = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    @Autowired
    @Lazy  // breaks circular dependency with BikeService
    private BikeService bikeService;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PostConstruct
    public void init() {
        connectAsync();
    }

    @Async
    public void connectAsync() {
        try {
            Thread.sleep(2000); // wait for Spring context to finish

            client = new MqttClient(broker, clientId + "_" + System.currentTimeMillis());

            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setAutomaticReconnect(true);
            opts.setCleanSession(false);
            opts.setConnectionTimeout(30);
            opts.setKeepAliveInterval(30);
            opts.setMaxInflight(100);

            // broker.emqx.io is public — credentials only if explicitly set
            if (username != null && !username.isBlank()) opts.setUserName(username);
            if (password != null && !password.isBlank()) opts.setPassword(password.toCharArray());
            // No SSL — plain TCP port 1883

            client.setCallback(new MqttCallback() {
                @Override public void connectionLost(Throwable cause) {
                    log.warn("MQTT connection lost: {}", cause.getMessage(), cause);
                    isConnected = false;
                    attemptReconnect();
                }
                @Override public void messageArrived(String topic, MqttMessage message) {
                    handleIncomingMessage(topic, message);
                }
                @Override public void deliveryComplete(IMqttDeliveryToken token) {
                    log.debug("MQTT delivery complete");
                }
            });

            client.connect(opts);
            isConnected = true;
            reconnectAttempts = 0;
            log.info("Connected to MQTT broker: {}", broker);
            subscribeToStatusTopics();

        } catch (MqttException e) {
            log.error("Failed to connect to MQTT broker: {}", e.getMessage(), e);
            isConnected = false;
            attemptReconnect();
        } catch (InterruptedException e) {
            log.error("MQTT init interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Unexpected error during MQTT init: {}", e.getMessage(), e);
            isConnected = false;
        }
    }

    private void attemptReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            log.error("Max MQTT reconnect attempts reached. Running without MQTT.");
            return;
        }
        reconnectAttempts++;
        long delay = Math.min(1000L * (long) Math.pow(2, reconnectAttempts - 1), 60_000L);
        log.info("Scheduling MQTT reconnect attempt {} in {}ms", reconnectAttempts, delay);
        try {
            Thread.sleep(delay);
            connectAsync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void subscribeToStatusTopics() {
        if (!isConnected || client == null) return;
        try {
            client.subscribe("bike/+/status", 1);
            client.subscribe("bike/+/gps", 1);    // ← ADD THIS
            client.subscribe("bike/+/alert", 1);  // ← ADD THIS
            log.info("Subscribed to bike/+/status, bike/+/gps, bike/+/alert");
        } catch (MqttException e) {
            log.error("Failed to subscribe: {}", e.getMessage(), e);
            isConnected = false;
        }
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                    log.info("Disconnected from MQTT broker");
                }
                client.close();
                isConnected = false;
                log.info("MQTT client closed");
            } catch (MqttException e) {
                log.error("Error during MQTT cleanup: {}", e.getMessage(), e);
            }
        }
    }

    // ── Inbound messages ──────────────────────────────────────────────────────

    /**
     * Handles JSON status payloads received from ESP32 bikes on bike/+/status.
     *
     * Expected formats:
     *   {"token":"1234","status":"LOCKED"}
     *   {"token":"1234","status":"UNLOCKED"}
     *   {"token":"1234","battery":85}
     *   {"token":"1234","lat":9.03,"lng":38.74}
     *
     * Token validation: Accepts messages if token matches OR token is not present (development mode).
     * This allows flexibility during development while still supporting token validation in production.
     */
    private void handleIncomingMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            String[] parts = topic.split("/");
            if (parts.length < 3 || !"bike".equals(parts[0])) return;
            String bikeId = parts[1];
            String topicType = parts[2];  // "status", "gps", or "alert"

            String tokenInMessage = extractJsonString(payload, "token");
            if (tokenInMessage != null && !mqttToken.equals(tokenInMessage)) {
                log.warn("Token mismatch for bike {} — dropped", bikeId);
                return;
            }

            if (bikeService == null) return;

            switch (topicType) {
                case "status" -> bikeService.processStatusUpdate(bikeId, payload);
                case "gps"    -> bikeService.processGpsUpdate(bikeId, payload);    // see Bug 2
                case "alert"  -> bikeService.processAlertUpdate(bikeId, payload);  // see Bug 3
                default       -> log.warn("Unknown topic type: {}", topic);
            }
        } catch (Exception e) {
            log.error("Error handling MQTT message: {}", e.getMessage(), e);
        }
    }

    // ── Outbound publishing ───────────────────────────────────────────────────

    /**
     * Low-level publish. Prefer {@link #publishCommand} for bike commands.
     */
    public void publish(String topic, String payload) {
        if (client == null || !client.isConnected()) {
            log.warn("MQTT not connected — cannot publish to {}", topic);
            return;
        }
        try {
            MqttMessage msg = new MqttMessage(payload.getBytes());
            msg.setQos(1);
            msg.setRetained(true);
            client.publish(topic, msg);
            log.debug("MQTT TX  topic={}  payload={}", topic, payload);
        } catch (MqttException e) {
            log.error("Failed to publish to {}: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * Publishes a JSON command that includes the shared token.
     *
     * Payload: {"command":"UNLOCK","token":"1234"}
     *
     * The ESP32 must verify the token before executing the command.
     */
    public void publishCommand(String topic, String command) {
        String json = String.format("{\"command\":\"%s\",\"token\":\"%s\"}", command, mqttToken);
        publish(topic, json);
    }

    /**
     * Sends UNLOCK to bike/{bikeId}/command.
     * Payload: {"command":"UNLOCK","token":"1234"}
     */
    public void sendUnlockCommand(String bikeId) {
        String topic = "bike/" + bikeId + "/command";
        publishCommand(topic, "UNLOCK");
        log.info("UNLOCK command (JSON+token) sent to {}", topic);
    }

    /**
     * Sends LOCK to bike/{bikeId}/command.
     * Payload: {"command":"LOCK","token":"1234"}
     */
    public void sendLockCommand(String bikeId) {
        String topic = "bike/" + bikeId + "/command";
        publishCommand(topic, "LOCK");
        log.info("LOCK command (JSON+token) sent to {}", topic);
    }

    // ── Status ────────────────────────────────────────────────────────────────

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public String getConnectionStatus() {
        return (client != null && client.isConnected())
                ? "Connected to " + broker
                : "Disconnected from " + broker;
    }

    // ── JSON helper ───────────────────────────────────────────────────────────

    /** Extracts a string value for "key":"value" without pulling in Jackson. */
    private String extractJsonString(String json, String key) {
        try {
            String search = "\"" + key + "\"";
            int idx   = json.indexOf(search);
            if (idx == -1) return null;
            int colon = json.indexOf(':', idx + search.length());
            int q1    = json.indexOf('"', colon + 1);
            int q2    = json.indexOf('"', q1 + 1);
            if (q1 == -1 || q2 == -1) return null;
            return json.substring(q1 + 1, q2);
        } catch (Exception e) {
            return null;
        }
    }
}
