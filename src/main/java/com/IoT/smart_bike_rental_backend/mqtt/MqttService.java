package com.IoT.smart_bike_rental_backend.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MqttService {

    @Value("${mqtt.broker}")
    private String broker;

    @Value("${mqtt.client-id}")
    private String clientId;

    private MqttClient client;
    private boolean isConnected = false;

    @PostConstruct
    public void init() {
        try {
            client = new MqttClient(broker, clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);

            client.connect(options);
            isConnected = true;
            System.out.println("Connected to MQTT broker at " + broker);

            client.subscribe("bike/+/status", (topic, message) -> {
                String payload = new String(message.getPayload());
                System.out.println("Received: " + topic + " -> " + payload);
            });

        } catch (MqttException e) {
            System.err.println("Failed to connect to MQTT broker: " + e.getMessage());
            // Don't throw exception - allow app to start without MQTT
            isConnected = false;
        }
    }

    public void publish(String topic, String message) {
        if (!isConnected || client == null || !client.isConnected()) {
            System.err.println("MQTT client not connected, cannot publish message");
            return;
        }

        try {
            client.publish(topic, new MqttMessage(message.getBytes()));
        } catch (MqttException e) {
            System.err.println("Failed to publish MQTT message: " + e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
                client.close();
            } catch (MqttException e) {
                System.err.println("Error disconnecting MQTT client: " + e.getMessage());
            }
        }
    }
}