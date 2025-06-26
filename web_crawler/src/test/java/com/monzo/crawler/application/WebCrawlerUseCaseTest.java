package com.monzo.crawler.application;

import com.monzo.crawler.domain.port.out.*;
import com.monzo.crawler.domain.model.PageData;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class WebCrawlerUseCaseTest {

    @Mock
    private PageFetcher pageFetcher;

    @Mock
    private LinkExtractor linkExtractor;

    @Mock
    private CrawlObserver crawlObserver;

    @Mock
    private FrontierQueue frontierQueue;

    @Mock
    private VisitedRepository visitedRepository;

    private WebCrawlerUseCase webCrawler;

    private static final String DOMAIN = "example.com";
    private static final URI START_URI = URI.create("https://example.com");
    private static final URI PAGE_1_URI = URI.create("https://example.com/page1");
    private static final URI PAGE_2_URI = URI.create("https://example.com/page2");
    private static final URI EXTERNAL_URI = URI.create("https://external.com/page");
    private static final int MAX_CONCURRENT_REQUESTS = 10;
    private BlockingQueue<URI> testQueue;

    @BeforeEach
    void setUp() {
        webCrawler = new WebCrawlerUseCase(pageFetcher, linkExtractor, crawlObserver, frontierQueue, visitedRepository, DOMAIN, MAX_CONCURRENT_REQUESTS);
        testQueue = new LinkedBlockingQueue<>();

        // Setup frontier queue mock to use our test queue
        when(frontierQueue.dequeue()).thenAnswer(invocation -> testQueue.poll());
        when(frontierQueue.isEmpty()).thenAnswer(invocation -> testQueue.isEmpty());
        doAnswer(invocation -> {
            URI uri = invocation.getArgument(0);
            testQueue.offer(uri);
            return null;
        }).when(frontierQueue).enqueue(any(URI.class));
    }

    @Test
    void shouldCrawlSinglePageSuccessfully() {
        URI normalizedStartUri = START_URI.resolve("/");

        // Given
        String htmlContent = "<html><body>Test page</body></html>";
        PageData successPageData = new PageData(htmlContent, PageData.Status.SUCCESS);
        Set<URI> extractedLinks = Set.of(PAGE_1_URI);

        // Setup visited repository - start URI not visited, but PAGE_1_URI is visited when checked
        when(visitedRepository.markVisited(normalizedStartUri)).thenReturn(true);
        when(visitedRepository.markVisited(PAGE_1_URI)).thenReturn(true);
        when(visitedRepository.isVisited(PAGE_1_URI)).thenReturn(false);

        when(pageFetcher.fetch(normalizedStartUri)).thenReturn(successPageData);
        when(pageFetcher.fetch(PAGE_1_URI)).thenReturn(new PageData(null, PageData.Status.NOT_FOUND));
        when(linkExtractor.extractLinks(htmlContent, normalizedStartUri)).thenReturn(extractedLinks);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(frontierQueue).enqueue(normalizedStartUri);
        verify(frontierQueue).enqueue(PAGE_1_URI);
        verify(visitedRepository).markVisited(normalizedStartUri);
        verify(visitedRepository).markVisited(PAGE_1_URI);
        verify(pageFetcher).fetch(normalizedStartUri);
        verify(pageFetcher).fetch(PAGE_1_URI);
        verify(linkExtractor).extractLinks(htmlContent, normalizedStartUri);
        verify(crawlObserver).onPageCrawled(normalizedStartUri, extractedLinks);
        verify(crawlObserver).onCrawlFailed(PAGE_1_URI, "NOT_FOUND", null);
    }

    @Test
    void shouldCrawlMultiplePagesInSameDomain() throws InterruptedException {
        URI normalizedStartUri = START_URI.resolve("/");

        // Given
        String htmlContent1 = "<html><body>Page 1</body></html>";
        String htmlContent2 = "<html><body>Page 2</body></html>";

        PageData pageData1 = new PageData(htmlContent1, PageData.Status.SUCCESS);
        PageData pageData2 = new PageData(htmlContent2, PageData.Status.SUCCESS);

        Set<URI> linksFromStart = Set.of(PAGE_1_URI, PAGE_2_URI);
        Set<URI> linksFromPage1 = Set.of(PAGE_2_URI);
        Set<URI> linksFromPage2 = Set.of();

        // Setup visited repository - PAGE_2_URI appears twice but should only be processed once
        when(visitedRepository.markVisited(normalizedStartUri)).thenReturn(true);
        when(visitedRepository.markVisited(PAGE_1_URI)).thenReturn(true);
        when(visitedRepository.markVisited(PAGE_2_URI))
                .thenReturn(true)  // First time: not visited, mark as visited
                .thenReturn(false); // Second time: already visited, don't process
        when(visitedRepository.isVisited(any())).thenReturn(false);

        when(pageFetcher.fetch(normalizedStartUri)).thenReturn(pageData1);
        when(pageFetcher.fetch(PAGE_1_URI)).thenReturn(pageData2);
        when(pageFetcher.fetch(PAGE_2_URI)).thenReturn(pageData2);

        when(linkExtractor.extractLinks(htmlContent1, normalizedStartUri)).thenReturn(linksFromStart);
        when(linkExtractor.extractLinks(htmlContent2, PAGE_1_URI)).thenReturn(linksFromPage1);
        when(linkExtractor.extractLinks(htmlContent2, PAGE_2_URI)).thenReturn(linksFromPage2);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(visitedRepository).markVisited(normalizedStartUri);
        verify(visitedRepository).markVisited(PAGE_1_URI);
        verify(visitedRepository, times(2)).markVisited(PAGE_2_URI); // Called twice but only processed once
        verify(pageFetcher).fetch(normalizedStartUri);
        verify(pageFetcher).fetch(PAGE_1_URI);
        verify(pageFetcher).fetch(PAGE_2_URI);
        verify(crawlObserver).onPageCrawled(normalizedStartUri, linksFromStart);
        verify(crawlObserver).onPageCrawled(PAGE_1_URI, linksFromPage1);
        verify(crawlObserver).onPageCrawled(PAGE_2_URI, linksFromPage2);
    }

    @Test
    void shouldNotCrawlExternalDomains() {
        URI normalizedStartUri = START_URI.resolve("/");

        // Given
        String htmlContent = "<html><body>Test page</body></html>";
        PageData successPageData = new PageData(htmlContent, PageData.Status.SUCCESS);
        Set<URI> extractedLinks = Set.of(EXTERNAL_URI, PAGE_1_URI);

        when(visitedRepository.markVisited(normalizedStartUri)).thenReturn(true);
        when(visitedRepository.markVisited(PAGE_1_URI)).thenReturn(true);
        when(visitedRepository.isVisited(PAGE_1_URI)).thenReturn(false);

        when(pageFetcher.fetch(normalizedStartUri)).thenReturn(successPageData);
        when(pageFetcher.fetch(PAGE_1_URI)).thenReturn(new PageData(null, PageData.Status.NOT_FOUND));
        when(linkExtractor.extractLinks(htmlContent, normalizedStartUri)).thenReturn(extractedLinks);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(frontierQueue).enqueue(normalizedStartUri);
        verify(frontierQueue).enqueue(PAGE_1_URI); // Same domain should be enqueued
        verify(frontierQueue, never()).enqueue(EXTERNAL_URI); // External domain should not be enqueued
        verify(pageFetcher).fetch(normalizedStartUri);
        verify(pageFetcher).fetch(PAGE_1_URI);
        verify(pageFetcher, never()).fetch(EXTERNAL_URI);
        verify(crawlObserver).onPageCrawled(normalizedStartUri, extractedLinks);
        verify(crawlObserver).onCrawlFailed(PAGE_1_URI, "NOT_FOUND", null);
    }

    @Test
    void shouldNotCrawlSamePageTwice() {
        URI normalizedStartUri = START_URI.resolve("/");

        // Given
        String htmlContent = "<html><body>Test page</body></html>";
        PageData successPageData = new PageData(htmlContent, PageData.Status.SUCCESS);
        Set<URI> extractedLinks = Set.of(PAGE_1_URI);

        // Setup visited repository - first call returns true (not visited), second returns false (already visited)
        when(visitedRepository.markVisited(normalizedStartUri)).thenReturn(true);
        when(visitedRepository.markVisited(PAGE_1_URI)).thenReturn(true);
        when(visitedRepository.isVisited(normalizedStartUri)).thenReturn(true); // Already visited when found as link
        when(visitedRepository.isVisited(PAGE_1_URI)).thenReturn(false);

        when(pageFetcher.fetch(normalizedStartUri)).thenReturn(successPageData);
        when(pageFetcher.fetch(PAGE_1_URI)).thenReturn(successPageData);
        when(linkExtractor.extractLinks(htmlContent, normalizedStartUri)).thenReturn(extractedLinks);
        when(linkExtractor.extractLinks(htmlContent, PAGE_1_URI)).thenReturn(Set.of(normalizedStartUri));

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(visitedRepository).markVisited(normalizedStartUri);
        verify(visitedRepository).markVisited(PAGE_1_URI);
        verify(pageFetcher).fetch(normalizedStartUri);
        verify(pageFetcher).fetch(PAGE_1_URI);
        verify(crawlObserver).onPageCrawled(normalizedStartUri, extractedLinks);
        verify(crawlObserver).onPageCrawled(PAGE_1_URI, Set.of(normalizedStartUri));

        // Verify start URI is not enqueued again since it's already visited
        verify(frontierQueue, times(1)).enqueue(normalizedStartUri); // Only initial enqueue
    }

    @Test
    void shouldHandlePageFetchFailure() {
        URI normalizedStartUri = START_URI.resolve("/");

        // Given
        PageData failedPageData = new PageData(null, PageData.Status.SERVER_ERROR);
        when(visitedRepository.markVisited(normalizedStartUri)).thenReturn(true);
        when(pageFetcher.fetch(normalizedStartUri)).thenReturn(failedPageData);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(visitedRepository).markVisited(normalizedStartUri);
        verify(pageFetcher).fetch(normalizedStartUri);
        verify(linkExtractor, never()).extractLinks(any(), any());
        verify(crawlObserver).onCrawlFailed(normalizedStartUri, "SERVER_ERROR", null);
        verify(crawlObserver, never()).onPageCrawled(any(), any());
    }

    @Test
    void shouldHandleUnexpectedExceptions() {
        URI normalizedStartUri = START_URI.resolve("/");

        // Given
        RuntimeException expectedException = new RuntimeException("Unexpected error");
        when(visitedRepository.markVisited(normalizedStartUri)).thenReturn(true);
        when(pageFetcher.fetch(normalizedStartUri)).thenThrow(expectedException);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(visitedRepository).markVisited(normalizedStartUri);
        verify(pageFetcher).fetch(normalizedStartUri);

        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(crawlObserver).onCrawlFailed(eq(normalizedStartUri), eq("UNEXPECTED_ERROR"), errorCaptor.capture());

        assertThat(errorCaptor.getValue()).isEqualTo(expectedException);
    }

    @Test
    void shouldNormalizeUris() {
        URI normalizedStartUri = START_URI.resolve("/");

        // Given
        URI uriWithTrailingSlash = URI.create("https://example.com/page/");
        URI uriWithoutTrailingSlash = URI.create("https://example.com/page");

        String htmlContent = "<html><body>Test page</body></html>";
        PageData successPageData = new PageData(htmlContent, PageData.Status.SUCCESS);

        when(visitedRepository.markVisited(normalizedStartUri)).thenReturn(true);
        when(visitedRepository.markVisited(uriWithoutTrailingSlash)).thenReturn(true);
        when(visitedRepository.isVisited(uriWithoutTrailingSlash)).thenReturn(false);

        when(pageFetcher.fetch(normalizedStartUri)).thenReturn(successPageData);
        when(pageFetcher.fetch(uriWithoutTrailingSlash)).thenReturn(new PageData(null, PageData.Status.NOT_FOUND));
        when(linkExtractor.extractLinks(htmlContent, normalizedStartUri)).thenReturn(Set.of(uriWithTrailingSlash));

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(frontierQueue).enqueue(normalizedStartUri);
        verify(frontierQueue).enqueue(uriWithoutTrailingSlash); // Normalized URI should be enqueued
        verify(frontierQueue, never()).enqueue(uriWithTrailingSlash); // Original URI should not be enqueued
        verify(pageFetcher).fetch(normalizedStartUri);
        verify(pageFetcher).fetch(uriWithoutTrailingSlash);
        verify(pageFetcher, never()).fetch(uriWithTrailingSlash);
    }

    @Test
    void shouldRespectVisitedRepository() {
        URI normalizedStartUri = START_URI.resolve("/");

        // Given - URI is already visited according to repository
        when(visitedRepository.markVisited(normalizedStartUri)).thenReturn(false); // Already visited

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(visitedRepository).markVisited(normalizedStartUri);
        verify(pageFetcher, never()).fetch(any()); // Should not fetch if already visited
        verify(crawlObserver, never()).onPageCrawled(any(), any());
        verify(crawlObserver, never()).onCrawlFailed(any(), any(), any());
    }

    @Test
    void shouldHandleEmptyLinkExtractionGracefully() {
        URI normalizedStartUri = START_URI.resolve("/");

        // Given
        String htmlContent = "<html><body>No links here</body></html>";
        PageData successPageData = new PageData(htmlContent, PageData.Status.SUCCESS);
        Set<URI> emptyLinks = Set.of();

        when(visitedRepository.markVisited(normalizedStartUri)).thenReturn(true);
        when(pageFetcher.fetch(normalizedStartUri)).thenReturn(successPageData);
        when(linkExtractor.extractLinks(htmlContent, normalizedStartUri)).thenReturn(emptyLinks);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(visitedRepository).markVisited(normalizedStartUri);
        verify(pageFetcher).fetch(normalizedStartUri);
        verify(linkExtractor).extractLinks(htmlContent, normalizedStartUri);
        verify(crawlObserver).onPageCrawled(normalizedStartUri, emptyLinks);

        // Verify no additional URIs are enqueued
        verify(frontierQueue, times(1)).enqueue(any()); // Only the start URI
    }
}
