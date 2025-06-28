package com.monzo.crawler.infrastructure;

import com.monzo.crawler.domain.port.out.VisitedRepository;
import com.monzo.crawler.infrastructure.config.TestRedisConfiguration;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisVisitedRepositoryIntegrationTest {

    @Container
    static final GenericContainer<?> redis = TestRedisConfiguration.createRedisContainer();

    private static RedisCommands<String, String> redisCommands;
    private VisitedRepository visitedRepository;

    @BeforeAll
    static void setUpClass() {
        // Create shared Redis connection once for all tests
        redisCommands = TestRedisConfiguration.getSharedCommands(redis);
    }

    @AfterAll
    static void tearDownClass() {
        // Close shared resources after all tests
        TestRedisConfiguration.closeSharedResources();
    }

    @BeforeEach
    void setUp() {
        // Just flush data, reuse connection
        TestRedisConfiguration.cleanTestData(redisCommands);
        visitedRepository = new RedisVisitedRepository(redisCommands);
    }

    // No @AfterEach needed - connection is shared and data is cleaned in @BeforeEach

    @Test
    void shouldReturnFalseForUnvisitedUri() {
        // Given
        URI uri = URI.create("https://example.com/page1");

        // When
        boolean isVisited = visitedRepository.isVisited(uri);

        // Then
        assertThat(isVisited).isFalse();
    }

    @Test
    void shouldMarkUriAsVisitedAndReturnTrue() {
        // Given
        URI uri = URI.create("https://example.com/page1");

        // When
        boolean wasMarked = visitedRepository.markVisited(uri);

        // Then
        assertThat(wasMarked).isTrue();
        assertThat(visitedRepository.isVisited(uri)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenMarkingAlreadyVisitedUri() {
        // Given
        URI uri = URI.create("https://example.com/page1");
        visitedRepository.markVisited(uri);

        // When
        boolean wasMarked = visitedRepository.markVisited(uri);

        // Then
        assertThat(wasMarked).isFalse();
        assertThat(visitedRepository.isVisited(uri)).isTrue();
    }

    @Test
    void shouldHandleMultipleUris() {
        // Given
        URI uri1 = URI.create("https://example.com/page1");
        URI uri2 = URI.create("https://example.com/page2");
        URI uri3 = URI.create("https://example.com/page3");

        // When
        boolean marked1 = visitedRepository.markVisited(uri1);
        boolean marked2 = visitedRepository.markVisited(uri2);

        // Then
        assertThat(marked1).isTrue();
        assertThat(marked2).isTrue();
        assertThat(visitedRepository.isVisited(uri1)).isTrue();
        assertThat(visitedRepository.isVisited(uri2)).isTrue();
        assertThat(visitedRepository.isVisited(uri3)).isFalse();
    }

    @Test
    void shouldHandleUrisWithDifferentPaths() {
        // Given
        URI baseUri = URI.create("https://example.com/");
        URI pageUri = URI.create("https://example.com/page");
        URI subPageUri = URI.create("https://example.com/page/sub");

        // When
        visitedRepository.markVisited(baseUri);
        visitedRepository.markVisited(pageUri);

        // Then
        assertThat(visitedRepository.isVisited(baseUri)).isTrue();
        assertThat(visitedRepository.isVisited(pageUri)).isTrue();
        assertThat(visitedRepository.isVisited(subPageUri)).isFalse();
    }

    @Test
    void shouldHandleUrisWithQueryParameters() {
        // Given
        URI uri1 = URI.create("https://example.com/page?param=value1");
        URI uri2 = URI.create("https://example.com/page?param=value2");
        URI uri3 = URI.create("https://example.com/page");

        // When
        visitedRepository.markVisited(uri1);

        // Then
        assertThat(visitedRepository.isVisited(uri1)).isTrue();
        assertThat(visitedRepository.isVisited(uri2)).isFalse();
        assertThat(visitedRepository.isVisited(uri3)).isFalse();
    }

    @Test
    void shouldHandleUrisWithFragments() {
        // Given
        URI uriWithFragment = URI.create("https://example.com/page#section1");
        URI uriWithoutFragment = URI.create("https://example.com/page");

        // When
        visitedRepository.markVisited(uriWithFragment);

        // Then
        assertThat(visitedRepository.isVisited(uriWithFragment)).isTrue();
        assertThat(visitedRepository.isVisited(uriWithoutFragment)).isFalse();
    }

    @Test
    void shouldHandleUrisWithDifferentSchemes() {
        // Given
        URI httpsUri = URI.create("https://example.com/page");
        URI httpUri = URI.create("http://example.com/page");

        // When
        visitedRepository.markVisited(httpsUri);

        // Then
        assertThat(visitedRepository.isVisited(httpsUri)).isTrue();
        assertThat(visitedRepository.isVisited(httpUri)).isFalse();
    }

    @Test
    void shouldHandleUrisWithDifferentPorts() {
        // Given
        URI uriWithDefaultPort = URI.create("https://example.com/page");
        URI uriWithExplicitPort = URI.create("https://example.com:443/page");
        URI uriWithCustomPort = URI.create("https://example.com:8080/page");

        // When
        visitedRepository.markVisited(uriWithDefaultPort);
        visitedRepository.markVisited(uriWithCustomPort);

        // Then
        assertThat(visitedRepository.isVisited(uriWithDefaultPort)).isTrue();
        assertThat(visitedRepository.isVisited(uriWithExplicitPort)).isFalse(); // Different representation
        assertThat(visitedRepository.isVisited(uriWithCustomPort)).isTrue();
    }

    @Test
    void shouldHandleEncodedUris() {
        // Given
        URI encodedUri = URI.create("https://example.com/page%20with%20spaces");
        URI anotherEncodedUri = URI.create("https://example.com/page%3Fquery%3Dvalue");

        // When
        visitedRepository.markVisited(encodedUri);
        visitedRepository.markVisited(anotherEncodedUri);

        // Then
        assertThat(visitedRepository.isVisited(encodedUri)).isTrue();
        assertThat(visitedRepository.isVisited(anotherEncodedUri)).isTrue();
    }

    @Test
    void shouldHandleUrisWithSpecialCharacters() throws URISyntaxException {
        // Given - Use URI constructor for proper encoding
        URI uriWithSpaces = new URI("https", "example.com", "/page with spaces", null);
        URI uriWithSpecialChars = new URI("https", "example.com", "/page@special&chars", "query=test", null);

        // When
        visitedRepository.markVisited(uriWithSpaces);
        visitedRepository.markVisited(uriWithSpecialChars);

        // Then
        assertThat(visitedRepository.isVisited(uriWithSpaces)).isTrue();
        assertThat(visitedRepository.isVisited(uriWithSpecialChars)).isTrue();
    }

    @Test
    void shouldPersistDataAcrossInstances() {
        // Given
        URI uri = URI.create("https://example.com/page1");
        visitedRepository.markVisited(uri);

        // When - create new repository with same Redis connection
        RedisVisitedRepository newRepository = new RedisVisitedRepository(redisCommands);

        // Then
        assertThat(newRepository.isVisited(uri)).isTrue();
    }

    @Test
    void shouldHandleLargeNumberOfUris() {
        // Given
        int numberOfUris = 1000;
        List<URI> uris = IntStream.range(0, numberOfUris)
                .mapToObj(i -> URI.create("https://example.com/page" + i))
                .toList();

        // When
        for (URI uri : uris) {
            boolean wasMarked = visitedRepository.markVisited(uri);
            assertThat(wasMarked).isTrue();
        }

        // Then
        for (URI uri : uris) {
            assertThat(visitedRepository.isVisited(uri)).isTrue();
        }

        // Verify set size in Redis
        Long setSize = redisCommands.scard("visited-urls");
        assertThat(setSize).isEqualTo(numberOfUris);
    }

    @Test
    void shouldBeThreadSafeForConcurrentMarkings() throws Exception {
        // Given
        int numberOfThreads = 10;
        int urisPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        // When - multiple threads marking different URIs
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int t = 0; t < numberOfThreads; t++) {
            final int threadId = t;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < urisPerThread; i++) {
                    URI uri = URI.create("https://example.com/thread-" + threadId + "-page-" + i);
                    visitedRepository.markVisited(uri);
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        // Then - verify all URIs were marked
        for (int t = 0; t < numberOfThreads; t++) {
            for (int i = 0; i < urisPerThread; i++) {
                URI uri = URI.create("https://example.com/thread-" + t + "-page-" + i);
                assertThat(visitedRepository.isVisited(uri)).isTrue();
            }
        }

        // Verify total count
        Long setSize = redisCommands.scard("visited-urls");
        assertThat(setSize).isEqualTo(numberOfThreads * urisPerThread);

        executor.shutdown();
    }

    @Test
    void shouldBeThreadSafeForConcurrentReads() throws Exception {
        // Given - pre-populate with some URIs
        int numberOfUris = 100;
        for (int i = 0; i < numberOfUris; i++) {
            visitedRepository.markVisited(URI.create("https://example.com/page" + i));
        }

        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        // When - multiple threads reading simultaneously
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (int t = 0; t < numberOfThreads; t++) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                boolean allFound = true;
                for (int i = 0; i < numberOfUris; i++) {
                    URI uri = URI.create("https://example.com/page" + i);
                    if (!visitedRepository.isVisited(uri)) {
                        allFound = false;
                        break;
                    }
                }
                return allFound;
            }, executor);
            futures.add(future);
        }

        // Then - all threads should find all URIs
        for (CompletableFuture<Boolean> future : futures) {
            assertThat(future.get(30, TimeUnit.SECONDS)).isTrue();
        }

        executor.shutdown();
    }

    @Test
    void shouldHandleConcurrentMarkingOfSameUri() throws Exception {
        // Given
        URI uri = URI.create("https://example.com/concurrent-page");
        int numberOfThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        // When - multiple threads trying to mark the same URI
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (int t = 0; t < numberOfThreads; t++) {
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() ->
                    visitedRepository.markVisited(uri), executor);
            futures.add(future);
        }

        List<Boolean> results = new ArrayList<>();
        for (CompletableFuture<Boolean> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }

        // Then - only one thread should successfully mark it as visited (return true)
        long successfulMarks = results.stream().mapToLong(b -> b ? 1 : 0).sum();
        assertThat(successfulMarks).isEqualTo(1);
        assertThat(visitedRepository.isVisited(uri)).isTrue();

        // Verify only one entry in Redis
        Long setSize = redisCommands.scard("visited-urls");
        assertThat(setSize).isEqualTo(1);

        executor.shutdown();
    }

    @Test
    void shouldHandleMixedConcurrentOperations() throws Exception {
        // Given
        int numberOfReaderThreads = 5;
        int numberOfWriterThreads = 5;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfReaderThreads + numberOfWriterThreads);

        // Pre-populate with some URIs
        for (int i = 0; i < 25; i++) {
            visitedRepository.markVisited(URI.create("https://example.com/existing-page" + i));
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Writer threads
        for (int w = 0; w < numberOfWriterThreads; w++) {
            final int writerId = w;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    URI uri = URI.create("https://example.com/new-page-" + writerId + "-" + i);
                    visitedRepository.markVisited(uri);
                }
            }, executor);
            futures.add(future);
        }

        // Reader threads
        for (int r = 0; r < numberOfReaderThreads; r++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    // Read existing URIs
                    URI existingUri = URI.create("https://example.com/existing-page" + (i % 25));
                    visitedRepository.isVisited(existingUri);

                    // Read potentially new URIs
                    URI newUri = URI.create("https://example.com/new-page-0-" + i);
                    visitedRepository.isVisited(newUri);
                }
            }, executor);
            futures.add(future);
        }

        // When - wait for all operations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

        // Then - verify the final state
        // All pre-existing URIs should still be marked as visited
        for (int i = 0; i < 25; i++) {
            URI uri = URI.create("https://example.com/existing-page" + i);
            assertThat(visitedRepository.isVisited(uri)).isTrue();
        }

        // All new URIs should be marked as visited
        for (int w = 0; w < numberOfWriterThreads; w++) {
            for (int i = 0; i < operationsPerThread; i++) {
                URI uri = URI.create("https://example.com/new-page-" + w + "-" + i);
                assertThat(visitedRepository.isVisited(uri)).isTrue();
            }
        }

        executor.shutdown();
    }

    @Test
    void shouldHandleVeryLongUris() {
        // Given - construct a very long URI
        StringBuilder longPath = new StringBuilder("https://example.com/");
        for (int i = 0; i < 500; i++) {
            longPath.append("very-long-path-segment-").append(i).append("/");
        }
        URI longUri = URI.create(longPath.toString());

        // When
        boolean wasMarked = visitedRepository.markVisited(longUri);

        // Then
        assertThat(wasMarked).isTrue();
        assertThat(visitedRepository.isVisited(longUri)).isTrue();
    }

    @Test
    void shouldHandleEmptyPath() {
        // Given
        URI uriWithEmptyPath = URI.create("https://example.com");
        URI uriWithSlashPath = URI.create("https://example.com/");

        // When
        visitedRepository.markVisited(uriWithEmptyPath);

        // Then
        assertThat(visitedRepository.isVisited(uriWithEmptyPath)).isTrue();
        assertThat(visitedRepository.isVisited(uriWithSlashPath)).isFalse(); // Different URI representation
    }

    @Test
    void shouldVerifyRedisSetOperations() {
        // Given
        URI uri1 = URI.create("https://example.com/page1");
        URI uri2 = URI.create("https://example.com/page2");

        // When
        visitedRepository.markVisited(uri1);
        visitedRepository.markVisited(uri2);

        // Then - verify using direct Redis commands
        Set<String> allMembers = redisCommands.smembers("visited-urls");
        assertThat(allMembers).hasSize(2);
        assertThat(allMembers).contains(uri1.toString(), uri2.toString());

        // Verify set membership using Redis commands
        assertThat(redisCommands.sismember("visited-urls", uri1.toString())).isTrue();
        assertThat(redisCommands.sismember("visited-urls", uri2.toString())).isTrue();
        assertThat(redisCommands.sismember("visited-urls", "https://example.com/nonexistent")).isFalse();
    }
}
