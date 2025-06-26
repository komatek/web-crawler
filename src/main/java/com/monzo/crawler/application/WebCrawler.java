package com.monzo.crawler.application;

import java.net.URI;

/**
 * Interface representing a web crawler.
 *
 * This interface defines the contract for a web crawler implementation,
 * which is responsible for crawling web pages starting from a given URI.
 */
public interface WebCrawler {

    /**
     * Initiates the web crawling process starting from the specified URI.
     *
     * @param startUri the starting URI for the web crawling process
     *                 (must not be null).
     */
    void crawl(URI startUri);
}
