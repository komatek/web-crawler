package com.monzo.crawler.domain.model;

/**
 * A record to transport page data between the fetcher adapter and the core logic.
 * This decouples the core from HTTP-specific details like status codes.
 *
 * @param htmlContent The HTML content of the page. Null if fetch was not successful.
 * @param status      The outcome of the fetch operation.
 */
public record PageData(String htmlContent, Status status) {
    public enum Status {
        SUCCESS,
        NOT_FOUND,
        CLIENT_ERROR, // Other 4xx errors
        SERVER_ERROR, // 5xx errors
        FETCH_ERROR,  // Network errors, timeouts, etc.
    }
}
