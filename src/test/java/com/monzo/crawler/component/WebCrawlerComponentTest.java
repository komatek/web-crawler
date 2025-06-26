package com.monzo.crawler.component;

import com.monzo.crawler.application.WebCrawlerUseCase;
import com.monzo.crawler.domain.port.out.*;
import com.monzo.crawler.infrastructure.*;
import com.monzo.crawler.infrastructure.config.TestRedisConfiguration;
import com.monzo.crawler.infrastructure.config.TestWireMockConfiguration;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebCrawlerComponentTest {

    private static final Logger logger = LoggerFactory.getLogger(WebCrawlerComponentTest.class);

    @Container
    static final GenericContainer<?> redisContainer = TestRedisConfiguration.createRedisContainer();

    private static WireMockServer wireMockServer;
    private static String mockServerUrl;

    private TestRedisConfiguration.TestRedisSetup redisSetup;
    private RedisCommands<String, String> redis;

    private WebCrawlerUseCase webCrawler;
    private TestCrawlObserver crawlObserver;

    @BeforeAll
    static void setUpClass() {
        // Start WireMock server using utility
        wireMockServer = TestWireMockConfiguration.createWireMockServer();
        mockServerUrl = TestWireMockConfiguration.getBaseUrl(wireMockServer);
    }

    @AfterAll
    static void tearDownClass() {
        TestWireMockConfiguration.stopServer(wireMockServer);
    }

    @BeforeEach
    void setUp() {
        // Set up Redis connection using your TestRedisConfiguration
        redisSetup = TestRedisConfiguration.createTestSetup(redisContainer);
        redis = redisSetup.getCommands();

        // Clear Redis data
        redis.flushall();

        // Set up WireMock stubs using utility
        TestWireMockConfiguration.setupWebsiteStubs(wireMockServer);

        // Create crawler components
        var visitedRepository = new RedisVisitedRepository(redis);
        var frontierQueue = new RedisFrontierQueue(redis);
        var pageFetcher = new HttpClientPageFetcher(Duration.ofSeconds(5));
        var linkExtractor = new JsoupLinkExtractor();
        crawlObserver = new TestCrawlObserver();

        webCrawler = new WebCrawlerUseCase(
                pageFetcher,
                linkExtractor,
                crawlObserver,
                frontierQueue,
                visitedRepository,
                "localhost",
                10
        );
    }

    @AfterEach
    void tearDown() {
        // Clean up Redis connection using your TestRedisConfiguration
        if (redisSetup != null) {
            redisSetup.close();
        }
        // Reset WireMock stubs
        wireMockServer.resetAll();
    }

    @Test
    @Order(1)
    @DisplayName("Happy Path: Crawl a simple website with multiple pages and links")
    void testHappyPathCrawling() throws InterruptedException {
        // Given: A starting URI pointing to our mock server
        URI startUri = URI.create(mockServerUrl + "/");

        // When: We start crawling
        logger.info("Starting crawl test for: {}", startUri);
        webCrawler.crawl(startUri);

        // Then: Verify the crawling results
        assertCrawlResults();
    }

    private void assertCrawlResults() throws InterruptedException {
        // Wait a bit for async operations to complete
        Thread.sleep(1000);

        // Verify that all expected pages were crawled
        Set<URI> crawledPages = crawlObserver.getCrawledPages();
        logger.info("Crawled pages: {}", crawledPages);

        // Expected pages (all pages that should be crawled)
        Set<String> expectedPaths = Set.of(
                "/",
                "/about",
                "/products",
                "/products/widget1",
                "/products/widget2",
                "/contact",
                "/blog",
                "/blog/post1",
                "/team"
        );

        // Convert crawled pages to paths for easier comparison
        Set<String> crawledPaths = crawledPages.stream()
                .map(URI::getPath)
                .collect(java.util.stream.Collectors.toSet());

        // Assertions
        assertThat(crawledPages).as("Should have crawled multiple pages").isNotEmpty();
        assertThat(crawledPaths).as("Should have crawled all expected paths")
                .containsAll(expectedPaths);

        // Verify no failed crawls for the pages we set up
        Set<URI> failedPages = crawlObserver.getFailedPages();
        logger.info("Failed pages: {}", failedPages);

        // Should have no failures for our valid pages
        long validPageFailures = failedPages.stream()
                .filter(uri -> expectedPaths.contains(uri.getPath()))
                .count();
        assertThat(validPageFailures).as("Should have no failures for valid pages").isZero();

        // Verify that links were extracted and processed
        assertThat(crawlObserver.getTotalLinksFound()).as("Should have found links").isGreaterThan(0);

        // Verify Redis state
        assertThat(redis.scard("visited-urls")).as("Should have marked pages as visited in Redis")
                .isGreaterThanOrEqualTo(expectedPaths.size());

        assertThat(redis.llen("frontier-queue")).as("Frontier queue should be empty after crawl")
                .isZero();

        logger.info("Component test completed successfully!");
        logger.info("Total pages crawled: {}", crawledPages.size());
        logger.info("Total links found: {}", crawlObserver.getTotalLinksFound());
        logger.info("Failed pages: {}", failedPages.size());
    }

    /**
     * Test implementation of CrawlObserver that tracks crawling results
     */
    private static class TestCrawlObserver implements CrawlObserver {
        private final Set<URI> crawledPages = ConcurrentHashMap.newKeySet();
        private final Set<URI> failedPages = ConcurrentHashMap.newKeySet();
        private int totalLinksFound = 0;

        @Override
        public void onPageCrawled(URI pageUri, Set<URI> links) {
            crawledPages.add(pageUri);
            synchronized (this) {
                totalLinksFound += links.size();
            }
            logger.debug("Crawled: {} with {} links", pageUri, links.size());
        }

        @Override
        public void onCrawlFailed(URI pageUri, String reason, Throwable error) {
            failedPages.add(pageUri);
            logger.debug("Failed to crawl: {} - {}", pageUri, reason);
        }

        public Set<URI> getCrawledPages() {
            return Set.copyOf(crawledPages);
        }

        public Set<URI> getFailedPages() {
            return Set.copyOf(failedPages);
        }

        public synchronized int getTotalLinksFound() {
            return totalLinksFound;
        }
    }
}
