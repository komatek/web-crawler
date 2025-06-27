package com.monzo.crawler.domain.service;

import com.monzo.crawler.domain.port.out.FrontierQueue;
import com.monzo.crawler.domain.port.out.VisitedRepository;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CrawlStateServiceTest {

    private final FrontierQueue frontierQueue = mock(FrontierQueue.class);
    private final VisitedRepository visitedRepository = mock(VisitedRepository.class);
    private final UriProcessingService uriProcessingService = mock(UriProcessingService.class);

    private final CrawlStateService crawlStateService = new CrawlStateService(
            frontierQueue,
            visitedRepository,
            uriProcessingService
    );

    private static final URI TEST_URI = URI.create("https://example.com/page");
    private static final URI NORMALIZED_URI = URI.create("https://example.com/page");
    private static final URI EXTERNAL_URI = URI.create("https://external.com/page");
    private static final URI VISITED_URI = URI.create("https://example.com/visited");

    @Test
    void constructorShouldThrowNullPointerExceptionWhenFrontierQueueIsNull() {
        assertThrows(NullPointerException.class, () ->
                new CrawlStateService(null, visitedRepository, uriProcessingService)
        );
    }

    @Test
    void constructorShouldThrowNullPointerExceptionWhenVisitedRepositoryIsNull() {
        assertThrows(NullPointerException.class, () ->
                new CrawlStateService(frontierQueue, null, uriProcessingService)
        );
    }

    @Test
    void constructorShouldThrowNullPointerExceptionWhenUriProcessingServiceIsNull() {
        assertThrows(NullPointerException.class, () ->
                new CrawlStateService(frontierQueue, visitedRepository, null)
        );
    }

    @Test
    void tryAddToFrontierShouldAddToQueueWhenUriIsValidAndNotVisited() {
        // Given
        when(uriProcessingService.normalizeUri(TEST_URI)).thenReturn(NORMALIZED_URI);
        when(uriProcessingService.isValidForCrawling(NORMALIZED_URI)).thenReturn(true);
        when(visitedRepository.isVisited(NORMALIZED_URI)).thenReturn(false);

        // When
        crawlStateService.tryAddToFrontier(TEST_URI);

        // Then
        verify(uriProcessingService).normalizeUri(TEST_URI);
        verify(uriProcessingService).isValidForCrawling(NORMALIZED_URI);
        verify(visitedRepository).isVisited(NORMALIZED_URI);
        verify(frontierQueue).enqueue(NORMALIZED_URI);
    }

    @Test
    void tryAddToFrontierShouldNotAddToQueueWhenUriIsNotValidForCrawling() {
        // Given
        when(uriProcessingService.normalizeUri(EXTERNAL_URI)).thenReturn(EXTERNAL_URI);
        when(uriProcessingService.isValidForCrawling(EXTERNAL_URI)).thenReturn(false);

        // When
        crawlStateService.tryAddToFrontier(EXTERNAL_URI);

        // Then
        verify(uriProcessingService).normalizeUri(EXTERNAL_URI);
        verify(uriProcessingService).isValidForCrawling(EXTERNAL_URI);
        verify(visitedRepository, never()).isVisited(any());
        verify(frontierQueue, never()).enqueue(any());
    }

    @Test
    void tryAddToFrontierShouldNotAddToQueueWhenUriIsAlreadyVisited() {
        // Given
        when(uriProcessingService.normalizeUri(VISITED_URI)).thenReturn(VISITED_URI);
        when(uriProcessingService.isValidForCrawling(VISITED_URI)).thenReturn(true);
        when(visitedRepository.isVisited(VISITED_URI)).thenReturn(true);

        // When
        crawlStateService.tryAddToFrontier(VISITED_URI);

        // Then
        verify(uriProcessingService).normalizeUri(VISITED_URI);
        verify(uriProcessingService).isValidForCrawling(VISITED_URI);
        verify(visitedRepository).isVisited(VISITED_URI);
        verify(frontierQueue, never()).enqueue(any());
    }

    @Test
    void processDiscoveredLinksShouldReturnEmptySetWhenNoLinksProvided() {
        // Given
        Set<URI> emptyLinks = Set.of();

        // When
        Set<URI> result = crawlStateService.processDiscoveredLinks(emptyLinks);

        // Then
        assertTrue(result.isEmpty());
        verify(uriProcessingService, never()).normalizeUri(any());
        verify(frontierQueue, never()).enqueue(any());
    }

    @Test
    void processDiscoveredLinksShouldProcessValidUnvisitedLinks() {
        // Given
        URI link1 = URI.create("https://example.com/page1");
        URI link2 = URI.create("https://example.com/page2");
        URI normalizedLink1 = URI.create("https://example.com/page1");
        URI normalizedLink2 = URI.create("https://example.com/page2");

        Set<URI> discoveredLinks = Set.of(link1, link2);

        when(uriProcessingService.normalizeUri(link1)).thenReturn(normalizedLink1);
        when(uriProcessingService.normalizeUri(link2)).thenReturn(normalizedLink2);
        when(uriProcessingService.isValidForCrawling(normalizedLink1)).thenReturn(true);
        when(uriProcessingService.isValidForCrawling(normalizedLink2)).thenReturn(true);
        when(visitedRepository.isVisited(normalizedLink1)).thenReturn(false);
        when(visitedRepository.isVisited(normalizedLink2)).thenReturn(false);

        // When
        Set<URI> result = crawlStateService.processDiscoveredLinks(discoveredLinks);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.contains(normalizedLink1));
        assertTrue(result.contains(normalizedLink2));
        verify(frontierQueue).enqueue(normalizedLink1);
        verify(frontierQueue).enqueue(normalizedLink2);
    }

    @Test
    void processDiscoveredLinksShouldFilterOutInvalidLinks() {
        // Given
        URI validLink = URI.create("https://example.com/page1");
        URI invalidLink = URI.create("https://external.com/page2");
        URI normalizedValidLink = URI.create("https://example.com/page1");
        URI normalizedInvalidLink = URI.create("https://external.com/page2");

        Set<URI> discoveredLinks = Set.of(validLink, invalidLink);

        when(uriProcessingService.normalizeUri(validLink)).thenReturn(normalizedValidLink);
        when(uriProcessingService.normalizeUri(invalidLink)).thenReturn(normalizedInvalidLink);
        when(uriProcessingService.isValidForCrawling(normalizedValidLink)).thenReturn(true);
        when(uriProcessingService.isValidForCrawling(normalizedInvalidLink)).thenReturn(false);
        when(visitedRepository.isVisited(normalizedValidLink)).thenReturn(false);

        // When
        Set<URI> result = crawlStateService.processDiscoveredLinks(discoveredLinks);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.contains(normalizedValidLink));
        assertFalse(result.contains(normalizedInvalidLink));
        verify(frontierQueue).enqueue(normalizedValidLink);
        verify(frontierQueue, never()).enqueue(normalizedInvalidLink);
    }

    @Test
    void processDiscoveredLinksShouldFilterOutVisitedLinks() {
        // Given
        URI unvisitedLink = URI.create("https://example.com/page1");
        URI visitedLink = URI.create("https://example.com/page2");
        URI normalizedUnvisitedLink = URI.create("https://example.com/page1");
        URI normalizedVisitedLink = URI.create("https://example.com/page2");

        Set<URI> discoveredLinks = Set.of(unvisitedLink, visitedLink);

        when(uriProcessingService.normalizeUri(unvisitedLink)).thenReturn(normalizedUnvisitedLink);
        when(uriProcessingService.normalizeUri(visitedLink)).thenReturn(normalizedVisitedLink);
        when(uriProcessingService.isValidForCrawling(normalizedUnvisitedLink)).thenReturn(true);
        when(uriProcessingService.isValidForCrawling(normalizedVisitedLink)).thenReturn(true);
        when(visitedRepository.isVisited(normalizedUnvisitedLink)).thenReturn(false);
        when(visitedRepository.isVisited(normalizedVisitedLink)).thenReturn(true);

        // When
        Set<URI> result = crawlStateService.processDiscoveredLinks(discoveredLinks);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.contains(normalizedUnvisitedLink));
        assertFalse(result.contains(normalizedVisitedLink));
        verify(frontierQueue).enqueue(normalizedUnvisitedLink);
        verify(frontierQueue, never()).enqueue(normalizedVisitedLink);
    }

    @Test
    void processDiscoveredLinksShouldHandleMixedScenario() {
        // Given
        URI validUnvisitedLink = URI.create("https://example.com/page1");
        URI validVisitedLink = URI.create("https://example.com/page2");
        URI invalidLink = URI.create("https://external.com/page3");

        Set<URI> discoveredLinks = Set.of(validUnvisitedLink, validVisitedLink, invalidLink);

        // Setup mocks for valid unvisited link
        when(uriProcessingService.normalizeUri(validUnvisitedLink)).thenReturn(validUnvisitedLink);
        when(uriProcessingService.isValidForCrawling(validUnvisitedLink)).thenReturn(true);
        when(visitedRepository.isVisited(validUnvisitedLink)).thenReturn(false);

        // Setup mocks for valid visited link
        when(uriProcessingService.normalizeUri(validVisitedLink)).thenReturn(validVisitedLink);
        when(uriProcessingService.isValidForCrawling(validVisitedLink)).thenReturn(true);
        when(visitedRepository.isVisited(validVisitedLink)).thenReturn(true);

        // Setup mocks for invalid link
        when(uriProcessingService.normalizeUri(invalidLink)).thenReturn(invalidLink);
        when(uriProcessingService.isValidForCrawling(invalidLink)).thenReturn(false);

        // When
        Set<URI> result = crawlStateService.processDiscoveredLinks(discoveredLinks);

        // Then
        assertEquals(1, result.size());
        assertTrue(result.contains(validUnvisitedLink));
        verify(frontierQueue).enqueue(validUnvisitedLink);
        verify(frontierQueue, never()).enqueue(validVisitedLink);
        verify(frontierQueue, never()).enqueue(invalidLink);
    }

    @Test
    void markAsVisitedShouldDelegateToVisitedRepository() {
        // Given
        when(visitedRepository.markVisited(TEST_URI)).thenReturn(true);

        // When
        boolean result = crawlStateService.markAsVisited(TEST_URI);

        // Then
        assertTrue(result);
        verify(visitedRepository).markVisited(TEST_URI);
    }

    @Test
    void markAsVisitedShouldReturnFalseWhenAlreadyVisited() {
        // Given
        when(visitedRepository.markVisited(TEST_URI)).thenReturn(false);

        // When
        boolean result = crawlStateService.markAsVisited(TEST_URI);

        // Then
        assertFalse(result);
        verify(visitedRepository).markVisited(TEST_URI);
    }

    @Test
    void getNextUriShouldDelegateToFrontierQueue() {
        // Given
        when(frontierQueue.dequeue()).thenReturn(TEST_URI);

        // When
        URI result = crawlStateService.getNextUri();

        // Then
        assertEquals(TEST_URI, result);
        verify(frontierQueue).dequeue();
    }

    @Test
    void getNextUriShouldReturnNullWhenQueueIsEmpty() {
        // Given
        when(frontierQueue.dequeue()).thenReturn(null);

        // When
        URI result = crawlStateService.getNextUri();

        // Then
        assertNull(result);
        verify(frontierQueue).dequeue();
    }

    @Test
    void isFrontierEmptyShouldDelegateToFrontierQueue() {
        // Given
        when(frontierQueue.isEmpty()).thenReturn(true);

        // When
        boolean result = crawlStateService.isFrontierEmpty();

        // Then
        assertTrue(result);
        verify(frontierQueue).isEmpty();
    }

    @Test
    void isFrontierEmptyShouldReturnFalseWhenQueueHasElements() {
        // Given
        when(frontierQueue.isEmpty()).thenReturn(false);

        // When
        boolean result = crawlStateService.isFrontierEmpty();

        // Then
        assertFalse(result);
        verify(frontierQueue).isEmpty();
    }

    @Test
    void processDiscoveredLinksShouldMaintainOrderIndependence() {
        // Given - Test that the result is order-independent
        URI link1 = URI.create("https://example.com/page1");
        URI link2 = URI.create("https://example.com/page2");

        Set<URI> discoveredLinks1 = Set.of(link1, link2);
        Set<URI> discoveredLinks2 = Set.of(link2, link1); // Different order

        when(uriProcessingService.normalizeUri(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(uriProcessingService.isValidForCrawling(any())).thenReturn(true);
        when(visitedRepository.isVisited(any())).thenReturn(false);

        // When
        Set<URI> result1 = crawlStateService.processDiscoveredLinks(discoveredLinks1);
        Set<URI> result2 = crawlStateService.processDiscoveredLinks(discoveredLinks2);

        // Then
        assertEquals(result1, result2);
        assertEquals(2, result1.size());
        assertEquals(2, result2.size());
    }
}
