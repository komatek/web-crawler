package com.monzo.crawler.infrastructure;

import com.google.common.annotations.VisibleForTesting;
import com.monzo.crawler.domain.model.PageData;
import com.monzo.crawler.domain.port.out.PageFetcher;
import com.monzo.crawler.infrastructure.config.ConfigurationLoader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpClientPageFetcher implements PageFetcher {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientPageFetcher.class);
    private final HttpClient httpClient;
    private final String userAgent;
    private final Duration requestTimeout;

    public HttpClientPageFetcher(Duration timeout) {
        this(timeout, new ConfigurationLoader());
    }

    @VisibleForTesting
    HttpClientPageFetcher(Duration timeout, ConfigurationLoader config) {
        this.requestTimeout = timeout;
        this.userAgent = config.getUserAgent();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(timeout)
                .build();
    }

    @Override
    public PageData fetch(URI uri) {
        if (!isHttpOrHttps(uri)) {
            return new PageData(null, PageData.Status.CLIENT_ERROR);
        }

        HttpRequest request = createRequest(uri);

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return handleResponse(response);
        } catch (Exception e) {
            logger.error("Error fetching URI {}: {}", uri, e.getMessage());
            return new PageData(null, PageData.Status.FETCH_ERROR);
        }
    }

    private boolean isHttpOrHttps(URI uri) {
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private HttpRequest createRequest(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(requestTimeout)
                .header("User-Agent", userAgent)
                .GET()
                .build();
    }

    private PageData handleResponse(HttpResponse<String> response) {
        int statusCode = response.statusCode();
        String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();

        if (statusCode >= 200 && statusCode < 300) {
            return contentType.contains("text/html")
                    ? new PageData(response.body(), PageData.Status.SUCCESS)
                    : new PageData(null, PageData.Status.CLIENT_ERROR);
        }

        if (statusCode == 404) {
            return new PageData(null, PageData.Status.NOT_FOUND);
        }

        return statusCode >= 400 && statusCode < 500
                ? new PageData(null, PageData.Status.CLIENT_ERROR)
                : new PageData(null, PageData.Status.SERVER_ERROR);
    }
}
