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
 * MQTT Service for communicating with ESP32-based smart bike locks
 *
 * Topics:
 * - bike/{bikeId}/command (publish) - Send commands to bikes: LOCK, UNLOCK
 * - bike/{bikeId}/status (subscribe) - Receive status from bikes: LOCKED, UNLOCKED, BATTERY:xx, GPS:lat,lng
 */
@Service
@Slf4j
public class MqttService {

    @Value("${mqtt.broker}")
    private String broker;

    @Value("${mqtt.client-id}")
    private String clientId;

    private MqttClient client;
    private volatile boolean isConnected = false;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private int reconnectAttempts = 0;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    // Using @Lazy to avoid circular dependency
    @Autowired
    @Lazy
    private BikeService bikeService;

    @PostConstruct
    public void init() {
        // Connect asynchronously to avoid blocking Spring startup
        connectAsync();
    }

    @Async
    public void connectAsync() {
        try {
            Thread.sleep(2000); // Wait for Spring to fully initialize
            client = new MqttClient(broker, clientId + "_" + System.currentTimeMillis());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(false); // Use persistent session to maintain subscriptions
            options.setConnectionTimeout(30); // Increased timeout
            options.setKeepAliveInterval(30); // Increased keep-alive interval
            options.setMaxInflight(100);
            options.setUserName(username);
            options.setPassword(password.toCharArray());

            // Enable SSL/TLS
            options.setSocketFactory(
                    javax.net.ssl.SSLContext.getDefault().getSocketFactory()
            );

            // Set callback for handling incoming messages and connection events
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    log.warn("MQTT connection lost: {}", cause.getMessage(), cause);
                    isConnected = false;
                    // Attempt to reconnect with exponential backoff
                    attemptReconnect();
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleIncomingMessage(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    log.debug("MQTT message delivery complete");
                }
            });

            client.connect(options);
            isConnected = true;
            reconnectAttempts = 0; // Reset on successful connection
            log.info("Successfully connected to MQTT broker at {}", broker);

            // Subscribe to all bike status topics
            subscribeToStatusTopics();

        } catch (MqttException e) {
            log.error("Failed to connect to MQTT broker: {}", e.getMessage(), e);
            isConnected = false;
            attemptReconnect();
        } catch (InterruptedException e) {
            log.error("MQTT initialization interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Unexpected error during MQTT initialization: {}", e.getMessage(), e);
            isConnected = false;
        }
    }

    /**
     * Attempt to reconnect with exponential backoff
     */
    private void attemptReconnect() {
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            long delayMs = Math.min(1000 * (long) Math.pow(2, reconnectAttempts - 1), 60000); // Max 60 seconds
            log.info("Scheduling MQTT reconnection attempt {} in {}ms", reconnectAttempts, delayMs);

            try {
                Thread.sleep(delayMs);
                connectAsync();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            log.error("Max MQTT reconnection attempts ({}) reached. Application will continue without MQTT.", MAX_RECONNECT_ATTEMPTS);
        }
    }

    /**
     * Subscribe to bike status topics
     * Pattern: bike/+/status - receives status from all bikes
     */
    private void subscribeToStatusTopics() {
        if (!isConnected || client == null) {
            log.warn("Cannot subscribe - MQTT client not connected");
            return;
        }

        try {
            // Subscribe to all bike status messages with QoS 1
            client.subscribe("bike/+/status", 1);
            log.info("Successfully subscribed to bike/+/status topic");
        } catch (MqttException e) {
            log.error("Failed to subscribe to status topics: {}", e.getMessage(), e);
            isConnected = false;
        }
    }

    /**
     * Handle incoming MQTT messages from bikes
     */
    private void handleIncomingMessage(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            log.debug("Received MQTT message - Topic: {}, Payload: {}", topic, payload);

            // Parse topic to extract bikeId
            // Expected format: bike/{bikeId}/status
            String[] parts = topic.split("/");
            if (parts.length >= 3 && "bike".equals(parts[0]) && "status".equals(parts[2])) {
                String bikeId = parts[1];

                // Process the status update asynchronously to avoid blocking MQTT callback
                if (bikeService != null) {
                    try {
                        bikeService.processStatusUpdate(bikeId, payload);
                    } catch (Exception e) {
                        log.error("Error processing status update for bike {}: {}", bikeId, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error handling incoming MQTT message: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish a message to an MQTT topic
     *
     * @param topic The topic to publish to (e.g., bike/{bikeId}/command)
     * @param message The message payload (e.g., UNLOCK, LOCK)
     */
    public void publish(String topic, String message) {
        if (client == null || !client.isConnected()) {
            log.warn("MQTT client not connected, cannot publish message to {}", topic);
            return;
        }

        try {
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(1); // At least once delivery
            mqttMessage.setRetained(false);

            client.publish(topic, mqttMessage);
            log.debug("Published MQTT message - Topic: {}, Payload: {}", topic, message);
        } catch (MqttException e) {
            log.error("Failed to publish MQTT message to {}: {}", topic, e.getMessage(), e);
        }
    }

    /**
     * Send unlock command to a specific bike
     */
    public void sendUnlockCommand(String bikeId) {
        publish("bike/" + bikeId + "/command", "UNLOCK");
    }

    /**
     * Send lock command to a specific bike
     */
    public void sendLockCommand(String bikeId) {
        publish("bike/" + bikeId + "/command", "LOCK");
    }

    /**
     * Check if MQTT client is connected
     */
    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    /**
     * Get connection status message
     */
    public String getConnectionStatus() {
        if (client != null && client.isConnected()) {
            return "Connected to " + broker;
        } else {
            return "Disconnected from " + broker;
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
                log.info("MQTT client closed successfully");
            } catch (MqttException e) {
                log.error("Error disconnecting MQTT client: {}", e.getMessage(), e);
            }
        }
    }
}
