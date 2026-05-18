package com.IoT.smart_bike_rental_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring @Async support.
 * Required so MqttService.connectAsync() runs on a separate thread
 * instead of blocking the main startup thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
