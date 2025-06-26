package com.monzo.crawler.infrastructure;

import com.monzo.crawler.domain.port.out.FrontierQueue;
import com.monzo.crawler.infrastructure.config.TestRedisConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisFrontierQueueIntegrationTest {

    @Container
    static final GenericContainer<?> redis = TestRedisConfiguration.createRedisContainer();

    private TestRedisConfiguration.TestRedisSetup redisSetup;
    private FrontierQueue frontierQueue;

    @BeforeEach
    void setUp() {
        redisSetup = TestRedisConfiguration.createTestSetup(redis);
        frontierQueue = new RedisFrontierQueue(redisSetup.getCommands());
    }

    @AfterEach
    void tearDown() {
        if (redisSetup != null) {
            redisSetup.close();
        }
    }

    @Test
    void shouldStartEmpty() {
        // When/Then
        assertThat(frontierQueue.isEmpty()).isTrue();
        assertThat(frontierQueue.dequeue()).isNull();
    }

    @Test
    void shouldEnqueueAndDequeueUri() {
        // Given
        URI uri = URI.create("https://example.com/page1");

        // When
        frontierQueue.enqueue(uri);

        // Then
        assertThat(frontierQueue.isEmpty()).isFalse();

        URI dequeued = frontierQueue.dequeue();
        assertThat(dequeued).isEqualTo(uri);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldMaintainFifoOrder() {
        // Given
        URI uri1 = URI.create("https://example.com/page1");
        URI uri2 = URI.create("https://example.com/page2");
        URI uri3 = URI.create("https://example.com/page3");

        // When
        frontierQueue.enqueue(uri1);
        frontierQueue.enqueue(uri2);
        frontierQueue.enqueue(uri3);

        // Then
        assertThat(frontierQueue.isEmpty()).isFalse();
        assertThat(frontierQueue.dequeue()).isEqualTo(uri1);
        assertThat(frontierQueue.dequeue()).isEqualTo(uri2);
        assertThat(frontierQueue.dequeue()).isEqualTo(uri3);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleMultipleEnqueuesAndDequeues() {
        // Given
        URI uri1 = URI.create("https://example.com/page1");
        URI uri2 = URI.create("https://example.com/page2");
        URI uri3 = URI.create("https://example.com/page3");

        // When/Then
        frontierQueue.enqueue(uri1);
        assertThat(frontierQueue.isEmpty()).isFalse();

        frontierQueue.enqueue(uri2);
        assertThat(frontierQueue.dequeue()).isEqualTo(uri1);

        frontierQueue.enqueue(uri3);
        assertThat(frontierQueue.dequeue()).isEqualTo(uri2);
        assertThat(frontierQueue.dequeue()).isEqualTo(uri3);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldReturnNullWhenDequeueingFromEmptyQueue() {
        // Given - empty queue
        assertThat(frontierQueue.isEmpty()).isTrue();

        // When/Then
        assertThat(frontierQueue.dequeue()).isNull();
        assertThat(frontierQueue.dequeue()).isNull(); // Multiple calls should return null
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleUrisWithSpecialCharacters() {
        // Given - Use properly encoded URIs and various special characters
        URI uriWithEncodedSpaces = URI.create("https://example.com/page%20with%20spaces");
        URI uriWithQuery = URI.create("https://example.com/page?param=value&other=test");
        URI uriWithFragment = URI.create("https://example.com/page#section");
        URI uriWithPort = URI.create("https://example.com:8080/page");
        URI uriWithDashes = URI.create("https://example.com/page-with-dashes");
        URI uriWithUnderscores = URI.create("https://example.com/page_with_underscores");

        // When
        frontierQueue.enqueue(uriWithEncodedSpaces);
        frontierQueue.enqueue(uriWithQuery);
        frontierQueue.enqueue(uriWithFragment);
        frontierQueue.enqueue(uriWithPort);
        frontierQueue.enqueue(uriWithDashes);
        frontierQueue.enqueue(uriWithUnderscores);

        // Then
        assertThat(frontierQueue.dequeue()).isEqualTo(uriWithEncodedSpaces);
        assertThat(frontierQueue.dequeue()).isEqualTo(uriWithQuery);
        assertThat(frontierQueue.dequeue()).isEqualTo(uriWithFragment);
        assertThat(frontierQueue.dequeue()).isEqualTo(uriWithPort);
        assertThat(frontierQueue.dequeue()).isEqualTo(uriWithDashes);
        assertThat(frontierQueue.dequeue()).isEqualTo(uriWithUnderscores);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleUriEncodingProperly() {
        // Given - Test URI encoding behavior
        try {
            URI uriWithSpaces = new URI("https", "example.com", "/page with spaces", null);
            URI uriWithSpecialChars = new URI("https", "example.com", "/page@special&chars", "query=test", null);

            // When
            frontierQueue.enqueue(uriWithSpaces);
            frontierQueue.enqueue(uriWithSpecialChars);

            // Then
            assertThat(frontierQueue.dequeue()).isEqualTo(uriWithSpaces);
            assertThat(frontierQueue.dequeue()).isEqualTo(uriWithSpecialChars);
            assertThat(frontierQueue.isEmpty()).isTrue();

        } catch (Exception e) {
            // If URI construction fails, test with pre-encoded URIs
            URI encodedUri1 = URI.create("https://example.com/page%20with%20spaces");
            URI encodedUri2 = URI.create("https://example.com/page%40special%26chars?query=test");

            frontierQueue.enqueue(encodedUri1);
            frontierQueue.enqueue(encodedUri2);

            assertThat(frontierQueue.dequeue()).isEqualTo(encodedUri1);
            assertThat(frontierQueue.dequeue()).isEqualTo(encodedUri2);
            assertThat(frontierQueue.isEmpty()).isTrue();
        }
    }

    @Test
    void shouldHandleDuplicateUris() {
        // Given
        URI uri = URI.create("https://example.com/page1");

        // When
        frontierQueue.enqueue(uri);
        frontierQueue.enqueue(uri);
        frontierQueue.enqueue(uri);

        // Then - should store all duplicates (queue doesn't deduplicate)
        assertThat(frontierQueue.dequeue()).isEqualTo(uri);
        assertThat(frontierQueue.dequeue()).isEqualTo(uri);
        assertThat(frontierQueue.dequeue()).isEqualTo(uri);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldPersistDataAcrossConnections() {
        // Given
        URI uri1 = URI.create("https://example.com/page1");
        URI uri2 = URI.create("https://example.com/page2");

        frontierQueue.enqueue(uri1);
        frontierQueue.enqueue(uri2);

        // When - create new queue with same Redis instance
        RedisFrontierQueue newQueue = new RedisFrontierQueue(redisSetup.getCommands());

        // Then
        assertThat(newQueue.isEmpty()).isFalse();
        assertThat(newQueue.dequeue()).isEqualTo(uri1);
        assertThat(newQueue.dequeue()).isEqualTo(uri2);
        assertThat(newQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleLargeNumberOfUris() {
        // Given
        int numberOfUris = 1000;
        List<URI> uris = new ArrayList<>();

        for (int i = 0; i < numberOfUris; i++) {
            uris.add(URI.create("https://example.com/page" + i));
        }

        // When
        for (URI uri : uris) {
            frontierQueue.enqueue(uri);
        }

        // Then
        assertThat(frontierQueue.isEmpty()).isFalse();

        for (int i = 0; i < numberOfUris; i++) {
            URI dequeued = frontierQueue.dequeue();
            assertThat(dequeued).isEqualTo(uris.get(i));
        }

        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldBeThreadSafeForEnqueue() throws Exception {
        // Given
        int numberOfThreads = 10;
        int urisPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        // When - multiple threads enqueuing simultaneously
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int t = 0; t < numberOfThreads; t++) {
            final int threadId = t;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < urisPerThread; i++) {
                    URI uri = URI.create("https://example.com/thread-" + threadId + "-page-" + i);
                    frontierQueue.enqueue(uri);
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        // Then - all URIs should be enqueued
        Long queueSize = redisSetup.getCommands().llen("frontier-queue");
        assertThat(queueSize).isEqualTo(numberOfThreads * urisPerThread);
        assertThat(frontierQueue.isEmpty()).isFalse();

        executor.shutdown();
    }

    @Test
    void shouldBeThreadSafeForDequeue() throws Exception {
        // Given
        int numberOfUris = 1000;
        for (int i = 0; i < numberOfUris; i++) {
            frontierQueue.enqueue(URI.create("https://example.com/page" + i));
        }

        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        List<URI> dequeuedUris = new ArrayList<>();

        // When - multiple threads dequeuing simultaneously
        List<CompletableFuture<List<URI>>> futures = new ArrayList<>();
        for (int t = 0; t < numberOfThreads; t++) {
            CompletableFuture<List<URI>> future = CompletableFuture.supplyAsync(() -> {
                List<URI> threadUris = new ArrayList<>();
                URI uri;
                while ((uri = frontierQueue.dequeue()) != null) {
                    threadUris.add(uri);
                }
                return threadUris;
            }, executor);
            futures.add(future);
        }

        // Collect all results
        for (CompletableFuture<List<URI>> future : futures) {
            dequeuedUris.addAll(future.get(30, TimeUnit.SECONDS));
        }

        // Then - all URIs should be dequeued exactly once
        assertThat(dequeuedUris).hasSize(numberOfUris);
        assertThat(frontierQueue.isEmpty()).isTrue();

        executor.shutdown();
    }

    @Test
    void shouldHandleConcurrentEnqueueAndDequeue() throws Exception {
        // Given
        int numberOfProducers = 5;
        int numberOfConsumers = 3;
        int urisPerProducer = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfProducers + numberOfConsumers);

        List<URI> enqueuedUris = new ArrayList<>();
        List<URI> dequeuedUris = new ArrayList<>();

        // When - concurrent producers and consumers
        List<CompletableFuture<Void>> producerFutures = new ArrayList<>();
        for (int p = 0; p < numberOfProducers; p++) {
            final int producerId = p;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < urisPerProducer; i++) {
                    URI uri = URI.create("https://example.com/producer-" + producerId + "-page-" + i);
                    synchronized (enqueuedUris) {
                        enqueuedUris.add(uri);
                    }
                    frontierQueue.enqueue(uri);

                    // Small delay to allow interleaving
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, executor);
            producerFutures.add(future);
        }

        List<CompletableFuture<Void>> consumerFutures = new ArrayList<>();
        for (int c = 0; c < numberOfConsumers; c++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    URI uri = frontierQueue.dequeue();
                    if (uri != null) {
                        synchronized (dequeuedUris) {
                            dequeuedUris.add(uri);
                        }
                    } else {
                        // Short sleep when queue is empty
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }, executor);
            consumerFutures.add(future);
        }

        // Wait for all producers to finish
        CompletableFuture.allOf(producerFutures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        // Give consumers time to consume remaining items
        Thread.sleep(1000);

        // Stop consumers
        consumerFutures.forEach(f -> f.cancel(true));

        // Drain any remaining items
        URI remaining;
        while ((remaining = frontierQueue.dequeue()) != null) {
            dequeuedUris.add(remaining);
        }

        // Then
        assertThat(dequeuedUris).hasSize(numberOfProducers * urisPerProducer);
        assertThat(frontierQueue.isEmpty()).isTrue();

        executor.shutdown();
    }

    @Test
    void shouldCorrectlyReportEmptyState() {
        // Given - initially empty
        assertThat(frontierQueue.isEmpty()).isTrue();

        // When - add and remove one item
        URI uri = URI.create("https://example.com/page1");
        frontierQueue.enqueue(uri);
        assertThat(frontierQueue.isEmpty()).isFalse();

        frontierQueue.dequeue();
        assertThat(frontierQueue.isEmpty()).isTrue();

        // When - add multiple and remove all
        frontierQueue.enqueue(URI.create("https://example.com/page1"));
        frontierQueue.enqueue(URI.create("https://example.com/page2"));
        frontierQueue.enqueue(URI.create("https://example.com/page3"));
        assertThat(frontierQueue.isEmpty()).isFalse();

        frontierQueue.dequeue();
        frontierQueue.dequeue();
        assertThat(frontierQueue.isEmpty()).isFalse();

        frontierQueue.dequeue();
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleVeryLongUris() {
        // Given - construct a very long URI
        StringBuilder longPath = new StringBuilder("https://example.com/");
        for (int i = 0; i < 1000; i++) {
            longPath.append("very-long-path-segment-").append(i).append("/");
        }
        URI longUri = URI.create(longPath.toString());

        // When
        frontierQueue.enqueue(longUri);

        // Then
        assertThat(frontierQueue.isEmpty()).isFalse();
        URI dequeued = frontierQueue.dequeue();
        assertThat(dequeued).isEqualTo(longUri);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }
}
