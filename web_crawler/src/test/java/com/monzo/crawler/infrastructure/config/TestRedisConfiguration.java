package com.monzo.crawler.infrastructure.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test configuration utility for Redis integration tests
 * Provides standardized Redis container setup and connection management
 */
public class TestRedisConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(TestRedisConfiguration.class);

    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final int REDIS_PORT = 6379;

    // Redis configuration for testing
    private static final int TEST_DATABASE = 1; // Use different DB for tests

    /**
     * Create a Redis container for testing with optimized settings
     */
    public static GenericContainer<?> createRedisContainer() {
        return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(REDIS_PORT)
                .withReuse(true)
                .withCommand("redis-server", "--appendonly", "no", "--save", "")
                .withLabel("test-type", "redis-integration");
    }

    /**
     * Create Redis client connected to the test container
     */
    public static RedisClient createRedisClient(GenericContainer<?> redisContainer) {
        String redisUrl = String.format("redis://%s:%d/%d",
                redisContainer.getHost(),
                redisContainer.getMappedPort(REDIS_PORT),
                TEST_DATABASE);

        logger.debug("Creating Redis client with URL: {}", redisUrl);
        return RedisClient.create(redisUrl);
    }

    /**
     * Create a complete test setup with client, connection and commands
     */
    public static TestRedisSetup createTestSetup(GenericContainer<?> redisContainer) {
        RedisClient client = createRedisClient(redisContainer);
        StatefulRedisConnection<String, String> connection = client.connect();
        RedisCommands<String, String> commands = connection.sync();

        // Ensure we're using the test database
        commands.select(TEST_DATABASE);

        // Clear any existing data in test database
        commands.flushdb();

        logger.info("Redis test setup created successfully");
        return new TestRedisSetup(client, connection, commands);
    }

    /**
     * Create test setup with custom database selection
     */
    public static TestRedisSetup createTestSetup(GenericContainer<?> redisContainer, int database) {
        RedisClient client = createRedisClient(redisContainer);
        StatefulRedisConnection<String, String> connection = client.connect();
        RedisCommands<String, String> commands = connection.sync();

        commands.select(database);
        commands.flushdb();

        return new TestRedisSetup(client, connection, commands);
    }

    /**
     * Helper class to hold Redis test components
     * Implements AutoCloseable for proper resource management
     */
    public static class TestRedisSetup implements AutoCloseable {
        private final RedisClient client;
        private final StatefulRedisConnection<String, String> connection;
        private final RedisCommands<String, String> commands;

        public TestRedisSetup(RedisClient client,
                              StatefulRedisConnection<String, String> connection,
                              RedisCommands<String, String> commands) {
            this.client = client;
            this.connection = connection;
            this.commands = commands;
        }

        public RedisClient getClient() {
            return client;
        }

        public StatefulRedisConnection<String, String> getConnection() {
            return connection;
        }

        public RedisCommands<String, String> getCommands() {
            return commands;
        }

        /**
         * Clean up test data without closing connections
         */
        public void cleanupTestData() {
            try {
                commands.flushdb();
                logger.debug("Test data cleaned up");
            } catch (Exception e) {
                logger.warn("Failed to cleanup test data", e);
            }
        }

        /**
         * Get connection info for debugging
         */
        public String getConnectionInfo() {
            try {
                return String.format("Redis connection - DB: %s, Ping: %s",
                        commands.lastsave(), commands.ping());
            } catch (Exception e) {
                return "Redis connection info unavailable: " + e.getMessage();
            }
        }

        @Override
        public void close() {
            try {
                if (connection != null && connection.isOpen()) {
                    connection.close();
                    logger.debug("Redis connection closed");
                }
            } catch (Exception e) {
                logger.warn("Error closing Redis connection", e);
            } finally {
                try {
                    if (client != null) {
                        client.shutdown();
                        logger.debug("Redis client shutdown");
                    }
                } catch (Exception e) {
                    logger.warn("Error shutting down Redis client", e);
                }
            }
        }
    }
}
