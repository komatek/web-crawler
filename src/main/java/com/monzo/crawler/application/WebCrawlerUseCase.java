package com.monzo.crawler.application;

import com.monzo.crawler.domain.port.out.*;
import com.monzo.crawler.domain.model.PageData;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebCrawlerUseCase implements WebCrawler {

    private static final Logger logger = LoggerFactory.getLogger(WebCrawlerUseCase.class);

    private final PageFetcher pageFetcher;
    private final LinkExtractor linkExtractor;
    private final CrawlObserver crawlObserver;
    private final FrontierQueue frontierQueue;
    private final VisitedRepository visitedRepository;
    private final String domain;

    private final Phaser phaser = new Phaser(1);
    private final Semaphore rateLimiter;

    public WebCrawlerUseCase(
            PageFetcher pageFetcher,
            LinkExtractor linkExtractor,
            CrawlObserver crawlObserver,
            FrontierQueue frontierQueue,
            VisitedRepository visitedRepository,
            String domain,
            int maxConcurrentRequests
    ) {
        this.pageFetcher = Objects.requireNonNull(pageFetcher);
        this.linkExtractor = Objects.requireNonNull(linkExtractor);
        this.crawlObserver = Objects.requireNonNull(crawlObserver);
        this.frontierQueue = Objects.requireNonNull(frontierQueue);
        this.visitedRepository = Objects.requireNonNull(visitedRepository);
        this.domain = Objects.requireNonNull(domain);
        this.rateLimiter = new Semaphore(maxConcurrentRequests);
    }

    @Override
    public void crawl(URI startUri) {
        frontierQueue.enqueue(normalizeUri(startUri));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            while (true) {
                URI currentUri = frontierQueue.dequeue();

                if (currentUri != null) {
                    if (visitedRepository.markVisited(currentUri)) {
                        phaser.register(); // Create a new party for the task
                        executor.submit(() -> processUri(currentUri));
                    }
                } else {
                    // The frontier is empty. We must wait for running tasks to complete.
                    // This call correctly signals this thread's arrival and waits for others.
                    phaser.arriveAndAwaitAdvance();

                    // After waiting, if the frontier is still empty and only the main thread
                    // is left, the crawl is complete.
                    if (frontierQueue.isEmpty() && phaser.getRegisteredParties() == 1) {
                        break;
                    }
                }
            }
        } finally {
            // Final arrival of the main thread to terminate the phaser
            phaser.arriveAndDeregister();
        }
    }

    private void processUri(URI uri) {
        try {
            rateLimiter.acquire();

            logger.debug("Processing URI: {}", uri);
            PageData pageData = pageFetcher.fetch(uri);

            if (pageData.status() == PageData.Status.SUCCESS) {
                Set<URI> foundLinks = linkExtractor.extractLinks(pageData.htmlContent(), uri);
                crawlObserver.onPageCrawled(uri, foundLinks);

                // Now we only need to check for same domain and if not already visited
                // since the LinkExtractor already filtered out non-HTTP(S) and static files
                foundLinks.stream()
                        .map(this::normalizeUri)
                        .filter(this::isCrawlable)
                        .forEach(frontierQueue::enqueue);

            } else {
                crawlObserver.onCrawlFailed(uri, pageData.status().toString(), null);
            }
        } catch (InterruptedException e) {
            logger.warn("Task for URI {} was interrupted.", uri);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Unrecoverable error processing URI: {}", uri, e);
            crawlObserver.onCrawlFailed(uri, "UNEXPECTED_ERROR", e);
        } finally {
            rateLimiter.release();
            phaser.arriveAndDeregister(); // Signal task completion
        }
    }

    private boolean isCrawlable(URI uri) {
        return uri != null &&
                isSameDomain(uri) &&
                !visitedRepository.isVisited(uri);
    }

    private boolean isSameDomain(URI uri) {
        return uri.getHost() != null && domain.equalsIgnoreCase(uri.getHost());
    }

    private URI normalizeUri(URI uri) {
        try {
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), path, uri.getQuery(), null);
        } catch (URISyntaxException e) {
            logger.warn("Failed to normalize URI: {}. Returning original.", uri, e);
            return uri;
        }
    }
}
