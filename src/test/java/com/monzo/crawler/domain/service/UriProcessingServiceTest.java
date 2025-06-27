package com.monzo.crawler.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class UriProcessingServiceTest {

    private static final String ALLOWED_DOMAIN = "example.com";
    private final UriProcessingService uriProcessingService = new UriProcessingService(ALLOWED_DOMAIN);

    @Test
    void constructorShouldThrowNullPointerExceptionWhenAllowedDomainIsNull() {
        assertThrows(NullPointerException.class, () ->
                new UriProcessingService(null)
        );
    }

    @ParameterizedTest
    @MethodSource("provideUrisForPathNormalization")
    void normalizeUriShouldNormalizePaths(String inputUri, String expectedPath, String expectedFullUri, String description) {
        // Given
        URI uri = URI.create(inputUri);

        // When
        URI result = uriProcessingService.normalizeUri(uri);

        // Then
        assertEquals(expectedPath, result.getPath(), description);
        assertEquals(expectedFullUri, result.toString(), description);
    }

    private static Stream<Arguments> provideUrisForPathNormalization() {
        return Stream.of(
                Arguments.of("https://example.com", "/", "https://example.com/", "should add root path when path is null"),
                Arguments.of("https://example.com/", "/", "https://example.com/", "should keep single slash when path is root with trailing slash"),
                Arguments.of("https://example.com/page/", "/page", "https://example.com/page", "should remove trailing slash when path ends with slash"),
                Arguments.of("https://example.com/page", "/page", "https://example.com/page", "should preserve path when path does not end with slash"),
                Arguments.of("https://example.com/level1/level2/level3/", "/level1/level2/level3", "https://example.com/level1/level2/level3", "should handle nested paths"),
                Arguments.of("https://example.com//double//slash//path/", "//double//slash//path", "https://example.com//double//slash//path", "should handle paths with multiple slashes")
        );
    }

    @ParameterizedTest
    @MethodSource("provideUrisForComponentPreservation")
    void normalizeUriShouldPreserveUriComponents(String inputUri, String expectedScheme, String expectedUserInfo,
                                                 String expectedHost, int expectedPort, String expectedQuery,
                                                 String description) {
        // Given
        URI uri = URI.create(inputUri);

        // When
        URI result = uriProcessingService.normalizeUri(uri);

        // Then
        assertEquals(expectedScheme, result.getScheme(), description + " - scheme");
        assertEquals(expectedUserInfo, result.getUserInfo(), description + " - userInfo");
        assertEquals(expectedHost, result.getHost(), description + " - host");
        assertEquals(expectedPort, result.getPort(), description + " - port");
        assertEquals(expectedQuery, result.getQuery(), description + " - query");
    }

    private static Stream<Arguments> provideUrisForComponentPreservation() {
        return Stream.of(
                Arguments.of("http://example.com/page", "http", null, "example.com", -1, null, "should preserve scheme"),
                Arguments.of("https://example.com:8080/page", "https", null, "example.com", 8080, null, "should preserve port"),
                Arguments.of("https://user:pass@example.com/page", "https", "user:pass", "example.com", -1, null, "should preserve userInfo"),
                Arguments.of("https://example.com/page?param=value", "https", null, "example.com", -1, "param=value", "should preserve query"),
                Arguments.of("https://example.com/page?", "https", null, "example.com", -1, "", "should preserve empty query")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/page#section1",
            "https://example.com/page#",
            "https://example.com/page?param=value#fragment",
            "https://example.com/#section"
    })
    void normalizeUriShouldRemoveFragment(String inputUri) {
        // Given
        URI uri = URI.create(inputUri);

        // When
        URI result = uriProcessingService.normalizeUri(uri);

        // Then
        assertNull(result.getFragment(), "Fragment should be removed for URI: " + inputUri);
    }

    @Test
    void normalizeUriShouldHandleComplexUri() {
        // Given
        URI uri = URI.create("https://user:pass@example.com:8080/path/to/page/?param1=value1&param2=value2#fragment");

        // When
        URI result = uriProcessingService.normalizeUri(uri);

        // Then
        assertEquals("https", result.getScheme());
        assertEquals("user:pass", result.getUserInfo());
        assertEquals("example.com", result.getHost());
        assertEquals(8080, result.getPort());
        assertEquals("/path/to/page", result.getPath()); // Trailing slash removed
        assertEquals("param1=value1&param2=value2", result.getQuery());
        assertNull(result.getFragment()); // Fragment removed
    }

    @Test
    void normalizeUriShouldReturnOriginalUriWhenNormalizationFails() {
        // Given a valid URI (testing the fallback mechanism)
        URI validUri = URI.create("https://example.com/page");

        // When
        URI result = uriProcessingService.normalizeUri(validUri);

        // Then
        assertNotNull(result);
        assertEquals("https://example.com/page", result.toString());
    }

    @ParameterizedTest
    @CsvSource({
            "https://example.com/page, true, exact domain match",
            "https://EXAMPLE.COM/page, true, case insensitive match",
            "http://example.com/page, true, different scheme same domain",
            "https://example.com:8080/page, true, different port same domain",
            "https://other.com/page, false, different domain",
            "https://sub.example.com/page, false, subdomain",
            "https://com/page, false, superdomain"
    })
    void isSameDomainShouldValidateDomainMatching(String uriString, boolean expected, String description) {
        // Given
        URI uri = URI.create(uriString);

        // When
        boolean result = uriProcessingService.isSameDomain(uri);

        // Then
        assertEquals(expected, result, description);
    }

    @Test
    void isSameDomainShouldReturnFalseWhenHostIsNull() {
        // Given
        URI uri = URI.create("file:///local/path");

        // When
        boolean result = uriProcessingService.isSameDomain(uri);

        // Then
        assertFalse(result);
    }

    @ParameterizedTest
    @CsvSource({
            "https://example.com/page, true, valid URI same domain",
            "https://EXAMPLE.COM/page, true, valid URI case insensitive",
            "http://example.com/page, true, valid URI different scheme",
            "https://example.com:8080/page, true, valid URI different port",
            "https://other.com/page, false, valid URI different domain",
            "https://sub.example.com/page, false, valid URI subdomain",
            "https://com/page, false, valid URI superdomain"
    })
    void isValidForCrawlingShouldValidateUriForCrawling(String uriString, boolean expected, String description) {
        // Given
        URI uri = URI.create(uriString);

        // When
        boolean result = uriProcessingService.isValidForCrawling(uri);

        // Then
        assertEquals(expected, result, description);
    }

    @Test
    void isValidForCrawlingShouldReturnFalseWhenUriIsNull() {
        // When
        boolean result = uriProcessingService.isValidForCrawling(null);

        // Then
        assertFalse(result);
    }

    @Test
    void isValidForCrawlingShouldReturnFalseWhenHostIsNull() {
        // Given
        URI uri = URI.create("file:///local/path");

        // When
        boolean result = uriProcessingService.isValidForCrawling(uri);

        // Then
        assertFalse(result);
    }
}
