package com.monzo.crawler;

import com.monzo.crawler.application.WebCrawler;
import com.monzo.crawler.config.WebCrawlerFactory;
import com.monzo.crawler.domain.port.out.*;
import com.monzo.crawler.infrastructure.*;
import com.monzo.crawler.infrastructure.config.ConfigurationLoader;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrawlerApplication {

    private static final Logger logger = LoggerFactory.getLogger(CrawlerApplication.class);

    public static void main(String[] args) {
        if (args.length == 0) {
            logger.error("Please provide a starting URL. Example: https://monzo.com");
            logger.error("Usage: java -jar <jar-file> <start-url>");
            return;
        }

        // Load configuration
        ConfigurationLoader config = new ConfigurationLoader();

        URI startUri;
        try {
            startUri = new URI(args[0]);
            if (startUri.getHost() == null) {
                throw new URISyntaxException(args[0], "Host cannot be null");
            }
        } catch (URISyntaxException e) {
            logger.error("Invalid start URI provided: {}", args[0], e);
            logger.error("Invalid start URI: {}", e.getMessage());
            return;
        }

        // Create Redis connection using configuration
        String redisUrl = config.getRedisUrl();
        logger.info("Connecting to Redis at: {}", redisUrl);

        RedisClient redisClient = null;
        StatefulRedisConnection<String, String> connection = null;

        try {
            redisClient = RedisClient.create(redisUrl);
            connection = redisClient.connect();
            RedisCommands<String, String> redis = connection.sync();

            // Test Redis connection
            try {
                redis.ping();
                logger.info("Successfully connected to Redis");
            } catch (Exception e) {
                logger.error("Failed to connect to Redis at {}", redisUrl, e);
                return;
            }

            // Create infrastructure components
            Duration httpTimeout = Duration.ofSeconds(config.getHttpTimeoutSeconds());
            int maxConcurrentRequests = config.getMaxConcurrentRequests();

            PageFetcher pageFetcher = new HttpClientPageFetcher(httpTimeout);
            LinkExtractor linkExtractor = new JsoupLinkExtractor();
            CrawlObserver crawlObserver = new ConsoleCrawlObserver();
            FrontierQueue frontierQueue = new RedisFrontierQueue(redis);
            VisitedRepository visitedRepository = new RedisVisitedRepository(redis);

            // Create factory with infrastructure dependencies
            WebCrawlerFactory factory = new WebCrawlerFactory(
                    pageFetcher,
                    linkExtractor,
                    crawlObserver,
                    frontierQueue,
                    visitedRepository,
                    maxConcurrentRequests
            );

            // Create crawler configured for the specific domain
            WebCrawler webCrawler = factory.createForUri(startUri);

            logger.info("Starting crawl at: {}", startUri);
            logger.info("Restricting to host: {}", startUri.getHost());
            logger.info("Max concurrent requests: {}", maxConcurrentRequests);
            logger.info("HTTP timeout: {} seconds", config.getHttpTimeoutSeconds());

            // Start crawling
            webCrawler.crawl(startUri);

            logger.info("Crawl finished.");

        } catch (Exception e) {
            logger.error("Failed to start crawler", e);
        } finally {
            // Clean up resources
            if (connection != null) {
                try {
                    connection.close();
                    logger.debug("Redis connection closed");
                } catch (Exception e) {
                    logger.warn("Error closing Redis connection", e);
                }
            }
            if (redisClient != null) {
                try {
                    redisClient.shutdown();
                    logger.debug("Redis client shutdown");
                } catch (Exception e) {
                    logger.warn("Error shutting down Redis client", e);
                }
            }
        }
    }
}
