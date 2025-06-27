package com.monzo.crawler.infrastructure;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.monzo.crawler.domain.model.PageData;
import com.monzo.crawler.infrastructure.config.TestWireMockConfiguration;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class HttpClientPageFetcherIntegrationTest {

    private static WireMockServer wireMockServer;
    private static String baseUrl;

    private HttpClientPageFetcher pageFetcher;

    @BeforeAll
    static void setUpClass() {
        wireMockServer = TestWireMockConfiguration.createWireMockServer();
        baseUrl = TestWireMockConfiguration.getBaseUrl(wireMockServer);
    }

    @AfterAll
    static void tearDownClass() {
        TestWireMockConfiguration.stopServer(wireMockServer);
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();

        TestWireMockConfiguration.setupBasicHtmlStubs(wireMockServer);

        pageFetcher = new HttpClientPageFetcher(Duration.ofSeconds(5));
    }

    @Test
    void shouldFetchHtmlPageSuccessfully() {
        URI uri = URI.create(baseUrl + "/test-page");

        PageData result = pageFetcher.fetch(uri);

        assertThat(result.status()).isEqualTo(PageData.Status.SUCCESS);
        assertThat(result.htmlContent()).isEqualTo("<html><body><h1>Test Page</h1></body></html>");

        wireMockServer.verify(getRequestedFor(urlEqualTo("/test-page")));
    }

    @Test
    void shouldHandle404NotFound() {
        URI uri = URI.create(baseUrl + "/not-found");

        PageData result = pageFetcher.fetch(uri);

        assertThat(result.status()).isEqualTo(PageData.Status.NOT_FOUND);
        assertThat(result.htmlContent()).isNull();
    }

    @Test
    void shouldHandle500ServerError() {
        URI uri = URI.create(baseUrl + "/server-error");

        PageData result = pageFetcher.fetch(uri);

        assertThat(result.status()).isEqualTo(PageData.Status.SERVER_ERROR);
        assertThat(result.htmlContent()).isNull();
    }

    @Test
    void shouldHandle400ClientError() {
        URI uri = URI.create(baseUrl + "/bad-request");

        PageData result = pageFetcher.fetch(uri);

        assertThat(result.status()).isEqualTo(PageData.Status.CLIENT_ERROR);
        assertThat(result.htmlContent()).isNull();
    }

    @Test
    void shouldRejectNonHtmlContent() {
        URI uri = URI.create(baseUrl + "/json-content");

        PageData result = pageFetcher.fetch(uri);

        assertThat(result.status()).isEqualTo(PageData.Status.CLIENT_ERROR);
        assertThat(result.htmlContent()).isNull();
    }

    @Test
    void shouldHandleRedirects() {
        URI uri = URI.create(baseUrl + "/redirect");

        PageData result = pageFetcher.fetch(uri);

        assertThat(result.status()).isEqualTo(PageData.Status.SUCCESS);
        assertThat(result.htmlContent()).isEqualTo("<html><body>Final page</body></html>");

        wireMockServer.verify(getRequestedFor(urlEqualTo("/redirect")));
        wireMockServer.verify(getRequestedFor(urlEqualTo("/final-destination")));
    }

    @Test
    void shouldRejectNonHttpSchemes() {
        URI ftpUri = URI.create("ftp://example.com/file.txt");

        PageData result = pageFetcher.fetch(ftpUri);

        assertThat(result.status()).isEqualTo(PageData.Status.CLIENT_ERROR);
        assertThat(result.htmlContent()).isNull();
    }

    @Test
    void shouldHandleConnectionFailure() {
        URI uri = URI.create("http://localhost:99999/page");

        PageData result = pageFetcher.fetch(uri);

        assertThat(result.status()).isEqualTo(PageData.Status.FETCH_ERROR);
        assertThat(result.htmlContent()).isNull();
    }

    @Test
    void shouldHandleMixedCaseContentType() {
        URI uri = URI.create(baseUrl + "/mixed-case");

        PageData result = pageFetcher.fetch(uri);

        assertThat(result.status()).isEqualTo(PageData.Status.SUCCESS);
        assertThat(result.htmlContent()).isEqualTo("<html><body>Mixed case content type</body></html>");
    }
}
