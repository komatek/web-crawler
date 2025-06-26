package com.monzo.crawler.infrastructure;

import com.monzo.crawler.infrastructure.ConsoleCrawlObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.net.URI;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsoleCrawlObserverTest {

    private ConsoleCrawlObserver observer;
    private Logger mockLogger;

    @BeforeEach
    void setUp() {
        observer = new ConsoleCrawlObserver();
        mockLogger = mock(Logger.class);
    }

    @Test
    void shouldLogPageCrawledWithNewLinks() {
        // Given
        URI pageUri = URI.create("https://example.com/page1");
        Set<URI> links = Set.of(
                URI.create("https://example.com/page2"),
                URI.create("https://example.com/page3")
        );

        try (MockedStatic<org.slf4j.LoggerFactory> loggerFactory = mockStatic(org.slf4j.LoggerFactory.class)) {
            loggerFactory.when(() -> org.slf4j.LoggerFactory.getLogger(ConsoleCrawlObserver.class))
                    .thenReturn(mockLogger);

            ConsoleCrawlObserver observerWithMockedLogger = new ConsoleCrawlObserver();

            // When
            observerWithMockedLogger.onPageCrawled(pageUri, links);

            // Then
            ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockLogger).info(logCaptor.capture());

            String logMessage = logCaptor.getValue();
            assertThat(logMessage).contains("Crawled Page: https://example.com/page1");
            assertThat(logMessage).contains("Found 2 total links");
            assertThat(logMessage).contains("(2 new):");
            assertThat(logMessage).contains("-> https://example.com/page2");
            assertThat(logMessage).contains("-> https://example.com/page3");
            assertThat(logMessage).contains("--------------------------------------------------");
        }
    }

    @Test
    void shouldLogPageCrawledWithAllAlreadySeenLinks() {
        // Given
        URI pageUri1 = URI.create("https://example.com/page1");
        URI pageUri2 = URI.create("https://example.com/page2");
        Set<URI> links = Set.of(
                URI.create("https://example.com/page3"),
                URI.create("https://example.com/page4")
        );

        try (MockedStatic<org.slf4j.LoggerFactory> loggerFactory = mockStatic(org.slf4j.LoggerFactory.class)) {
            loggerFactory.when(() -> org.slf4j.LoggerFactory.getLogger(ConsoleCrawlObserver.class))
                    .thenReturn(mockLogger);

            ConsoleCrawlObserver observerWithMockedLogger = new ConsoleCrawlObserver();

            // First crawl to populate seen links
            observerWithMockedLogger.onPageCrawled(pageUri1, links);
            reset(mockLogger); // Reset to clear first call

            // When - crawl again with same links
            observerWithMockedLogger.onPageCrawled(pageUri2, links);

            // Then
            ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockLogger).info(logCaptor.capture());

            String logMessage = logCaptor.getValue();
            assertThat(logMessage).contains("Crawled Page: https://example.com/page2");
            assertThat(logMessage).contains("Found 2 total links");
            assertThat(logMessage).contains("(all already seen)");
            assertThat(logMessage).doesNotContain("->");
        }
    }

    @Test
    void shouldLogPageCrawledWithMixOfNewAndSeenLinks() {
        // Given
        URI pageUri1 = URI.create("https://example.com/page1");
        URI pageUri2 = URI.create("https://example.com/page2");

        Set<URI> firstLinks = Set.of(
                URI.create("https://example.com/page3"),
                URI.create("https://example.com/page4")
        );

        Set<URI> secondLinks = Set.of(
                URI.create("https://example.com/page3"), // Already seen
                URI.create("https://example.com/page5")  // New
        );

        try (MockedStatic<org.slf4j.LoggerFactory> loggerFactory = mockStatic(org.slf4j.LoggerFactory.class)) {
            loggerFactory.when(() -> org.slf4j.LoggerFactory.getLogger(ConsoleCrawlObserver.class))
                    .thenReturn(mockLogger);

            ConsoleCrawlObserver observerWithMockedLogger = new ConsoleCrawlObserver();

            // First crawl
            observerWithMockedLogger.onPageCrawled(pageUri1, firstLinks);
            reset(mockLogger);

            // When - second crawl with mix of new and seen links
            observerWithMockedLogger.onPageCrawled(pageUri2, secondLinks);

            // Then
            ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockLogger).info(logCaptor.capture());

            String logMessage = logCaptor.getValue();
            assertThat(logMessage).contains("Crawled Page: https://example.com/page2");
            assertThat(logMessage).contains("Found 2 total links");
            assertThat(logMessage).contains("(1 new):");
            assertThat(logMessage).contains("-> https://example.com/page5");
            assertThat(logMessage).doesNotContain("-> https://example.com/page3");
        }
    }

    @Test
    void shouldLogPageCrawledWithNoLinks() {
        // Given
        URI pageUri = URI.create("https://example.com/page1");
        Set<URI> emptyLinks = Set.of();

        try (MockedStatic<org.slf4j.LoggerFactory> loggerFactory = mockStatic(org.slf4j.LoggerFactory.class)) {
            loggerFactory.when(() -> org.slf4j.LoggerFactory.getLogger(ConsoleCrawlObserver.class))
                    .thenReturn(mockLogger);

            ConsoleCrawlObserver observerWithMockedLogger = new ConsoleCrawlObserver();

            // When
            observerWithMockedLogger.onPageCrawled(pageUri, emptyLinks);

            // Then
            ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockLogger).info(logCaptor.capture());

            String logMessage = logCaptor.getValue();
            assertThat(logMessage).contains("Crawled Page: https://example.com/page1");
            assertThat(logMessage).contains("Found 0 total links");
            assertThat(logMessage).contains("(all already seen)");
        }
    }

    @Test
    void shouldSortLinksAlphabetically() {
        // Given
        URI pageUri = URI.create("https://example.com/page1");
        Set<URI> links = Set.of(
                URI.create("https://example.com/zebra"),
                URI.create("https://example.com/alpha"),
                URI.create("https://example.com/beta")
        );

        try (MockedStatic<org.slf4j.LoggerFactory> loggerFactory = mockStatic(org.slf4j.LoggerFactory.class)) {
            loggerFactory.when(() -> org.slf4j.LoggerFactory.getLogger(ConsoleCrawlObserver.class))
                    .thenReturn(mockLogger);

            ConsoleCrawlObserver observerWithMockedLogger = new ConsoleCrawlObserver();

            // When
            observerWithMockedLogger.onPageCrawled(pageUri, links);

            // Then
            ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockLogger).info(logCaptor.capture());

            String logMessage = logCaptor.getValue();

            // Verify alphabetical ordering
            int alphaIndex = logMessage.indexOf("-> https://example.com/alpha");
            int betaIndex = logMessage.indexOf("-> https://example.com/beta");
            int zebraIndex = logMessage.indexOf("-> https://example.com/zebra");

            assertThat(alphaIndex).isLessThan(betaIndex);
            assertThat(betaIndex).isLessThan(zebraIndex);
        }
    }

    @Test
    void shouldLogCrawlFailedWithError() {
        // Given
        URI pageUri = URI.create("https://example.com/failed-page");
        String reason = "CONNECTION_TIMEOUT";
        RuntimeException error = new RuntimeException("Connection timed out");

        try (MockedStatic<org.slf4j.LoggerFactory> loggerFactory = mockStatic(org.slf4j.LoggerFactory.class)) {
            loggerFactory.when(() -> org.slf4j.LoggerFactory.getLogger(ConsoleCrawlObserver.class))
                    .thenReturn(mockLogger);

            ConsoleCrawlObserver observerWithMockedLogger = new ConsoleCrawlObserver();

            // When
            observerWithMockedLogger.onCrawlFailed(pageUri, reason, error);

            // Then
            verify(mockLogger).warn("Failed to crawl {}: Reason: {}. Error: {}",
                    pageUri, reason, error.getMessage());
        }
    }

    @Test
    void shouldLogCrawlFailedWithoutError() {
        // Given
        URI pageUri = URI.create("https://example.com/failed-page");
        String reason = "NOT_FOUND";

        try (MockedStatic<org.slf4j.LoggerFactory> loggerFactory = mockStatic(org.slf4j.LoggerFactory.class)) {
            loggerFactory.when(() -> org.slf4j.LoggerFactory.getLogger(ConsoleCrawlObserver.class))
                    .thenReturn(mockLogger);

            ConsoleCrawlObserver observerWithMockedLogger = new ConsoleCrawlObserver();

            // When
            observerWithMockedLogger.onCrawlFailed(pageUri, reason, null);

            // Then
            verify(mockLogger).warn("Failed to crawl {}: Reason: {}", pageUri, reason);
        }
    }

    @Test
    void shouldMaintainStateAcrossMultipleCrawls() {
        // Given
        URI pageUri1 = URI.create("https://example.com/page1");
        URI pageUri2 = URI.create("https://example.com/page2");
        URI pageUri3 = URI.create("https://example.com/page3");

        Set<URI> links1 = Set.of(URI.create("https://example.com/link1"));
        Set<URI> links2 = Set.of(URI.create("https://example.com/link1"), URI.create("https://example.com/link2"));
        Set<URI> links3 = Set.of(URI.create("https://example.com/link2"), URI.create("https://example.com/link3"));

        try (MockedStatic<org.slf4j.LoggerFactory> loggerFactory = mockStatic(org.slf4j.LoggerFactory.class)) {
            loggerFactory.when(() -> org.slf4j.LoggerFactory.getLogger(ConsoleCrawlObserver.class))
                    .thenReturn(mockLogger);

            ConsoleCrawlObserver observerWithMockedLogger = new ConsoleCrawlObserver();

            // When
            observerWithMockedLogger.onPageCrawled(pageUri1, links1); // link1 is new
            observerWithMockedLogger.onPageCrawled(pageUri2, links2); // link1 seen, link2 new
            observerWithMockedLogger.onPageCrawled(pageUri3, links3); // link2 seen, link3 new

            // Then
            ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockLogger, times(3)).info(logCaptor.capture());

            assertThat(logCaptor.getAllValues().get(0)).contains("(1 new):");
            assertThat(logCaptor.getAllValues().get(1)).contains("(1 new):");
            assertThat(logCaptor.getAllValues().get(2)).contains("(1 new):");
        }
    }
}
