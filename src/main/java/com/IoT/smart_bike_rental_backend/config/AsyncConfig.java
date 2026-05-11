package com.IoT.smart_bike_rental_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration to enable async support for Spring
 * Required for @Async annotation to work
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
