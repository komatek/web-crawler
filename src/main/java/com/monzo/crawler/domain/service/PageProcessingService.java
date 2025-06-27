package com.monzo.crawler.domain.service;

import com.monzo.crawler.domain.model.PageData;
import com.monzo.crawler.domain.port.out.CrawlObserver;
import com.monzo.crawler.domain.port.out.LinkExtractor;
import com.monzo.crawler.domain.port.out.PageFetcher;
import java.net.URI;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PageProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(PageProcessingService.class);

    private final PageFetcher pageFetcher;
    private final LinkExtractor linkExtractor;
    private final CrawlObserver crawlObserver;
    private final CrawlStateService crawlStateService;

    public PageProcessingService(
            PageFetcher pageFetcher,
            LinkExtractor linkExtractor,
            CrawlObserver crawlObserver,
            CrawlStateService crawlStateService
    ) {
        this.pageFetcher = Objects.requireNonNull(pageFetcher);
        this.linkExtractor = Objects.requireNonNull(linkExtractor);
        this.crawlObserver = Objects.requireNonNull(crawlObserver);
        this.crawlStateService = Objects.requireNonNull(crawlStateService);
    }

    /**
     * Processes a single page: fetches content, extracts links, and handles results
     */
    public void processPage(URI uri) {
        logger.debug("Processing page: {}", uri);

        try {
            PageData pageData = pageFetcher.fetch(uri);

            if (pageData.status() == PageData.Status.SUCCESS) {
                handleSuccessfulPage(uri, pageData);
            } else {
                handleFailedPage(uri, pageData);
            }
        } catch (Exception e) {
            logger.error("Unexpected error processing page: {}", uri, e);
            crawlObserver.onCrawlFailed(uri, "UNEXPECTED_ERROR", e);
        }
    }

    private void handleSuccessfulPage(URI uri, PageData pageData) {
        Set<URI> discoveredLinks = linkExtractor.extractLinks(pageData.htmlContent(), uri);

        // Process discovered links through crawl state service
        Set<URI> enqueuedLinks = crawlStateService.processDiscoveredLinks(discoveredLinks);

        // Notify observer with all discovered links (not just enqueued ones)
        crawlObserver.onPageCrawled(uri, discoveredLinks);

        logger.debug("Page {} processed successfully. Found {} links, enqueued {} new ones",
                uri, discoveredLinks.size(), enqueuedLinks.size());
    }

    private void handleFailedPage(URI uri, PageData pageData) {
        crawlObserver.onCrawlFailed(uri, pageData.status().toString(), null);
        logger.debug("Failed to process page: {} - Status: {}", uri, pageData.status());
    }
}
