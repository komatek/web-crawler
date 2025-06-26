package com.monzo.crawler.infrastructure;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.monzo.crawler.domain.model.PageData;
import com.monzo.crawler.infrastructure.config.TestWireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class HttpClientPageFetcherIntegrationTest {

    private WireMockServer wireMockServer;
    private HttpClientPageFetcher pageFetcher;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        // Use the utility to create and start WireMock server
        wireMockServer = TestWireMockConfiguration.createWireMockServer();
        baseUrl = TestWireMockConfiguration.getBaseUrl(wireMockServer);

        // Set up basic HTML stubs for testing
        TestWireMockConfiguration.setupBasicHtmlStubs(wireMockServer);

        pageFetcher = new HttpClientPageFetcher(Duration.ofSeconds(5));
    }

    @AfterEach
    void tearDown() {
        TestWireMockConfiguration.stopServer(wireMockServer);
    }

    @Test
    void shouldFetchHtmlPageSuccessfully() {
        // Given
        URI uri = URI.create(baseUrl + "/test-page");

        // When
        PageData result = pageFetcher.fetch(uri);

        // Then
        assertThat(result.status()).isEqualTo(PageData.Status.SUCCESS);
        assertThat(result.htmlContent()).isEqualTo("<html><body><h1>Test Page</h1></body></html>");

        // Verify the request was made
        wireMockServer.verify(getRequestedFor(urlEqualTo("/test-page")));
    }

    @Test
    void shouldHandle404NotFound() {
        // Given
        URI uri = URI.create(baseUrl + "/not-found");

        // When
        PageData result = pageFetcher.fetch(uri);

        // Then
        assertThat(result.status()).isEqualTo(PageData.Status.NOT_FOUND);
        assertThat(result.htmlContent()).isNull();
    }

    @Test
    void shouldHandle500ServerError() {
        // Given
        URI uri = URI.create(baseUrl + "/server-error");

        // When
        PageData result = pageFetcher.fetch(uri);

        // Then
        assertThat(result.status()).isEqualTo(PageData.Status.SERVER_ERROR);
        assertThat(result.htmlContent()).isNull();
    }

    @Test
    void shouldHandle400ClientError() {
        // Given
        URI uri = URI.create(baseUrl + "/bad-request");

        // When
        PageData result = pageFetcher.fetch(uri);

        // Then
        assertThat(result.status()).isEqualTo(PageData.Status.CLIENT_ERROR);
        assertThat(result.htmlContent()).isNull();
    }

    @Test
    void shouldRejectNonHtmlContent() {
        // Given
        URI uri = URI.create(baseUrl + "/json-content");

        // When
        PageData result = pageFetcher.fetch(uri);

        // Then
        assertThat(result.status()).isEqualTo(PageData.Status.CLIENT_ERROR);
        assertThat(result.htmlContent()).isNull();
    }

    @Test
    void shouldHandleRedirects() {
        // Given
        URI uri = URI.create(baseUrl + "/redirect");

        // When
        PageData result = pageFetcher.fetch(uri);

        // Then
        assertThat(result.status()).isEqualTo(PageData.Status.SUCCESS);
        assertThat(result.htmlContent()).isEqualTo("<html><body>Final page</body></html>");

        wireMockServer.verify(getRequestedFor(urlEqualTo("/redirect")));
        wireMockServer.verify(getRequestedFor(urlEqualTo("/final-destination")));
    }

    @Test
    void shouldRejectNonHttpSchemes() {
        // Given
        URI ftpUri = URI.create("ftp://example.com/file.txt");

        // When
        PageData result = pageFetcher.fetch(ftpUri);

        // Then
        assertThat(result.status()).isEqualTo(PageData.Status.CLIENT_ERROR);
        assertThat(result.htmlContent()).isNull();
    }

    @Test
    void shouldHandleConnectionFailure() {
        // Given - use a port that doesn't exist
        URI uri = URI.create("http://localhost:99999/page");

        // When
        PageData result = pageFetcher.fetch(uri);

        // Then
        assertThat(result.status()).isEqualTo(PageData.Status.FETCH_ERROR);
        assertThat(result.htmlContent()).isNull();
    }

    @Test
    void shouldHandleMixedCaseContentType() {
        // Given
        URI uri = URI.create(baseUrl + "/mixed-case");

        // When
        PageData result = pageFetcher.fetch(uri);

        // Then
        assertThat(result.status()).isEqualTo(PageData.Status.SUCCESS);
        assertThat(result.htmlContent()).isEqualTo("<html><body>Mixed case content type</body></html>");
    }
}
