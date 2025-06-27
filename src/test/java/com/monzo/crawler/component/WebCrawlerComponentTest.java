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

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WebCrawlerComponentTest {

    private static final Logger logger = LoggerFactory.getLogger(WebCrawlerComponentTest.class);
    private static final String ALLOWED_DOMAIN = "localhost";
    private static final int MAX_CONCURRENT_REQUESTS = 10;

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
        // Start WireMock server ONCE for all tests
        wireMockServer = TestWireMockConfiguration.createWireMockServer();
        mockServerUrl = TestWireMockConfiguration.getBaseUrl(wireMockServer);

        // Redis container is already started by @Container annotation
        logger.info("Redis container started on port: {}", redisContainer.getMappedPort(6379));
        logger.info("WireMock server started once for all tests at: {}", mockServerUrl);
    }

    @AfterAll
    static void tearDownClass() {
        TestWireMockConfiguration.stopServer(wireMockServer);
        logger.info("WireMock server stopped after all tests completed");
    }

    @BeforeEach
    void setUp() {
        // Create fresh Redis connection for each test
        redisSetup = TestRedisConfiguration.createTestSetup(redisContainer);
        redis = redisSetup.getCommands();

        // Clear all Redis data to ensure test isolation
        redis.flushall();

        // Reset WireMock stubs to clean state for each test
        wireMockServer.resetAll();

        // Create fresh observer for test isolation
        crawlObserver = new TestCrawlObserver();
        createWebCrawler();
    }

    @AfterEach
    void tearDown() {
        // Clean up Redis connection
        if (redisSetup != null) {
            redisSetup.close();
        }
    }

    private void createWebCrawler() {
        // Infrastructure components
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
    void testHandleServerErrors() {
        // Given
        setupStubsWithServerErrors();
        URI startUri = URI.create(mockServerUrl + "/");

        // When
        webCrawler.crawl(startUri);

        // Then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Set<URI> failedPages = crawlObserver.getFailedPages();
            assertThat(failedPages).isNotEmpty();

            boolean hasServerError = failedPages.stream()
                    .anyMatch(uri -> uri.getPath().equals("/server-error"));
            assertThat(hasServerError).isTrue();
        });
    }

    @Test
    void testDomainRestrictions() {
        // Given
        setupStubsWithExternalLinks();
        URI startUri = URI.create(mockServerUrl + "/");

        // When
        webCrawler.crawl(startUri);

        // Then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Set<URI> crawledPages = crawlObserver.getCrawledPages();

            // Verify no external domains were crawled
            boolean hasExternalDomain = crawledPages.stream()
                    .anyMatch(uri -> !uri.getHost().equals(ALLOWED_DOMAIN));
            assertThat(hasExternalDomain).isFalse();

            // Should still crawl internal pages
            assertThat(crawledPages).isNotEmpty();
        });
    }

    @Test
    void testCircularReferences() {
        // Given
        setupStubsWithCircularReferences();
        URI startUri = URI.create(mockServerUrl + "/");

        // When
        webCrawler.crawl(startUri);

        // Then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Set<URI> crawledPages = crawlObserver.getCrawledPages();

            // Should crawl each page only once despite circular references
            Set<String> crawledPaths = crawledPages.stream()
                    .map(URI::getPath)
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(crawledPaths).containsExactlyInAnyOrder("/", "/page-a", "/page-b");
            assertThat(crawledPages).hasSize(3); // Each page visited only once
        });
    }

    @Test
    void testSlowResponses() {
        // Given
        setupStubsWithSlowResponses();
        URI startUri = URI.create(mockServerUrl + "/");

        // When
        webCrawler.crawl(startUri);

        // Then
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Set<URI> crawledPages = crawlObserver.getCrawledPages();
            assertThat(crawledPages).isNotEmpty();

            // Verify slow page was eventually crawled
            boolean hasSlowPage = crawledPages.stream()
                    .anyMatch(uri -> uri.getPath().equals("/slow-page"));
            assertThat(hasSlowPage).isTrue();
        });
    }

    @Test
    void testIgnoreNonHtmlContent() {
        // Given
        setupStubsWithMixedContentTypes();
        URI startUri = URI.create(mockServerUrl + "/");

        // When
        webCrawler.crawl(startUri);

        // Then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Set<URI> crawledPages = crawlObserver.getCrawledPages();

            // Should crawl HTML pages but not JSON/images
            Set<String> crawledPaths = crawledPages.stream()
                    .map(URI::getPath)
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(crawledPaths).contains("/", "/html-page");
            assertThat(crawledPaths).doesNotContain("/api/data.json", "/image.jpg");
        });
    }

    @Test
    void testEmptyPages() {
        // Given
        setupStubsWithEmptyPages();
        URI startUri = URI.create(mockServerUrl + "/");

        // When
        webCrawler.crawl(startUri);

        // Then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Set<URI> crawledPages = crawlObserver.getCrawledPages();
            assertThat(crawledPages).isNotEmpty();

            // Verify empty page was crawled successfully
            boolean hasEmptyPage = crawledPages.stream()
                    .anyMatch(uri -> uri.getPath().equals("/empty"));
            assertThat(hasEmptyPage).isTrue();
        });
    }

    @Test
    void testMalformedHtml() {
        // Given
        setupStubsWithMalformedHtml();
        URI startUri = URI.create(mockServerUrl + "/");

        // When
        webCrawler.crawl(startUri);

        // Then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Set<URI> crawledPages = crawlObserver.getCrawledPages();
            assertThat(crawledPages).isNotEmpty();

            // Should still extract links from malformed HTML
            boolean hasMalformedPage = crawledPages.stream()
                    .anyMatch(uri -> uri.getPath().equals("/malformed"));
            assertThat(hasMalformedPage).isTrue();
        });
    }

    @Test
    void testConcurrentCrawling() {
        // Given
        setupStubsWithDeepHierarchy();
        URI startUri = URI.create(mockServerUrl + "/");

        // When
        long startTime = System.currentTimeMillis();
        webCrawler.crawl(startUri);
        long endTime = System.currentTimeMillis();

        // Then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Set<URI> crawledPages = crawlObserver.getCrawledPages();
            assertThat(crawledPages).hasSizeGreaterThan(1);

            // Verify deep hierarchy was crawled
            Set<String> crawledPaths = crawledPages.stream()
                    .map(URI::getPath)
                    .collect(java.util.stream.Collectors.toSet());

            assertThat(crawledPaths).contains("/", "/level1", "/level1/level2", "/level1/level2/level3");
        });

        logger.info("Crawling took {} ms", endTime - startTime);
    }

    @Test
    void testUriNormalization() {
        // Given
        setupStubsWithUnnormalizedUris();
        URI startUri = URI.create(mockServerUrl + "/");

        // When
        webCrawler.crawl(startUri);

        // Then
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Set<URI> crawledPages = crawlObserver.getCrawledPages();

            // Should normalize URIs and not crawl duplicates
            Set<String> crawledPaths = crawledPages.stream()
                    .map(URI::getPath)
                    .collect(java.util.stream.Collectors.toSet());

            // Should contain normalized paths
            assertThat(crawledPaths).contains("/", "/page");
            // Should not contain both "/page" and "/page/" as separate entries
            long pagePathCount = crawledPages.stream()
                    .map(URI::getPath)
                    .filter(path -> path.equals("/page"))
                    .count();
            assertThat(pagePathCount).isEqualTo(1);
        });
    }

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

    private void setupStubsWithServerErrors() {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><body>
                                    <a href="/working-page">Working</a>
                                    <a href="/server-error">Server Error</a>
                                </body></html>
                                """)));

        wireMockServer.stubFor(get(urlEqualTo("/working-page"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Working page</body></html>")));

        wireMockServer.stubFor(get(urlEqualTo("/server-error"))
                .willReturn(aResponse().withStatus(500)));
    }

    private void setupStubsWithExternalLinks() {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><body>
                                    <a href="/internal-page">Internal</a>
                                    <a href="https://external.com/page">External</a>
                                    <a href="http://another-domain.com/page">Another Domain</a>
                                </body></html>
                                """)));

        wireMockServer.stubFor(get(urlEqualTo("/internal-page"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Internal page</body></html>")));
    }

    private void setupStubsWithCircularReferences() {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><body>
                                    <a href="/page-a">Page A</a>
                                    <a href="/page-b">Page B</a>
                                </body></html>
                                """)));

        wireMockServer.stubFor(get(urlEqualTo("/page-a"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><body>
                                    <a href="/">Home</a>
                                    <a href="/page-b">Page B</a>
                                </body></html>
                                """)));

        wireMockServer.stubFor(get(urlEqualTo("/page-b"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><body>
                                    <a href="/">Home</a>
                                    <a href="/page-a">Page A</a>
                                </body></html>
                                """)));
    }

    private void setupStubsWithSlowResponses() {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><body>
                                    <a href="/fast-page">Fast</a>
                                    <a href="/slow-page">Slow</a>
                                </body></html>
                                """)));

        wireMockServer.stubFor(get(urlEqualTo("/fast-page"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Fast page</body></html>")));

        wireMockServer.stubFor(get(urlEqualTo("/slow-page"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Slow page</body></html>")
                        .withFixedDelay(2000))); // 2 second delay
    }

    private void setupStubsWithMixedContentTypes() {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><body>
                                    <a href="/html-page">HTML Page</a>
                                    <a href="/api/data.json">JSON API</a>
                                    <a href="/image.jpg">Image</a>
                                </body></html>
                                """)));

        wireMockServer.stubFor(get(urlEqualTo("/html-page"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>HTML content</body></html>")));

        wireMockServer.stubFor(get(urlEqualTo("/api/data.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\": \"value\"}")));

        wireMockServer.stubFor(get(urlEqualTo("/image.jpg"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "image/jpeg")
                        .withBody("fake-image-data")));
    }

    private void setupStubsWithEmptyPages() {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><body>
                                    <a href="/empty">Empty Page</a>
                                    <a href="/normal">Normal Page</a>
                                </body></html>
                                """)));

        wireMockServer.stubFor(get(urlEqualTo("/empty"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody(""))); // Empty body

        wireMockServer.stubFor(get(urlEqualTo("/normal"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Normal content</body></html>")));
    }

    private void setupStubsWithMalformedHtml() {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><body>
                                    <a href="/malformed">Malformed Page</a>
                                    <a href="/valid">Valid Page</a>
                                </body></html>
                                """)));

        wireMockServer.stubFor(get(urlEqualTo("/malformed"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><body>
                                    <h1>Unclosed header
                                    <a href="/valid">Valid link in malformed HTML
                                    <div><span>Nested unclosed tags
                                </body>
                                """)));

        wireMockServer.stubFor(get(urlEqualTo("/valid"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><h1>Valid HTML</h1></body></html>")));
    }

    private void setupStubsWithDeepHierarchy() {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><a href=\"/level1\">Level 1</a></body></html>")));

        wireMockServer.stubFor(get(urlEqualTo("/level1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><a href=\"/level1/level2\">Level 2</a></body></html>")));

        wireMockServer.stubFor(get(urlEqualTo("/level1/level2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body><a href=\"/level1/level2/level3\">Level 3</a></body></html>")));

        wireMockServer.stubFor(get(urlEqualTo("/level1/level2/level3"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Deep level content</body></html>")));
    }

    private void setupStubsWithUnnormalizedUris() {
        wireMockServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("""
                                <html><body>
                                    <a href="/page">Page without slash</a>
                                    <a href="/page/">Page with slash</a>
                                    <a href="/page#fragment">Page with fragment</a>
                                </body></html>
                                """)));

        wireMockServer.stubFor(get(urlEqualTo("/page"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>Page content</body></html>")));

        // WireMock will also match /page/ to /page due to URI normalization
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

        // Verify Redis state
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
