package com.monzo.crawler.application;

import com.monzo.crawler.domain.service.CrawlStateService;
import com.monzo.crawler.domain.service.PageProcessingService;
import com.monzo.crawler.domain.service.UriProcessingService;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebCrawlerUseCase implements WebCrawler {

    private static final Logger logger = LoggerFactory.getLogger(WebCrawlerUseCase.class);

    private final PageProcessingService pageProcessingService;
    private final CrawlStateService crawlStateService;

    private final Phaser phaser = new Phaser(1);
    private final Semaphore rateLimiter;

    public WebCrawlerUseCase(
            PageProcessingService pageProcessingService,
            CrawlStateService crawlStateService,
            int maxConcurrentRequests
    ) {
        this.pageProcessingService = Objects.requireNonNull(pageProcessingService);
        this.crawlStateService = Objects.requireNonNull(crawlStateService);
        this.rateLimiter = new Semaphore(maxConcurrentRequests);
    }

    @Override
    public void crawl(URI startUri) {
        crawlStateService.tryAddToFrontier(startUri);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            while (true) {
                URI currentUri = crawlStateService.getNextUri();

                if (currentUri != null) {
                    if (crawlStateService.markAsVisited(currentUri)) {
                        phaser.register();
                        executor.submit(() -> processUriWithRateLimit(currentUri));
                    }
                } else {
                    // Wait for running tasks to complete
                    phaser.arriveAndAwaitAdvance();

                    // Check if crawl is complete
                    if (crawlStateService.isFrontierEmpty() && phaser.getRegisteredParties() == 1) {
                        break;
                    }
                }
            }
        } finally {
            phaser.arriveAndDeregister();
        }
    }

    private void processUriWithRateLimit(URI uri) {
        try {
            rateLimiter.acquire();
            pageProcessingService.processPage(uri);
        } catch (InterruptedException e) {
            logger.warn("Task for URI {} was interrupted.", uri);
            Thread.currentThread().interrupt();
        } finally {
            rateLimiter.release();
            phaser.arriveAndDeregister();
        }
    }
}
