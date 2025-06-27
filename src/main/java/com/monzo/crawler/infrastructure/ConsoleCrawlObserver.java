package com.monzo.crawler.infrastructure;

import com.google.common.annotations.VisibleForTesting;
import com.monzo.crawler.domain.port.out.CrawlObserver;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

public class ConsoleCrawlObserver implements CrawlObserver {
    private final Logger logger;
    private final Set<URI> allSeenLinks = ConcurrentHashMap.newKeySet();

    // Default constructor for production
    public ConsoleCrawlObserver() {
        this(LoggerFactory.getLogger(ConsoleCrawlObserver.class));
    }

    @VisibleForTesting
    ConsoleCrawlObserver(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void onPageCrawled(URI pageUri, Set<URI> links) {
        Set<URI> newLinks = links.stream()
                .filter(allSeenLinks::add)
                .collect(Collectors.toSet());

        StringBuilder sb = new StringBuilder();
        sb.append("\n--------------------------------------------------\n");
        sb.append("Crawled Page: ").append(pageUri).append("\n");
        sb.append("Found ").append(links.size()).append(" total links");

        if (newLinks.isEmpty()) {
            sb.append(" (all already seen)\n");
        } else {
            sb.append(" (").append(newLinks.size()).append(" new):\n");
            newLinks.stream()
                    .map(URI::toString)
                    .sorted()
                    .forEach(link -> sb.append("  -> ").append(link).append("\n"));
        }

        sb.append("--------------------------------------------------");
        logger.info(sb.toString());
    }

    @Override
    public void onCrawlFailed(URI pageUri, String reason, Throwable error) {
        if (error != null) {
            logger.warn("Failed to crawl {}: Reason: {}. Error: {}", pageUri, reason, error.getMessage());
        } else {
            logger.warn("Failed to crawl {}: Reason: {}", pageUri, reason);
        }
    }
}
