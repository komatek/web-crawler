package com.monzo.crawler.infrastructure.config;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Enhanced test configuration utility for Redis integration tests
 * Provides connection reuse and efficient data cleanup
 */
public class TestRedisConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(TestRedisConfiguration.class);

    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final int REDIS_PORT = 6379;
    private static final int TEST_DATABASE = 1;

    // Singleton Redis client for reuse across tests in same class
    private static volatile RedisClient sharedClient;
    private static volatile StatefulRedisConnection<String, String> sharedConnection;
    private static volatile RedisCommands<String, String> sharedCommands;
    private static volatile GenericContainer<?> lastContainer;

    /**
     * Create a Redis container for testing with optimized settings
     */
    public static GenericContainer<?> createRedisContainer() {
        return new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(REDIS_PORT)
                .withReuse(true) // Reuse container across test runs
                .withCommand("redis-server", "--appendonly", "no", "--save", "")
                .withLabel("test-type", "redis-integration");
    }

    /**
     * Get or create shared Redis commands for the container
     * Reuses connection within the same test class execution
     */
    public static synchronized RedisCommands<String, String> getSharedCommands(GenericContainer<?> redisContainer) {
        if (sharedCommands == null || lastContainer != redisContainer || !isConnectionHealthy()) {
            closeSharedResources();
            createSharedConnection(redisContainer);
        }
        return sharedCommands;
    }

    /**
     * Create a complete test setup with shared connection
     */
    public static TestRedisSetup createTestSetup(GenericContainer<?> redisContainer) {
        RedisCommands<String, String> commands = getSharedCommands(redisContainer);

        // Clean data instead of creating new connection
        cleanTestData(commands);

        return new TestRedisSetup(sharedClient, sharedConnection, commands, false); // false = don't close on cleanup
    }

    /**
     * Create test setup with dedicated connection (for tests that need isolation)
     */
    public static TestRedisSetup createDedicatedTestSetup(GenericContainer<?> redisContainer) {
        RedisClient client = createRedisClient(redisContainer);
        StatefulRedisConnection<String, String> connection = client.connect();
        RedisCommands<String, String> commands = connection.sync();

        commands.select(TEST_DATABASE);
        commands.flushdb();

        return new TestRedisSetup(client, connection, commands, true); // true = close on cleanup
    }

    /**
     * Clean all test data efficiently
     */
    public static void cleanTestData(RedisCommands<String, String> commands) {
        try {
            commands.select(TEST_DATABASE);
            commands.flushdb();
            logger.debug("Test data cleaned efficiently");
        } catch (Exception e) {
            logger.warn("Failed to clean test data", e);
        }
    }

    /**
     * Clean specific keys only (more efficient for small datasets)
     */
    public static void cleanSpecificKeys(RedisCommands<String, String> commands, String... keyPatterns) {
        try {
            commands.select(TEST_DATABASE);
            for (String pattern : keyPatterns) {
                var keys = commands.keys(pattern);
                if (!keys.isEmpty()) {
                    commands.del(keys.toArray(new String[0]));
                }
            }
            logger.debug("Cleaned specific keys: {}", String.join(", ", keyPatterns));
        } catch (Exception e) {
            logger.warn("Failed to clean specific keys", e);
        }
    }

    private static void createSharedConnection(GenericContainer<?> redisContainer) {
        try {
            sharedClient = createRedisClient(redisContainer);
            sharedConnection = sharedClient.connect();
            sharedCommands = sharedConnection.sync();
            sharedCommands.select(TEST_DATABASE);
            lastContainer = redisContainer;
            logger.info("Shared Redis connection created successfully");
        } catch (Exception e) {
            logger.error("Failed to create shared Redis connection", e);
            throw new RuntimeException("Failed to create shared Redis connection", e);
        }
    }

    private static boolean isConnectionHealthy() {
        try {
            return sharedConnection != null &&
                    sharedConnection.isOpen() &&
                    "PONG".equals(sharedCommands.ping());
        } catch (Exception e) {
            logger.debug("Connection health check failed", e);
            return false;
        }
    }

    private static RedisClient createRedisClient(GenericContainer<?> redisContainer) {
        String redisUrl = String.format("redis://%s:%d/%d",
                redisContainer.getHost(),
                redisContainer.getMappedPort(REDIS_PORT),
                TEST_DATABASE);

        logger.debug("Creating Redis client with URL: {}", redisUrl);
        return RedisClient.create(redisUrl);
    }

    /**
     * Close shared resources (call this in @AfterAll if needed)
     */
    public static synchronized void closeSharedResources() {
        try {
            if (sharedConnection != null && sharedConnection.isOpen()) {
                sharedConnection.close();
                logger.debug("Shared Redis connection closed");
            }
        } catch (Exception e) {
            logger.warn("Error closing shared Redis connection", e);
        } finally {
            try {
                if (sharedClient != null) {
                    sharedClient.shutdown();
                    logger.debug("Shared Redis client shutdown");
                }
            } catch (Exception e) {
                logger.warn("Error shutting down shared Redis client", e);
            } finally {
                sharedClient = null;
                sharedConnection = null;
                sharedCommands = null;
                lastContainer = null;
            }
        }
    }

    /**
     * Enhanced helper class with connection reuse support
     */
    public static class TestRedisSetup implements AutoCloseable {
        private final RedisClient client;
        private final StatefulRedisConnection<String, String> connection;
        private final RedisCommands<String, String> commands;
        private final boolean shouldCloseOnCleanup;

        public TestRedisSetup(RedisClient client,
                              StatefulRedisConnection<String, String> connection,
                              RedisCommands<String, String> commands,
                              boolean shouldCloseOnCleanup) {
            this.client = client;
            this.connection = connection;
            this.commands = commands;
            this.shouldCloseOnCleanup = shouldCloseOnCleanup;
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
         * Clean up test data efficiently without closing connections
         */
        public void cleanupTestData() {
            TestRedisConfiguration.cleanTestData(commands);
        }

        /**
         * Clean specific test keys
         */
        public void cleanupSpecificKeys(String... keyPatterns) {
            TestRedisConfiguration.cleanSpecificKeys(commands, keyPatterns);
        }

        /**
         * Get connection info for debugging
         */
        public String getConnectionInfo() {
            try {
                return String.format("Redis connection - DB: %d, Ping: %s, Open: %s",
                        TEST_DATABASE, commands.ping(), connection.isOpen());
            } catch (Exception e) {
                return "Redis connection info unavailable: " + e.getMessage();
            }
        }

        @Override
        public void close() {
            if (shouldCloseOnCleanup) {
                // Close dedicated connection
                try {
                    if (connection != null && connection.isOpen()) {
                        connection.close();
                        logger.debug("Dedicated Redis connection closed");
                    }
                } catch (Exception e) {
                    logger.warn("Error closing dedicated Redis connection", e);
                } finally {
                    try {
                        if (client != null) {
                            client.shutdown();
                            logger.debug("Dedicated Redis client shutdown");
                        }
                    } catch (Exception e) {
                        logger.warn("Error shutting down dedicated Redis client", e);
                    }
                }
            } else {
                // For shared connections, just clean data
                cleanupTestData();
            }
        }
    }

    /**
     * Utility for creating optimized test base class
     */
    public static abstract class OptimizedRedisTestBase {
        protected static GenericContainer<?> redisContainer;
        protected TestRedisSetup redisSetup;
        protected RedisCommands<String, String> redis;

        protected static void setUpRedisContainer() {
            if (redisContainer == null) {
                redisContainer = createRedisContainer();
                redisContainer.start();
            }
        }

        protected static void tearDownRedisContainer() {
            closeSharedResources();
            if (redisContainer != null) {
                redisContainer.stop();
            }
        }

        protected void setUpRedis() {
            redisSetup = createTestSetup(redisContainer);
            redis = redisSetup.getCommands();
        }

        protected void tearDownRedis() {
            if (redisSetup != null) {
                redisSetup.close(); // This will just clean data, not close shared connection
            }
        }

        protected void cleanupSpecificTestData(String... keyPatterns) {
            if (redisSetup != null) {
                redisSetup.cleanupSpecificKeys(keyPatterns);
            }
        }
    }
}
