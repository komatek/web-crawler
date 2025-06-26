package com.monzo.crawler.domain.port.out;

import java.net.URI;
import java.util.Set;

public interface LinkExtractor {
    /**
     * Extracts all hyperlinks from a given HTML content string.
     *
     * @param htmlContent The HTML content of the page.
     * @param baseUrl     The base URL of the page, used to resolve relative links.
     * @return A Set of absolute URLs found on the page.
     */
    Set<URI> extractLinks(String htmlContent, URI baseUrl);
}
