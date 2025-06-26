package com.monzo.crawler.infrastructure;

import com.monzo.crawler.infrastructure.JsoupLinkExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JsoupLinkExtractorTest {

    private JsoupLinkExtractor linkExtractor;
    private URI baseUri;

    @BeforeEach
    void setUp() {
        linkExtractor = new JsoupLinkExtractor();
        baseUri = URI.create("https://example.com");
    }

    @Test
    void shouldExtractAbsoluteHttpLinks() {
        // Given
        String html = """
            <html>
                <body>
                    <a href="https://example.com/page1">Page 1</a>
                    <a href="https://example.com/page2">Page 2</a>
                </body>
            </html>
            """;

        // When
        Set<URI> links = linkExtractor.extractLinks(html, baseUri);

        // Then
        assertThat(links).containsExactlyInAnyOrder(
                URI.create("https://example.com/page1"),
                URI.create("https://example.com/page2")
        );
    }

    @Test
    void shouldExtractRelativeLinks() {
        // Given
        String html = """
            <html>
                <body>
                    <a href="/about">About</a>
                    <a href="contact.html">Contact</a>
                    <a href="../parent">Parent</a>
                </body>
            </html>
            """;

        // When
        Set<URI> links = linkExtractor.extractLinks(html, baseUri);

        // Then
        assertThat(links).containsExactlyInAnyOrder(
                URI.create("https://example.com/about"),
                URI.create("https://example.com/contact.html"),
                URI.create("https://example.com/parent")
        );
    }

    @Test
    void shouldFilterOutMailtoLinks() {
        // Given
        String html = """
            <html>
                <body>
                    <a href="https://example.com/page1">Page 1</a>
                    <a href="mailto:test@example.com">Email</a>
                    <a href="https://example.com/page2">Page 2</a>
                </body>
            </html>
            """;

        // When
        Set<URI> links = linkExtractor.extractLinks(html, baseUri);

        // Then
        assertThat(links).containsExactlyInAnyOrder(
                URI.create("https://example.com/page1"),
                URI.create("https://example.com/page2")
        );
        assertThat(links).noneMatch(uri -> uri.getScheme().equals("mailto"));
    }

    @Test
    void shouldFilterOutTelLinks() {
        // Given
        String html = """
            <html>
                <body>
                    <a href="https://example.com/page1">Page 1</a>
                    <a href="tel:+1234567890">Call us</a>
                    <a href="https://example.com/page2">Page 2</a>
                </body>
            </html>
            """;

        // When
        Set<URI> links = linkExtractor.extractLinks(html, baseUri);

        // Then
        assertThat(links).containsExactlyInAnyOrder(
                URI.create("https://example.com/page1"),
                URI.create("https://example.com/page2")
        );
        assertThat(links).noneMatch(uri -> uri.getScheme().equals("tel"));
    }

    @Test
    void shouldFilterOutStaticFiles() {
        // Given
        String html = """
            <html>
                <body>
                    <a href="https://example.com/page1">Page 1</a>
                    <a href="https://example.com/document.pdf">PDF Document</a>
                    <a href="https://example.com/image.jpg">Image</a>
                    <a href="https://example.com/video.mp4">Video</a>
                    <a href="https://example.com/archive.zip">Archive</a>
                    <a href="https://example.com/page2">Page 2</a>
                </body>
            </html>
            """;

        // When
        Set<URI> links = linkExtractor.extractLinks(html, baseUri);

        // Then
        assertThat(links).containsExactlyInAnyOrder(
                URI.create("https://example.com/page1"),
                URI.create("https://example.com/page2")
        );
        assertThat(links).noneMatch(uri ->
                uri.getPath().toLowerCase().matches(".*\\.(pdf|jpg|mp4|zip)$"));
    }

    @Test
    void shouldFilterOutJavaScriptLinks() {
        // Given
        String html = """
            <html>
                <body>
                    <a href="https://example.com/page1">Page 1</a>
                    <a href="javascript:void(0)">JavaScript Link</a>
                    <a href="https://example.com/page2">Page 2</a>
                </body>
            </html>
            """;

        // When
        Set<URI> links = linkExtractor.extractLinks(html, baseUri);

        // Then
        assertThat(links).containsExactlyInAnyOrder(
                URI.create("https://example.com/page1"),
                URI.create("https://example.com/page2")
        );
        assertThat(links).noneMatch(uri -> uri.getScheme().equals("javascript"));
    }

    @Test
    void shouldHandleMalformedUrls() {
        // Given
        String html = """
            <html>
                <body>
                    <a href="https://example.com/page1">Valid Page</a>
                    <a href="not a valid url">Invalid URL</a>
                    <a href="https://example.com/page2">Another Valid Page</a>
                </body>
            </html>
            """;

        // When
        Set<URI> links = linkExtractor.extractLinks(html, baseUri);

        // Then
        assertThat(links).containsExactlyInAnyOrder(
                URI.create("https://example.com/page1"),
                URI.create("https://example.com/page2")
        );
    }

    @Test
    void shouldReturnEmptySetForNullHtml() {
        // When
        Set<URI> links = linkExtractor.extractLinks(null, baseUri);

        // Then
        assertThat(links).isEmpty();
    }

    @Test
    void shouldReturnEmptySetForBlankHtml() {
        // When
        Set<URI> links = linkExtractor.extractLinks("   ", baseUri);

        // Then
        assertThat(links).isEmpty();
    }

    @Test
    void shouldReturnEmptySetForHtmlWithNoLinks() {
        // Given
        String html = """
            <html>
                <body>
                    <p>This is a paragraph with no links.</p>
                    <div>Another div without links</div>
                </body>
            </html>
            """;

        // When
        Set<URI> links = linkExtractor.extractLinks(html, baseUri);

        // Then
        assertThat(links).isEmpty();
    }

    @Test
    void shouldHandleDuplicateLinks() {
        // Given
        String html = """
            <html>
                <body>
                    <a href="https://example.com/page1">Page 1 First</a>
                    <a href="https://example.com/page1">Page 1 Second</a>
                    <a href="https://example.com/page2">Page 2</a>
                </body>
            </html>
            """;

        // When
        Set<URI> links = linkExtractor.extractLinks(html, baseUri);

        // Then
        assertThat(links).containsExactlyInAnyOrder(
                URI.create("https://example.com/page1"),
                URI.create("https://example.com/page2")
        );
    }
}
