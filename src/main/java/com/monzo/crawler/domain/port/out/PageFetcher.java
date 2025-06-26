package com.monzo.crawler.domain.port.out;

import com.monzo.crawler.domain.model.PageData;
import java.net.URI;

public interface PageFetcher {
    /**
     * Fetches the content of a given URI.
     *
     * @param uri The URI of the page to fetch.
     * @return PageData containing the status and HTML content if successful.
     */
    PageData fetch(URI uri);
}

