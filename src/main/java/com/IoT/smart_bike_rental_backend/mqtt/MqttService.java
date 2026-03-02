package com.IoT.smart_bike_rental_backend.mqtt;

import jakarta.annotation.PostConstruct;
import org.eclipse.paho.client.mqttv3.MqttClient;
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

    @PostConstruct
    public void init() throws Exception {

        client = new MqttClient(broker, clientId);
        client.connect();

        client.subscribe("bike/+/status", (topic, message) -> {
            String payload = new String(message.getPayload());
            System.out.println("Received: " + topic + " -> " + payload);
        });
    }

    public void publish(String topic, String message) throws Exception {
        client.publish(topic, new MqttMessage(message.getBytes()));
    }
}