package com.monzo.crawler.config;

import com.monzo.crawler.application.WebCrawlerUseCase;
import com.monzo.crawler.domain.service.CrawlStateService;
import com.monzo.crawler.domain.service.PageProcessingService;
import com.monzo.crawler.domain.service.UriProcessingService;
import com.monzo.crawler.domain.port.out.*;
import java.net.URI;
import java.util.Objects;

public class WebCrawlerFactory {

    private final PageFetcher pageFetcher;
    private final LinkExtractor linkExtractor;
    private final CrawlObserver crawlObserver;
    private final FrontierQueue frontierQueue;
    private final VisitedRepository visitedRepository;
    private final int maxConcurrentRequests;

    public WebCrawlerFactory(
            PageFetcher pageFetcher,
            LinkExtractor linkExtractor,
            CrawlObserver crawlObserver,
            FrontierQueue frontierQueue,
            VisitedRepository visitedRepository,
            int maxConcurrentRequests
    ) {
        this.pageFetcher = Objects.requireNonNull(pageFetcher);
        this.linkExtractor = Objects.requireNonNull(linkExtractor);
        this.crawlObserver = Objects.requireNonNull(crawlObserver);
        this.frontierQueue = Objects.requireNonNull(frontierQueue);
        this.visitedRepository = Objects.requireNonNull(visitedRepository);
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    /**
     * Creates a WebCrawler configured for the given start URI's domain
     */
    public WebCrawlerUseCase createForUri(URI startUri) {
        String domain = extractDomain(startUri);

        // Create domain services with the runtime domain
        UriProcessingService uriProcessingService = new UriProcessingService(domain);

        CrawlStateService crawlStateService = new CrawlStateService(
                frontierQueue,
                visitedRepository,
                uriProcessingService
        );

        PageProcessingService pageProcessingService = new PageProcessingService(
                pageFetcher,
                linkExtractor,
                crawlObserver,
                crawlStateService
        );

        return new WebCrawlerUseCase(
                pageProcessingService,
                crawlStateService,
                maxConcurrentRequests
        );
    }

    private String extractDomain(URI uri) {
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid URI - no host found: " + uri);
        }
        return uri.getHost();
    }
}
