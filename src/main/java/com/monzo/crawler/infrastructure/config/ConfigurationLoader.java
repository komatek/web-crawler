package com.monzo.crawler.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration loader that reads from application.properties and environment variables
 */
public class ConfigurationLoader {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationLoader.class);
    private final Properties properties;

    public ConfigurationLoader() {
        this.properties = loadProperties();
    }

    private Properties loadProperties() {
        Properties props = new Properties();

        // Load from application.properties
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                props.load(input);
                logger.debug("Loaded configuration from application.properties");
            }
        } catch (IOException e) {
            logger.warn("Could not load application.properties", e);
        }

        return props;
    }

    /**
     * Get a configuration value with fallback to environment variables and system properties
     */
    public String getProperty(String key, String defaultValue) {
        // Priority: System Property > Environment Variable > Application Properties > Default
        String value = System.getProperty(key);
        if (value != null) {
            return value;
        }

        // Convert property key to environment variable format (e.g., crawler.redis.url -> CRAWLER_REDIS_URL)
        String envKey = key.toUpperCase().replace('.', '_');
        value = System.getenv(envKey);
        if (value != null) {
            return value;
        }

        value = properties.getProperty(key);
        if (value != null) {
            return value;
        }

        return defaultValue;
    }

    public int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for property {}: {}. Using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    public long getLongProperty(String key, long defaultValue) {
        String value = getProperty(key, String.valueOf(defaultValue));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for property {}: {}. Using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    public String getRedisUrl() {
        return getProperty("crawler.redis.url", "redis://localhost:6379");
    }

    public int getMaxConcurrentRequests() {
        return getIntProperty("crawler.max.concurrent.requests", 90);
    }

    public long getHttpTimeoutSeconds() {
        return getLongProperty("crawler.http.timeout.seconds", 5);
    }

    public String getUserAgent() {
        return getProperty("crawler.user.agent", "Monzo-Java-Crawler/1.0");
    }
}
