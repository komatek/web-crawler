package com.monzo.crawler.config;

import com.monzo.crawler.application.WebCrawlerUseCase;
import com.monzo.crawler.domain.port.out.FrontierQueue;
import com.monzo.crawler.domain.port.out.VisitedRepository;
import com.monzo.crawler.infrastructure.*;
import com.monzo.crawler.infrastructure.config.TestRedisConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class WebCrawlerFactoryIntegrationTest {

    @Container
    static final GenericContainer<?> redis = TestRedisConfiguration.createRedisContainer();

    private TestRedisConfiguration.TestRedisSetup redisSetup;
    private FrontierQueue frontierQueue;
    private VisitedRepository visitedRepository;

    @BeforeEach
    void setUp() {
        redisSetup = TestRedisConfiguration.createTestSetup(redis);
        frontierQueue = new RedisFrontierQueue(redisSetup.getCommands());
        visitedRepository = new RedisVisitedRepository(redisSetup.getCommands());
    }

    @AfterEach
    void tearDown() {
        if (redisSetup != null) {
            redisSetup.close();
        }
    }

    @Test
    void shouldCreateCrawlerWithRealDependencies() {
        var pageFetcher = new HttpClientPageFetcher(Duration.ofSeconds(5));
        var linkExtractor = new JsoupLinkExtractor();
        var crawlObserver = new ConsoleCrawlObserver();

        var factory = new WebCrawlerFactory(
                pageFetcher,
                linkExtractor,
                crawlObserver,
                frontierQueue,
                visitedRepository,
                5
        );

        URI testUri = URI.create("https://example.com");

        // When
        WebCrawlerUseCase crawler = factory.createForUri(testUri);

        // Then
        assertThat(crawler).isNotNull();
    }

    @Test
    void shouldHandleMultipleDomainCreation() {
        // Given
        var factory = createFactoryWithRealDependencies();

        // When
        WebCrawlerUseCase crawler1 = factory.createForUri(URI.create("https://site1.com"));
        WebCrawlerUseCase crawler2 = factory.createForUri(URI.create("https://site2.com"));
        WebCrawlerUseCase crawler3 = factory.createForUri(URI.create("https://site1.com")); // Same domain as first

        // Then
        assertThat(crawler1).isNotNull();
        assertThat(crawler2).isNotNull();
        assertThat(crawler3).isNotNull();

        // All should be different instances
        assertThat(crawler1).isNotSameAs(crawler2);
        assertThat(crawler1).isNotSameAs(crawler3);
        assertThat(crawler2).isNotSameAs(crawler3);
    }

    private WebCrawlerFactory createFactoryWithRealDependencies() {
        return new WebCrawlerFactory(
                new HttpClientPageFetcher(Duration.ofSeconds(5)),
                new JsoupLinkExtractor(),
                new ConsoleCrawlObserver(),
                frontierQueue,
                visitedRepository,
                10
        );
    }
}
