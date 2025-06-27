package com.monzo.crawler.domain.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UriProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(UriProcessingService.class);

    private final String allowedDomain;

    public UriProcessingService(String allowedDomain) {
        this.allowedDomain = Objects.requireNonNull(allowedDomain);
    }

    /**
     * Normalizes a URI by ensuring consistent path formatting
     */
    public URI normalizeUri(URI uri) {
        try {
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    path,
                    uri.getQuery(),
                    null // Remove fragment
            );
        } catch (URISyntaxException e) {
            logger.warn("Failed to normalize URI: {}. Returning original.", uri, e);
            return uri;
        }
    }

    /**
     * Checks if a URI belongs to the allowed domain
     */
    public boolean isSameDomain(URI uri) {
        return uri.getHost() != null && allowedDomain.equalsIgnoreCase(uri.getHost());
    }

    /**
     * Validates if a URI is suitable for crawling based on domain rules
     */
    public boolean isValidForCrawling(URI uri) {
        return uri != null && isSameDomain(uri);
    }
}
