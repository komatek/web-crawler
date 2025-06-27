package com.monzo.crawler.domain.service;

import com.monzo.crawler.domain.port.out.FrontierQueue;
import com.monzo.crawler.domain.port.out.VisitedRepository;
import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class CrawlStateService {

    private final FrontierQueue frontierQueue;
    private final VisitedRepository visitedRepository;
    private final UriProcessingService uriProcessingService;

    public CrawlStateService(
            FrontierQueue frontierQueue,
            VisitedRepository visitedRepository,
            UriProcessingService uriProcessingService
    ) {
        this.frontierQueue = Objects.requireNonNull(frontierQueue);
        this.visitedRepository = Objects.requireNonNull(visitedRepository);
        this.uriProcessingService = Objects.requireNonNull(uriProcessingService);
    }

    /**
     * Adds a URI to the frontier if it hasn't been visited and is valid for crawling
     */
    public void tryAddToFrontier(URI uri) {
        URI normalizedUri = uriProcessingService.normalizeUri(uri);

        if (uriProcessingService.isValidForCrawling(normalizedUri) &&
                !visitedRepository.isVisited(normalizedUri)) {
            frontierQueue.enqueue(normalizedUri);
        }
    }

    /**
     * Processes a set of discovered links, filtering and adding valid ones to frontier
     */
    public Set<URI> processDiscoveredLinks(Set<URI> discoveredLinks) {
        return discoveredLinks.stream()
                .map(uriProcessingService::normalizeUri)
                .filter(uriProcessingService::isValidForCrawling)
                .filter(uri -> !visitedRepository.isVisited(uri))
                .peek(frontierQueue::enqueue)
                .collect(Collectors.toSet());
    }

    /**
     * Attempts to mark a URI as visited
     */
    public boolean markAsVisited(URI uri) {
        return visitedRepository.markVisited(uri);
    }

    /**
     * Gets the next URI to crawl from the frontier
     */
    public URI getNextUri() {
        return frontierQueue.dequeue();
    }

    /**
     * Checks if the crawl frontier is empty
     */
    public boolean isFrontierEmpty() {
        return frontierQueue.isEmpty();
    }
}
