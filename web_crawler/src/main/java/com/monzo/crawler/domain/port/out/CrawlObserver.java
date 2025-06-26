package com.monzo.crawler.domain.port.out;

import java.net.URI;
import java.util.Set;

public interface CrawlObserver {
    /**
     * Called when a page has been successfully crawled.
     *
     * @param pageUri The URI of the page that was crawled.
     * @param links   The set of valid, domain-specific links found on the page.
     */
    void onPageCrawled(URI pageUri, Set<URI> links);

    /**
     * Called when an attempt to crawl a page fails.
     *
     * @param pageUri The URI that failed to be crawled.
     * @param reason  A description of why the crawl failed.
     * @param error   The associated exception, if any.
     */
    void onCrawlFailed(URI pageUri, String reason, Throwable error);
}
