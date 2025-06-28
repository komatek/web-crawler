package com.monzo.crawler.application;

import com.monzo.crawler.domain.service.CrawlStateService;
import com.monzo.crawler.domain.service.PageProcessingService;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebCrawlerUseCaseTest {

    private final PageProcessingService pageProcessingService = mock(PageProcessingService.class);
    private final CrawlStateService crawlStateService = mock(CrawlStateService.class);

    private static final URI START_URI = URI.create("https://example.com");
    private static final URI PAGE_1_URI = URI.create("https://example.com/page1");
    private static final URI PAGE_2_URI = URI.create("https://example.com/page2");
    private static final int MAX_CONCURRENT_REQUESTS = 2;

    private final WebCrawlerUseCase webCrawler = new WebCrawlerUseCase(
            pageProcessingService,
            crawlStateService,
            MAX_CONCURRENT_REQUESTS
    );

    @Test
    void shouldCreateWebCrawlerWithValidDependencies() {
        // Given
        PageProcessingService mockPageProcessingService = mock(PageProcessingService.class);
        CrawlStateService mockCrawlStateService = mock(CrawlStateService.class);
        int maxConcurrentRequests = 5;

        // When
        WebCrawlerUseCase crawler = new WebCrawlerUseCase(
                mockPageProcessingService,
                mockCrawlStateService,
                maxConcurrentRequests
        );

        // Then
        assertNotNull(crawler);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenPageProcessingServiceIsNull() {
        // Given
        CrawlStateService mockCrawlStateService = mock(CrawlStateService.class);
        int maxConcurrentRequests = 5;

        // When & Then
        assertThrows(NullPointerException.class, () -> {
            new WebCrawlerUseCase(null, mockCrawlStateService, maxConcurrentRequests);
        });
    }

    @Test
    void shouldThrowNullPointerExceptionWhenCrawlStateServiceIsNull() {
        // Given
        PageProcessingService mockPageProcessingService = mock(PageProcessingService.class);
        int maxConcurrentRequests = 5;

        // When & Then
        assertThrows(NullPointerException.class, () -> {
            new WebCrawlerUseCase(mockPageProcessingService, null, maxConcurrentRequests);
        });
    }

    @Test
    void shouldThrowNullPointerExceptionWhenBothServicesAreNull() {
        // Given
        int maxConcurrentRequests = 5;

        // When & Then
        assertThrows(NullPointerException.class, () -> {
            new WebCrawlerUseCase(null, null, maxConcurrentRequests);
        });
    }

    @Test
    void shouldAddStartUriToFrontierWhenCrawlStarts() {
        // Given
        when(crawlStateService.getNextUri()).thenReturn(null);
        when(crawlStateService.isFrontierEmpty()).thenReturn(true);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(crawlStateService).tryAddToFrontier(START_URI);
    }

    @Test
    void shouldProcessSingleUriWhenOnlyOneUriInFrontier() {
        // Given
        when(crawlStateService.getNextUri())
                .thenReturn(START_URI)
                .thenReturn(null);
        when(crawlStateService.markAsVisited(START_URI)).thenReturn(true);
        when(crawlStateService.isFrontierEmpty()).thenReturn(true);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(crawlStateService).markAsVisited(START_URI);
        verify(pageProcessingService).processPage(START_URI);
    }

    @Test
    void shouldNotProcessUriWhenAlreadyVisited() {
        // Given
        when(crawlStateService.getNextUri())
                .thenReturn(START_URI)
                .thenReturn(null);
        when(crawlStateService.markAsVisited(START_URI)).thenReturn(false); // Already visited
        when(crawlStateService.isFrontierEmpty()).thenReturn(true);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(crawlStateService).markAsVisited(START_URI);
        verify(pageProcessingService, never()).processPage(any());
    }

    @Test
    void shouldProcessMultipleUrisWhenMultipleUrisInFrontier() {
        // Given
        when(crawlStateService.getNextUri())
                .thenReturn(PAGE_1_URI)
                .thenReturn(PAGE_2_URI)
                .thenReturn(null);
        when(crawlStateService.markAsVisited(PAGE_1_URI)).thenReturn(true);
        when(crawlStateService.markAsVisited(PAGE_2_URI)).thenReturn(true);
        when(crawlStateService.isFrontierEmpty()).thenReturn(true);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(pageProcessingService).processPage(PAGE_1_URI);
        verify(pageProcessingService).processPage(PAGE_2_URI);
        verify(crawlStateService).markAsVisited(PAGE_1_URI);
        verify(crawlStateService).markAsVisited(PAGE_2_URI);
    }

    @Test
    void shouldSkipAlreadyVisitedUrisInMultipleUriScenario() {
        // Given
        when(crawlStateService.getNextUri())
                .thenReturn(PAGE_1_URI)
                .thenReturn(PAGE_2_URI)
                .thenReturn(null);
        when(crawlStateService.markAsVisited(PAGE_1_URI)).thenReturn(true);
        when(crawlStateService.markAsVisited(PAGE_2_URI)).thenReturn(false); // Already visited
        when(crawlStateService.isFrontierEmpty()).thenReturn(true);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(pageProcessingService).processPage(PAGE_1_URI);
        verify(pageProcessingService, never()).processPage(PAGE_2_URI);
        verify(crawlStateService).markAsVisited(PAGE_1_URI);
        verify(crawlStateService).markAsVisited(PAGE_2_URI);
    }

    @Test
    void shouldWaitForTasksToCompleteWhenFrontierBecomesEmpty() throws InterruptedException {
        // Given
        CountDownLatch processingStarted = new CountDownLatch(1);
        CountDownLatch processingCanFinish = new CountDownLatch(1);

        when(crawlStateService.getNextUri())
                .thenReturn(PAGE_1_URI)
                .thenReturn(null)  // First time frontier is empty
                .thenReturn(null); // Second time frontier is still empty
        when(crawlStateService.markAsVisited(PAGE_1_URI)).thenReturn(true);
        when(crawlStateService.isFrontierEmpty()).thenReturn(true);

        // Mock page processing to simulate slow processing
        doAnswer(invocation -> {
            processingStarted.countDown();
            assertTrue(processingCanFinish.await(5, TimeUnit.SECONDS));
            return null;
        }).when(pageProcessingService).processPage(PAGE_1_URI);

        // When
        Thread crawlThread = new Thread(() -> webCrawler.crawl(START_URI));
        crawlThread.start();

        // Wait for processing to start
        assertTrue(processingStarted.await(5, TimeUnit.SECONDS));

        // Allow processing to finish
        processingCanFinish.countDown();

        // Wait for crawl to complete
        crawlThread.join(5000);
        assertFalse(crawlThread.isAlive());

        // Then
        verify(pageProcessingService).processPage(PAGE_1_URI);
    }

    @Test
    void shouldRespectConcurrencyLimit() throws InterruptedException {
        // Given
        AtomicInteger concurrentTasks = new AtomicInteger(0);
        AtomicInteger maxConcurrentTasks = new AtomicInteger(0);
        CountDownLatch firstBatchStarted = new CountDownLatch(MAX_CONCURRENT_REQUESTS);
        CountDownLatch allTasksStarted = new CountDownLatch(3);
        CountDownLatch tasksCanFinish = new CountDownLatch(1);

        URI uri1 = URI.create("https://example.com/page1");
        URI uri2 = URI.create("https://example.com/page2");
        URI uri3 = URI.create("https://example.com/page3");

        when(crawlStateService.getNextUri())
                .thenReturn(uri1)
                .thenReturn(uri2)
                .thenReturn(uri3)
                .thenReturn(null);
        when(crawlStateService.markAsVisited(any())).thenReturn(true);
        when(crawlStateService.isFrontierEmpty()).thenReturn(false, false, false, true);

        // Mock page processing to track concurrency
        doAnswer(invocation -> {
            int current = concurrentTasks.incrementAndGet();
            maxConcurrentTasks.updateAndGet(max -> Math.max(max, current));
            firstBatchStarted.countDown();
            allTasksStarted.countDown();
            assertTrue(tasksCanFinish.await(5, TimeUnit.SECONDS));
            concurrentTasks.decrementAndGet();
            return null;
        }).when(pageProcessingService).processPage(any());

        // When
        Thread crawlThread = new Thread(() -> webCrawler.crawl(START_URI));
        crawlThread.start();

        // Wait for first batch to start (should be limited by semaphore)
        assertTrue(firstBatchStarted.await(5, TimeUnit.SECONDS));

        // Give a moment for the third task to try to start (it should be blocked)
        Thread.sleep(100);

        // At this point, only MAX_CONCURRENT_REQUESTS tasks should be running
        assertEquals(MAX_CONCURRENT_REQUESTS, concurrentTasks.get(),
                "Should have exactly " + MAX_CONCURRENT_REQUESTS + " concurrent tasks");

        // Allow tasks to finish
        tasksCanFinish.countDown();

        // Wait for all tasks to complete
        assertTrue(allTasksStarted.await(5, TimeUnit.SECONDS));

        // Wait for crawl to complete
        crawlThread.join(5000);
        assertFalse(crawlThread.isAlive());

        // Then
        assertTrue(maxConcurrentTasks.get() <= MAX_CONCURRENT_REQUESTS,
                "Max concurrent tasks was " + maxConcurrentTasks.get() +
                        " but should not exceed " + MAX_CONCURRENT_REQUESTS);
        verify(pageProcessingService, times(3)).processPage(any());
    }

    @Test
    void shouldHandleInterruptedExceptionGracefully() {
        // Given
        when(crawlStateService.getNextUri()).thenReturn(PAGE_1_URI).thenReturn(null);
        when(crawlStateService.markAsVisited(PAGE_1_URI)).thenReturn(true);
        when(crawlStateService.isFrontierEmpty()).thenReturn(true);

        // Mock page processing to throw InterruptedException
        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Task interrupted");
        }).when(pageProcessingService).processPage(PAGE_1_URI);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(pageProcessingService).processPage(PAGE_1_URI);
        // Should complete without throwing exception
    }

    @Test
    void shouldContinueCrawlingWhenNewUrisAddedDuringProcessing() throws InterruptedException {
        // Given
        CountDownLatch firstProcessingStarted = new CountDownLatch(1);
        CountDownLatch firstProcessingCanFinish = new CountDownLatch(1);

        when(crawlStateService.getNextUri())
                .thenReturn(PAGE_1_URI)
                .thenReturn(null)     // First check - frontier empty
                .thenReturn(PAGE_2_URI) // After waiting, new URI appears
                .thenReturn(null);    // Finally empty
        when(crawlStateService.markAsVisited(any())).thenReturn(true);
        when(crawlStateService.isFrontierEmpty())
                .thenReturn(false)    // Initially not empty
                .thenReturn(true)     // Empty after first URI
                .thenReturn(false)    // Not empty when PAGE_2_URI added
                .thenReturn(true);    // Finally empty

        doAnswer(invocation -> {
            if (invocation.getArgument(0).equals(PAGE_1_URI)) {
                firstProcessingStarted.countDown();
                assertTrue(firstProcessingCanFinish.await(5, TimeUnit.SECONDS));
            }
            return null;
        }).when(pageProcessingService).processPage(any());

        // When
        Thread crawlThread = new Thread(() -> webCrawler.crawl(START_URI));
        crawlThread.start();

        // Wait for first processing to start
        assertTrue(firstProcessingStarted.await(5, TimeUnit.SECONDS));

        // Allow first processing to finish
        firstProcessingCanFinish.countDown();

        // Wait for crawl to complete
        crawlThread.join(5000);
        assertFalse(crawlThread.isAlive());

        // Then
        verify(pageProcessingService).processPage(PAGE_1_URI);
        verify(pageProcessingService).processPage(PAGE_2_URI);
    }

    @Test
    void shouldCompleteSuccessfullyWhenNoUrisInFrontier() {
        // Given
        when(crawlStateService.getNextUri()).thenReturn(null);
        when(crawlStateService.isFrontierEmpty()).thenReturn(true);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(crawlStateService).tryAddToFrontier(START_URI);
        verify(crawlStateService).getNextUri();
        verify(pageProcessingService, never()).processPage(any());
    }

    @Test
    void shouldCallServicesInCorrectOrder() {
        // Given
        when(crawlStateService.getNextUri())
                .thenReturn(START_URI)
                .thenReturn(null);
        when(crawlStateService.markAsVisited(START_URI)).thenReturn(true);
        when(crawlStateService.isFrontierEmpty()).thenReturn(true);

        // When
        webCrawler.crawl(START_URI);

        // Then - verify the order of operations
        var inOrder = inOrder(crawlStateService, pageProcessingService);
        inOrder.verify(crawlStateService).tryAddToFrontier(START_URI);
        inOrder.verify(crawlStateService).getNextUri();
        inOrder.verify(crawlStateService).markAsVisited(START_URI);
        inOrder.verify(pageProcessingService).processPage(START_URI);
    }

    @Test
    void shouldHandleNullUriFromGetNextUri() {
        // Given
        when(crawlStateService.getNextUri()).thenReturn(null);
        when(crawlStateService.isFrontierEmpty()).thenReturn(true);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(crawlStateService).tryAddToFrontier(START_URI);
        verify(crawlStateService).getNextUri();
        verify(crawlStateService, never()).markAsVisited(any());
        verify(pageProcessingService, never()).processPage(any());
    }

    @Test
    void shouldContinueProcessingEvenWhenSomeTasksFail() {
        // Given
        when(crawlStateService.getNextUri())
                .thenReturn(PAGE_1_URI)
                .thenReturn(PAGE_2_URI)
                .thenReturn(null);
        when(crawlStateService.markAsVisited(any())).thenReturn(true);
        when(crawlStateService.isFrontierEmpty()).thenReturn(true);

        // Mock first page to throw exception, second to succeed
        doThrow(new RuntimeException("Processing failed"))
                .when(pageProcessingService).processPage(PAGE_1_URI);
        doNothing().when(pageProcessingService).processPage(PAGE_2_URI);

        // When
        webCrawler.crawl(START_URI);

        // Then
        verify(pageProcessingService).processPage(PAGE_1_URI);
        verify(pageProcessingService).processPage(PAGE_2_URI);
    }

    @Test
    void shouldHandleNullStartUri() {
        // Given
        when(crawlStateService.getNextUri()).thenReturn(null);
        when(crawlStateService.isFrontierEmpty()).thenReturn(true);

        // When
        webCrawler.crawl(null);

        // Then
        verify(crawlStateService).tryAddToFrontier(null);
    }
}
