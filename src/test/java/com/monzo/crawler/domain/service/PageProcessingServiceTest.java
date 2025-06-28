package com.monzo.crawler.domain.service;

import com.monzo.crawler.domain.model.PageData;
import com.monzo.crawler.domain.port.out.CrawlObserver;
import com.monzo.crawler.domain.port.out.LinkExtractor;
import com.monzo.crawler.domain.port.out.PageFetcher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PageProcessingServiceTest {

    private final PageFetcher pageFetcher = mock(PageFetcher.class);
    private final LinkExtractor linkExtractor = mock(LinkExtractor.class);
    private final CrawlObserver crawlObserver = mock(CrawlObserver.class);
    private final CrawlStateService crawlStateService = mock(CrawlStateService.class);

    private final PageProcessingService pageProcessingService = new PageProcessingService(
            pageFetcher,
            linkExtractor,
            crawlObserver,
            crawlStateService
    );

    private static final URI TEST_URI = URI.create("https://example.com/test-page");
    private static final URI LINK_1_URI = URI.create("https://example.com/link1");
    private static final URI LINK_2_URI = URI.create("https://example.com/link2");
    private static final URI EXTERNAL_LINK_URI = URI.create("https://external.com/link");

    private static final String HTML_CONTENT = "<html><body><h1>Test Page</h1><a href='/link1'>Link 1</a><a href='/link2'>Link 2</a></body></html>";

    @Test
    void constructorShouldThrowNullPointerExceptionWhenPageFetcherIsNull() {
        assertThrows(NullPointerException.class, () ->
                new PageProcessingService(null, linkExtractor, crawlObserver, crawlStateService)
        );
    }

    @Test
    void constructorShouldThrowNullPointerExceptionWhenLinkExtractorIsNull() {
        assertThrows(NullPointerException.class, () ->
                new PageProcessingService(pageFetcher, null, crawlObserver, crawlStateService)
        );
    }

    @Test
    void constructorShouldThrowNullPointerExceptionWhenCrawlObserverIsNull() {
        assertThrows(NullPointerException.class, () ->
                new PageProcessingService(pageFetcher, linkExtractor, null, crawlStateService)
        );
    }

    @Test
    void constructorShouldThrowNullPointerExceptionWhenCrawlStateServiceIsNull() {
        assertThrows(NullPointerException.class, () ->
                new PageProcessingService(pageFetcher, linkExtractor, crawlObserver, null)
        );
    }

    // Successful page processing tests
    @Test
    void processPageShouldHandleSuccessfulPageWithMultipleLinks() {
        // Given
        Set<URI> discoveredLinks = Set.of(LINK_1_URI, LINK_2_URI, EXTERNAL_LINK_URI);
        Set<URI> enqueuedLinks = Set.of(LINK_1_URI, LINK_2_URI); // External link filtered out
        PageData successPageData = new PageData(HTML_CONTENT, PageData.Status.SUCCESS);

        when(pageFetcher.fetch(TEST_URI)).thenReturn(successPageData);
        when(linkExtractor.extractLinks(HTML_CONTENT, TEST_URI)).thenReturn(discoveredLinks);
        when(crawlStateService.processDiscoveredLinks(discoveredLinks)).thenReturn(enqueuedLinks);

        // When
        pageProcessingService.processPage(TEST_URI);

        // Then
        verify(pageFetcher).fetch(TEST_URI);
        verify(linkExtractor).extractLinks(HTML_CONTENT, TEST_URI);
        verify(crawlStateService).processDiscoveredLinks(discoveredLinks);
        verify(crawlObserver).onPageCrawled(TEST_URI, discoveredLinks);
        verifyNoMoreInteractions(crawlObserver);
    }

    @Test
    void processPageShouldHandleSuccessfulPageWithSingleLink() {
        // Given
        Set<URI> discoveredLinks = Set.of(LINK_1_URI);
        Set<URI> enqueuedLinks = Set.of(LINK_1_URI);
        PageData successPageData = new PageData(HTML_CONTENT, PageData.Status.SUCCESS);

        when(pageFetcher.fetch(TEST_URI)).thenReturn(successPageData);
        when(linkExtractor.extractLinks(HTML_CONTENT, TEST_URI)).thenReturn(discoveredLinks);
        when(crawlStateService.processDiscoveredLinks(discoveredLinks)).thenReturn(enqueuedLinks);

        // When
        pageProcessingService.processPage(TEST_URI);

        // Then
        verify(pageFetcher).fetch(TEST_URI);
        verify(linkExtractor).extractLinks(HTML_CONTENT, TEST_URI);
        verify(crawlStateService).processDiscoveredLinks(discoveredLinks);
        verify(crawlObserver).onPageCrawled(TEST_URI, discoveredLinks);
        verifyNoMoreInteractions(crawlObserver);
    }

    @Test
    void processPageShouldHandleSuccessfulPageWithNoLinks() {
        // Given
        Set<URI> emptyLinks = Set.of();
        PageData successPageData = new PageData(HTML_CONTENT, PageData.Status.SUCCESS);

        when(pageFetcher.fetch(TEST_URI)).thenReturn(successPageData);
        when(linkExtractor.extractLinks(HTML_CONTENT, TEST_URI)).thenReturn(emptyLinks);
        when(crawlStateService.processDiscoveredLinks(emptyLinks)).thenReturn(emptyLinks);

        // When
        pageProcessingService.processPage(TEST_URI);

        // Then
        verify(pageFetcher).fetch(TEST_URI);
        verify(linkExtractor).extractLinks(HTML_CONTENT, TEST_URI);
        verify(crawlStateService).processDiscoveredLinks(emptyLinks);
        verify(crawlObserver).onPageCrawled(TEST_URI, emptyLinks);
        verifyNoMoreInteractions(crawlObserver);
    }

    @Test
    void processPageShouldHandleSuccessfulPageWithEmptyHtmlContent() {
        // Given
        String emptyHtml = "";
        Set<URI> emptyLinks = Set.of();
        PageData successPageData = new PageData(emptyHtml, PageData.Status.SUCCESS);

        when(pageFetcher.fetch(TEST_URI)).thenReturn(successPageData);
        when(linkExtractor.extractLinks(emptyHtml, TEST_URI)).thenReturn(emptyLinks);
        when(crawlStateService.processDiscoveredLinks(emptyLinks)).thenReturn(emptyLinks);

        // When
        pageProcessingService.processPage(TEST_URI);

        // Then
        verify(pageFetcher).fetch(TEST_URI);
        verify(linkExtractor).extractLinks(emptyHtml, TEST_URI);
        verify(crawlStateService).processDiscoveredLinks(emptyLinks);
        verify(crawlObserver).onPageCrawled(TEST_URI, emptyLinks);
    }

    @Test
    void processPageShouldHandleSuccessfulPageWithNullHtmlContent() {
        // Given
        Set<URI> emptyLinks = Set.of();
        PageData successPageData = new PageData(null, PageData.Status.SUCCESS);

        when(pageFetcher.fetch(TEST_URI)).thenReturn(successPageData);
        when(linkExtractor.extractLinks(null, TEST_URI)).thenReturn(emptyLinks);
        when(crawlStateService.processDiscoveredLinks(emptyLinks)).thenReturn(emptyLinks);

        // When
        pageProcessingService.processPage(TEST_URI);

        // Then
        verify(pageFetcher).fetch(TEST_URI);
        verify(linkExtractor).extractLinks(null, TEST_URI);
        verify(crawlStateService).processDiscoveredLinks(emptyLinks);
        verify(crawlObserver).onPageCrawled(TEST_URI, emptyLinks);
    }

    // Failed page processing tests
    @Test
    void processPageShouldHandleNotFoundStatus() {
        // Given
        PageData notFoundPageData = new PageData(null, PageData.Status.NOT_FOUND);

        when(pageFetcher.fetch(TEST_URI)).thenReturn(notFoundPageData);

        // When
        pageProcessingService.processPage(TEST_URI);

        // Then
        verify(pageFetcher).fetch(TEST_URI);
        verify(linkExtractor, never()).extractLinks(any(), any());
        verify(crawlStateService, never()).processDiscoveredLinks(any());
        verify(crawlObserver).onCrawlFailed(TEST_URI, "NOT_FOUND", null);
        verify(crawlObserver, never()).onPageCrawled(any(), any());
    }

    @Test
    void processPageShouldHandleServerErrorStatus() {
        // Given
        PageData serverErrorPageData = new PageData(null, PageData.Status.SERVER_ERROR);

        when(pageFetcher.fetch(TEST_URI)).thenReturn(serverErrorPageData);

        // When
        pageProcessingService.processPage(TEST_URI);

        // Then
        verify(pageFetcher).fetch(TEST_URI);
        verify(linkExtractor, never()).extractLinks(any(), any());
        verify(crawlStateService, never()).processDiscoveredLinks(any());
        verify(crawlObserver).onCrawlFailed(TEST_URI, "SERVER_ERROR", null);
        verify(crawlObserver, never()).onPageCrawled(any(), any());
    }

    @Test
    void processPageShouldHandleClientErrorStatus() {
        // Given
        PageData clientErrorPageData = new PageData(null, PageData.Status.CLIENT_ERROR);

        when(pageFetcher.fetch(TEST_URI)).thenReturn(clientErrorPageData);

        // When
        pageProcessingService.processPage(TEST_URI);

        // Then
        verify(pageFetcher).fetch(TEST_URI);
        verify(linkExtractor, never()).extractLinks(any(), any());
        verify(crawlStateService, never()).processDiscoveredLinks(any());
        verify(crawlObserver).onCrawlFailed(TEST_URI, "CLIENT_ERROR", null);
        verify(crawlObserver, never()).onPageCrawled(any(), any());
    }

    // Exception handling tests
    @Test
    void processPageShouldHandlePageFetcherException() {
        // Given
        RuntimeException fetchException = new RuntimeException("Network connection failed");

        when(pageFetcher.fetch(TEST_URI)).thenThrow(fetchException);

        // When
        pageProcessingService.processPage(TEST_URI);

        // Then
        verify(pageFetcher).fetch(TEST_URI);
        verify(linkExtractor, never()).extractLinks(any(), any());
        verify(crawlStateService, never()).processDiscoveredLinks(any());

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(crawlObserver).onCrawlFailed(eq(TEST_URI), eq("UNEXPECTED_ERROR"), exceptionCaptor.capture());
        assertEquals(fetchException, exceptionCaptor.getValue());
        verify(crawlObserver, never()).onPageCrawled(any(), any());
    }

    @Test
    void processPageShouldHandleLinkExtractorException() {
        // Given
        PageData successPageData = new PageData(HTML_CONTENT, PageData.Status.SUCCESS);
        RuntimeException extractorException = new IllegalArgumentException("Invalid HTML structure");

        when(pageFetcher.fetch(TEST_URI)).thenReturn(successPageData);
        when(linkExtractor.extractLinks(HTML_CONTENT, TEST_URI)).thenThrow(extractorException);

        // When
        pageProcessingService.processPage(TEST_URI);

        // Then
        verify(pageFetcher).fetch(TEST_URI);
        verify(linkExtractor).extractLinks(HTML_CONTENT, TEST_URI);
        verify(crawlStateService, never()).processDiscoveredLinks(any());

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(crawlObserver).onCrawlFailed(eq(TEST_URI), eq("UNEXPECTED_ERROR"), exceptionCaptor.capture());
        assertEquals(extractorException, exceptionCaptor.getValue());
        verify(crawlObserver, never()).onPageCrawled(any(), any());
    }

    @Test
    void processPageShouldHandleCrawlStateServiceException() {
        // Given
        Set<URI> discoveredLinks = Set.of(LINK_1_URI);
        PageData successPageData = new PageData(HTML_CONTENT, PageData.Status.SUCCESS);
        RuntimeException stateServiceException = new IllegalStateException("Frontier queue is full");

        when(pageFetcher.fetch(TEST_URI)).thenReturn(successPageData);
        when(linkExtractor.extractLinks(HTML_CONTENT, TEST_URI)).thenReturn(discoveredLinks);
        when(crawlStateService.processDiscoveredLinks(discoveredLinks)).thenThrow(stateServiceException);

        // When
        pageProcessingService.processPage(TEST_URI);

        // Then
        verify(pageFetcher).fetch(TEST_URI);
        verify(linkExtractor).extractLinks(HTML_CONTENT, TEST_URI);
        verify(crawlStateService).processDiscoveredLinks(discoveredLinks);

        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(crawlObserver).onCrawlFailed(eq(TEST_URI), eq("UNEXPECTED_ERROR"), exceptionCaptor.capture());
        assertEquals(stateServiceException, exceptionCaptor.getValue());
        verify(crawlObserver, never()).onPageCrawled(any(), any());
    }

    @Test
    void processPageShouldHandleCrawlObserverExceptionDuringSuccess() {
        // Given
        Set<URI> discoveredLinks = Set.of(LINK_1_URI);
        Set<URI> enqueuedLinks = Set.of(LINK_1_URI);
        PageData successPageData = new PageData(HTML_CONTENT, PageData.Status.SUCCESS);
        RuntimeException observerException = new RuntimeException("Observer connection failed");

        when(pageFetcher.fetch(TEST_URI)).thenReturn(successPageData);
        when(linkExtractor.extractLinks(HTML_CONTENT, TEST_URI)).thenReturn(discoveredLinks);
        when(crawlStateService.processDiscoveredLinks(discoveredLinks)).thenReturn(enqueuedLinks);
        doThrow(observerException).when(crawlObserver).onPageCrawled(TEST_URI, discoveredLinks);

        // When
        pageProcessingService.processPage(TEST_URI);

        // Then
        verify(pageFetcher).fetch(TEST_URI);
        verify(linkExtractor).extractLinks(HTML_CONTENT, TEST_URI);
        verify(crawlStateService).processDiscoveredLinks(discoveredLinks);
        verify(crawlObserver).onPageCrawled(TEST_URI, discoveredLinks);

        // The exception should be caught and reported as unexpected error
        ArgumentCaptor<Throwable> exceptionCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(crawlObserver).onCrawlFailed(eq(TEST_URI), eq("UNEXPECTED_ERROR"), exceptionCaptor.capture());
        assertEquals(observerException, exceptionCaptor.getValue());
    }
}
