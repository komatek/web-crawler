package com.monzo.crawler.config;

import com.monzo.crawler.application.WebCrawlerUseCase;
import com.monzo.crawler.domain.port.out.*;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WebCrawlerFactoryTest {

    private final PageFetcher mockPageFetcher = mock(PageFetcher.class);
    private final LinkExtractor mockLinkExtractor = mock(LinkExtractor.class);
    private final CrawlObserver mockCrawlObserver = mock(CrawlObserver.class);
    private final FrontierQueue mockFrontierQueue = mock(FrontierQueue.class);
    private final VisitedRepository mockVisitedRepository = mock(VisitedRepository.class);

    private final WebCrawlerFactory factory = new WebCrawlerFactory(
            mockPageFetcher,
            mockLinkExtractor,
            mockCrawlObserver,
            mockFrontierQueue,
            mockVisitedRepository,
            10 // Arbitrary max concurrent requests
    );

    // Constructor tests
    @Test
    void shouldCreateFactoryWithValidDependencies() {
        // Given
        PageFetcher pageFetcher = mock(PageFetcher.class);
        LinkExtractor linkExtractor = mock(LinkExtractor.class);
        CrawlObserver crawlObserver = mock(CrawlObserver.class);
        FrontierQueue frontierQueue = mock(FrontierQueue.class);
        VisitedRepository visitedRepository = mock(VisitedRepository.class);
        int maxConcurrentRequests = 5;

        // When
        WebCrawlerFactory factory = new WebCrawlerFactory(
                pageFetcher,
                linkExtractor,
                crawlObserver,
                frontierQueue,
                visitedRepository,
                maxConcurrentRequests
        );

        // Then
        assertThat(factory).isNotNull();
    }

    @Test
    void shouldRejectNullPageFetcher() {
        // When & Then
        assertThatThrownBy(() -> new WebCrawlerFactory(
                null, mockLinkExtractor, mockCrawlObserver,
                mockFrontierQueue, mockVisitedRepository, 10))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullLinkExtractor() {
        // When & Then
        assertThatThrownBy(() -> new WebCrawlerFactory(
                mockPageFetcher, null, mockCrawlObserver,
                mockFrontierQueue, mockVisitedRepository, 10))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullCrawlObserver() {
        // When & Then
        assertThatThrownBy(() -> new WebCrawlerFactory(
                mockPageFetcher, mockLinkExtractor, null,
                mockFrontierQueue, mockVisitedRepository, 10))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullFrontierQueue() {
        // When & Then
        assertThatThrownBy(() -> new WebCrawlerFactory(
                mockPageFetcher, mockLinkExtractor, mockCrawlObserver,
                null, mockVisitedRepository, 10))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullVisitedRepository() {
        // When & Then
        assertThatThrownBy(() -> new WebCrawlerFactory(
                mockPageFetcher, mockLinkExtractor, mockCrawlObserver,
                mockFrontierQueue, null, 10))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectAllNullDependencies() {
        // When & Then
        assertThatThrownBy(() -> new WebCrawlerFactory(
                null, null, null, null, null, 10))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldCreateWebCrawlerForValidUri() {
        // Given
        URI validUri = URI.create("https://example.com/page");

        // When
        WebCrawlerUseCase result = factory.createForUri(validUri);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(WebCrawlerUseCase.class);
    }

    @Test
    void shouldHandleDifferentDomains() {
        // Given
        URI domain1 = URI.create("https://monzo.com");
        URI domain2 = URI.create("https://example.org");

        // When
        WebCrawlerUseCase crawler1 = factory.createForUri(domain1);
        WebCrawlerUseCase crawler2 = factory.createForUri(domain2);

        // Then
        assertThat(crawler1).isNotNull();
        assertThat(crawler2).isNotNull();
        assertThat(crawler1).isNotSameAs(crawler2); // Different instances
    }

    @Test
    void shouldHandleHttpAndHttps() {
        // Given
        URI httpUri = URI.create("http://example.com");
        URI httpsUri = URI.create("https://example.com");

        // When
        WebCrawlerUseCase httpCrawler = factory.createForUri(httpUri);
        WebCrawlerUseCase httpsCrawler = factory.createForUri(httpsUri);

        // Then
        assertThat(httpCrawler).isNotNull();
        assertThat(httpsCrawler).isNotNull();
    }

    @Test
    void shouldHandleUriWithPort() {
        // Given
        URI uriWithPort = URI.create("https://example.com:8080/page");

        // When
        WebCrawlerUseCase result = factory.createForUri(uriWithPort);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void shouldHandleUriWithPath() {
        // Given
        URI uriWithPath = URI.create("https://example.com/some/deep/path");

        // When
        WebCrawlerUseCase result = factory.createForUri(uriWithPath);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void shouldHandleUriWithQueryParameters() {
        // Given
        URI uriWithQuery = URI.create("https://example.com/page?param=value&other=123");

        // When
        WebCrawlerUseCase result = factory.createForUri(uriWithQuery);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void shouldRejectUriWithNullHost() {
        // Given
        URI invalidUri = URI.create("file:///local/path");

        // When & Then
        assertThatThrownBy(() -> factory.createForUri(invalidUri))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid URI - no host found");
    }

    @Test
    void shouldRejectUriWithEmptyHost() {
        // Given - Create a URI that somehow has an empty host
        URI invalidUri = URI.create("https:///path"); // Empty host

        // When & Then
        assertThatThrownBy(() -> factory.createForUri(invalidUri))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid URI - no host found");
    }

    @Test
    void shouldRejectNullUri() {
        // When & Then
        assertThatThrownBy(() -> factory.createForUri(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleSpecialCharactersInDomain() {
        // Given
        URI unicodeUri = URI.create("https://xn--bcher-kva.example.com");

        // When
        WebCrawlerUseCase result = factory.createForUri(unicodeUri);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void shouldCreateIndependentCrawlerInstances() {
        // Given
        URI sameUri = URI.create("https://example.com");

        // When
        WebCrawlerUseCase crawler1 = factory.createForUri(sameUri);
        WebCrawlerUseCase crawler2 = factory.createForUri(sameUri);

        // Then
        assertThat(crawler1).isNotNull();
        assertThat(crawler2).isNotNull();
        assertThat(crawler1).isNotSameAs(crawler2); // Should be different instances
    }

    @Test
    void shouldHandleUriWithFragment() {
        // Given
        URI uriWithFragment = URI.create("https://example.com/page#section1");

        // When
        WebCrawlerUseCase result = factory.createForUri(uriWithFragment);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void shouldHandleUriWithUserInfo() {
        // Given
        URI uriWithUserInfo = URI.create("https://user:pass@example.com/page");

        // When
        WebCrawlerUseCase result = factory.createForUri(uriWithUserInfo);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void shouldHandleMinimalUri() {
        // Given
        URI minimalUri = URI.create("https://example.com");

        // When
        WebCrawlerUseCase result = factory.createForUri(minimalUri);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void shouldHandleUriWithDefaultPorts() {
        // Given
        URI httpUriWithDefaultPort = URI.create("http://example.com:80");
        URI httpsUriWithDefaultPort = URI.create("https://example.com:443");

        // When
        WebCrawlerUseCase httpResult = factory.createForUri(httpUriWithDefaultPort);
        WebCrawlerUseCase httpsResult = factory.createForUri(httpsUriWithDefaultPort);

        // Then
        assertThat(httpResult).isNotNull();
        assertThat(httpsResult).isNotNull();
    }

    @Test
    void shouldHandleLocalhostUri() {
        // Given
        URI localhostUri = URI.create("http://localhost:3000/test");

        // When
        WebCrawlerUseCase result = factory.createForUri(localhostUri);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void shouldHandleIpAddressUri() {
        // Given
        URI ipUri = URI.create("http://192.168.1.1:8080/api");

        // When
        WebCrawlerUseCase result = factory.createForUri(ipUri);

        // Then
        assertThat(result).isNotNull();
    }

    @Test
    void shouldRejectMalformedUri() {
        // Given
        URI malformedUri = URI.create("not-a-valid-uri");

        // When & Then
        assertThatThrownBy(() -> factory.createForUri(malformedUri))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid URI - no host found");
    }

    @Test
    void shouldRejectUriWithNoHost() {
        // Given - Create URI with empty authority using URI constructor
        try {
            URI uriWithNoHost = new URI("https", "", "/path", null);

            // When & Then
            assertThatThrownBy(() -> factory.createForUri(uriWithNoHost))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid URI - no host found");
        } catch (Exception e) {
            // If URI construction fails, test an alternative approach
            // Use a URI that passes creation but has null host
            URI relativeUri = URI.create("/relative/path");

            assertThatThrownBy(() -> factory.createForUri(relativeUri))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid URI - no host found");
        }
    }

    @Test
    void shouldCreateFactoryWithSameDependencyInstances() {
        // Given
        PageFetcher sameFetcher = mock(PageFetcher.class);
        LinkExtractor sameExtractor = mock(LinkExtractor.class);
        CrawlObserver sameObserver = mock(CrawlObserver.class);
        FrontierQueue sameQueue = mock(FrontierQueue.class);
        VisitedRepository sameRepository = mock(VisitedRepository.class);

        // When
        WebCrawlerFactory factory1 = new WebCrawlerFactory(
                sameFetcher, sameExtractor, sameObserver, sameQueue, sameRepository, 5);
        WebCrawlerFactory factory2 = new WebCrawlerFactory(
                sameFetcher, sameExtractor, sameObserver, sameQueue, sameRepository, 10);

        // Then
        assertThat(factory1).isNotNull();
        assertThat(factory2).isNotNull();
        assertThat(factory1).isNotSameAs(factory2);
    }
}
