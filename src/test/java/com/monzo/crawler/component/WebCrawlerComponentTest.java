package com.monzo.crawler.component;

import com.monzo.crawler.application.WebCrawlerUseCase;
import com.monzo.crawler.domain.port.out.*;
import com.monzo.crawler.domain.service.CrawlStateService;
import com.monzo.crawler.domain.service.PageProcessingService;
import com.monzo.crawler.domain.service.UriProcessingService;
import com.monzo.crawler.infrastructure.*;
import com.monzo.crawler.infrastructure.config.TestRedisConfiguration;
import com.monzo.crawler.infrastructure.config.TestWireMockConfiguration;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.concurrent.atomic.AtomicLong;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@Testcontainers
class WebCrawlerComponentTest {

    private static final Logger logger = LoggerFactory.getLogger(WebCrawlerComponentTest.class);
    private static final String ALLOWED_DOMAIN = "localhost";
    private static final int MAX_CONCURRENT_REQUESTS = 10;

    @Container
    static final GenericContainer<?> redisContainer = TestRedisConfiguration.createRedisContainer();

    private static WireMockServer wireMockServer;
    private static String mockServerUrl;
    private static RedisCommands<String, String> redis;

    private WebCrawlerUseCase webCrawler;
    private TestCrawlObserver crawlObserver;

    @BeforeAll
    static void setUpClass() {
        // Start WireMock server ONCE for all tests
        wireMockServer = TestWireMockConfiguration.createWireMockServer();
        mockServerUrl = TestWireMockConfiguration.getBaseUrl(wireMockServer);

        // Create shared Redis connection ONCE for all tests
        redis = TestRedisConfiguration.getSharedCommands(redisContainer);

        logger.info("Redis container started on port: {}", redisContainer.getMappedPort(6379));
        logger.info("WireMock server started once for all tests at: {}", mockServerUrl);
    }

    @AfterAll
    static void tearDownClass() {
        TestWireMockConfiguration.stopServer(wireMockServer);
        TestRedisConfiguration.closeSharedResources(); // Close shared Redis resources ONCE
        logger.info("WireMock server stopped and Redis resources cleaned up");
    }

    @BeforeEach
    void setUp() {
        // ONLY flush Redis data, DON'T close/recreate connection
        TestRedisConfiguration.cleanTestData(redis);

        // Reset WireMock stubs to clean state for each test
        wireMockServer.resetAll();

        // Create fresh observer for test isolation
        crawlObserver = new TestCrawlObserver();
        createWebCrawler();
    }

    // NO @AfterEach - we don't close anything per test!

    private void createWebCrawler() {
        // Infrastructure components using the SAME Redis connection
        var visitedRepository = new RedisVisitedRepository(redis);
        var frontierQueue = new RedisFrontierQueue(redis);
        var pageFetcher = new HttpClientPageFetcher(Duration.ofSeconds(5));
        var linkExtractor = new JsoupLinkExtractor();

        // Domain services
        var uriProcessingService = new UriProcessingService(ALLOWED_DOMAIN);
        var crawlStateService = new CrawlStateService(frontierQueue, visitedRepository, uriProcessingService);
        var pageProcessingService = new PageProcessingService(pageFetcher, linkExtractor, crawlObserver, crawlStateService);

        // Application service (use case)
        webCrawler = new WebCrawlerUseCase(
                pageProcessingService,
                crawlStateService,
                MAX_CONCURRENT_REQUESTS
        );
    }

    @Test
    void testHappyPathCrawling() {
        // Given
        TestWireMockConfiguration.setupWebsiteStubs(wireMockServer);
        URI startUri = URI.create(mockServerUrl + "/");

        // When
        webCrawler.crawl(startUri);

        // Then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Set<String> expectedPaths = Set.of("/", "/about", "/products", "/products/widget1",
                    "/products/widget2", "/contact", "/blog", "/blog/post1", "/team");
            assertCrawlResults(expectedPaths, 0);
        });
    }

    @Test
    void testHandle404Errors() {
        // Given
        setupStubsWithBrokenLinks();
        URI startUri = URI.create(mockServerUrl + "/");

        // When
        webCrawler.crawl(startUri);

        // Then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Set<URI> failedPages = crawlObserver.getFailedPages();
            assertThat(failedPages).isNotEmpty();

            // Verify specific 404 pages are in failed set
            boolean has404 = failedPages.stream()
                    .anyMatch(uri -> uri.getPath().equals("/broken-link"));
            assertThat(has404).isTrue();
        });
    }

    @Test
    void testConcurrentCrawling() {
        // Given
        setupStubsWithConcurrentDelays();
        URI startUri = URI.create(mockServerUrl + "/");

        // Track concurrent execution with virtual thread compatibility
        Set<String> activeThreadIdentifiers = ConcurrentHashMap.newKeySet();
        Set<Long> activeThreadIds = ConcurrentHashMap.newKeySet();
        AtomicInteger maxConcurrentRequests = new AtomicInteger(0);
        AtomicInteger currentConcurrentRequests = new AtomicInteger(0);
        AtomicLong uniqueThreadCounter = new AtomicLong(0);

        // Enhanced observer to track concurrency
        TestCrawlObserver concurrentObserver = new TestCrawlObserver() {
            @Override
            public void onPageCrawled(URI pageUri, Set<URI> links) {
                Thread currentThread = Thread.currentThread();
                String threadName = currentThread.getName();
                long threadId = currentThread.getId();

                // Check if virtual thread (Java version agnostic)
                boolean isVirtual = isVirtualThread(currentThread);

                String threadIdentifier = isVirtual ?
                        "VirtualThread-" + threadId :
                        (threadName != null && !threadName.trim().isEmpty() ? threadName : "PlatformThread-" + threadId);

                activeThreadIdentifiers.add(threadIdentifier);
                activeThreadIds.add(threadId);

                // Track concurrent request count
                int current = currentConcurrentRequests.incrementAndGet();
                maxConcurrentRequests.updateAndGet(max -> Math.max(max, current));

                long sequenceNum = uniqueThreadCounter.incrementAndGet();

                logger.info("Page {} crawled on thread: {} (ID: {}, Virtual: {}, Seq: {})",
                        pageUri, threadIdentifier, threadId, isVirtual, sequenceNum);

                super.onPageCrawled(pageUri, links);

                try {
                    Thread.sleep(100); // Help detect concurrency
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    currentConcurrentRequests.decrementAndGet();
                }
            }

            private boolean isVirtualThread(Thread thread) {
                try {
                    return (Boolean) thread.getClass().getMethod("isVirtual").invoke(thread);
                } catch (Exception e) {
                    String className = thread.getClass().getSimpleName();
                    String threadName = thread.getName();
                    return className.contains("Virtual") ||
                            (threadName != null && threadName.contains("Virtual")) ||
                            thread.getClass().getName().contains("VirtualThread");
                }
            }
        };

        // Replace the observer and recreate crawler
        crawlObserver = concurrentObserver;
        createWebCrawler();

        // When
        long startTime = System.currentTimeMillis();
        webCrawler.crawl(startUri);
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // Then
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Set<URI> crawledPages = crawlObserver.getCrawledPages();
            assertThat(crawledPages).hasSizeGreaterThan(3);

            Set<String> crawledPaths = crawledPages.stream()
                    .map(URI::getPath)
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(crawledPaths).contains("/", "/slow1", "/slow2", "/slow3");
        });

        // Debug logging
        logger.info("=== CONCURRENCY TEST RESULTS ===");
        logger.info("Total crawling time: {} ms", totalTime);
        logger.info("Active thread identifiers: {}", activeThreadIdentifiers);
        logger.info("Unique thread IDs count: {}", activeThreadIds.size());
        logger.info("Max concurrent requests: {}", maxConcurrentRequests.get());
        logger.info("=================================");

        // Verify concurrency
        assertThat(maxConcurrentRequests.get())
                .as("Should have multiple concurrent requests")
                .isGreaterThan(1);

        assertThat(activeThreadIds.size())
                .as("Should use multiple thread IDs")
                .isGreaterThan(1);

        assertThat(totalTime)
                .as("Concurrent crawling should be faster than sequential")
                .isLessThan(1200);

        logger.info("✅ CONCURRENCY VERIFIED: Multiple threads, high concurrency, fast execution");
    }

    // ... other test methods (keeping them short for space)

    private void setupStubsWithConcurrentDelays() {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><body>
                                    <a href="/slow1">Slow Page 1</a>
                                    <a href="/slow2">Slow Page 2</a>
                                    <a href="/slow3">Slow Page 3</a>
                                </body></html>
                                """)));

        wireMockServer.stubFor(get(urlEqualTo("/slow1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Slow page 1 content</body></html>")
                        .withFixedDelay(500)));

        wireMockServer.stubFor(get(urlEqualTo("/slow2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Slow page 2 content</body></html>")
                        .withFixedDelay(500)));

        wireMockServer.stubFor(get(urlEqualTo("/slow3"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Slow page 3 content</body></html>")
                        .withFixedDelay(500)));
    }

    // Include your other setup methods here...
    private void setupStubsWithBrokenLinks() {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><body>
                                    <a href="/working-page">Working</a>
                                    <a href="/broken-link">Broken</a>
                                </body></html>
                                """)));

        wireMockServer.stubFor(get(urlEqualTo("/working-page"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Working page</body></html>")));

        wireMockServer.stubFor(get(urlEqualTo("/broken-link"))
                .willReturn(aResponse().withStatus(404)));
    }

    private void assertCrawlResults(Set<String> expectedPaths, int expectedFailures) {
        Set<URI> crawledPages = crawlObserver.getCrawledPages();
        logger.info("Crawled pages: {}", crawledPages);

        Set<String> crawledPaths = crawledPages.stream()
                .map(URI::getPath)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(crawledPages).as("Should have crawled multiple pages").isNotEmpty();
        assertThat(crawledPaths).as("Should have crawled all expected paths")
                .containsAll(expectedPaths);

        Set<URI> failedPages = crawlObserver.getFailedPages();
        logger.info("Failed pages: {}", failedPages);

        if (expectedFailures > 0) {
            assertThat(failedPages).hasSizeGreaterThanOrEqualTo(expectedFailures);
        }

        assertThat(crawlObserver.getTotalLinksFound()).as("Should have found links").isGreaterThan(0);

        // Verify Redis state using the SAME connection
        assertThat(redis.scard("visited-urls")).as("Should have marked pages as visited in Redis")
                .isGreaterThanOrEqualTo(expectedPaths.size());

        assertThat(redis.llen("frontier-queue")).as("Frontier queue should be empty after crawl")
                .isZero();

        logger.info("Test completed successfully!");
        logger.info("Total pages crawled: {}", crawledPages.size());
        logger.info("Total links found: {}", crawlObserver.getTotalLinksFound());
        logger.info("Failed pages: {}", failedPages.size());
    }

    /**
     * Test implementation of CrawlObserver
     */
    private static class TestCrawlObserver implements CrawlObserver {
        private final Set<URI> crawledPages = ConcurrentHashMap.newKeySet();
        private final Set<URI> failedPages = ConcurrentHashMap.newKeySet();
        private final Map<URI, Long> crawlTimestamps = new ConcurrentHashMap<>();
        private int totalLinksFound = 0;

        @Override
        public void onPageCrawled(URI pageUri, Set<URI> links) {
            crawledPages.add(pageUri);
            crawlTimestamps.put(pageUri, System.currentTimeMillis());
            synchronized (this) {
                totalLinksFound += links.size();
            }
            logger.debug("Crawled: {} with {} links on thread: {}",
                    pageUri, links.size(), Thread.currentThread().getName());
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

        public boolean hasOverlappingCrawls(long windowMs) {
            List<Long> timestamps = new ArrayList<>(crawlTimestamps.values());
            if (timestamps.size() < 2) {
                return false;
            }

            timestamps.sort(Long::compareTo);

            for (int i = 0; i < timestamps.size() - 1; i++) {
                long timeDiff = timestamps.get(i + 1) - timestamps.get(i);
                if (timeDiff < windowMs) {
                    return true;
                }
            }
            return false;
        }
    }
}
