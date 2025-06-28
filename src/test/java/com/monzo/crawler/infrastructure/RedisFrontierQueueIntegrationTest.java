package com.monzo.crawler.infrastructure;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisFrontierQueueIntegrationTest {

    @Container
    static final GenericContainer<?> redis = TestRedisConfiguration.createRedisContainer();

    private TestRedisConfiguration.TestRedisSetup redisSetup;
    private RedisCommands<String, String> redisCommands;
    private RedisFrontierQueue frontierQueue;

    @BeforeEach
    void setUp() {
        /**
         * Initialize Redis connection for each test.
         * Due to problems with shared connections in tests,
         * it was finally decided to create a dedicated connection for each test.
         */

        // Create dedicated connection for each test
        redisSetup = TestRedisConfiguration.createDedicatedTestSetup(redis);
        redisCommands = redisSetup.getCommands();
        frontierQueue = new RedisFrontierQueue(redisCommands);
    }

    @AfterEach
    void tearDown() {
        // Close dedicated connection after each test
        if (redisSetup != null) {
            redisSetup.close();
        }
    }

    @Test
    void shouldReturnTrueForEmptyQueueInitially() {
        // Given - fresh queue

        // When
        boolean isEmpty = frontierQueue.isEmpty();

        // Then
        assertThat(isEmpty).isTrue();
    }

    @Test
    void shouldEnqueueAndDequeueSingleUri() {
        // Given
        URI testUri = URI.create("https://example.com");

        // When
        frontierQueue.enqueue(testUri);
        URI dequeuedUri = frontierQueue.dequeue();

        // Then
        assertThat(dequeuedUri).isEqualTo(testUri);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldReturnNullWhenDequeueingFromEmptyQueue() {
        // Given - empty queue
        assertThat(frontierQueue.isEmpty()).isTrue();

        // When
        URI dequeuedUri = frontierQueue.dequeue();

        // Then
        assertThat(dequeuedUri).isNull();
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldMaintainFifoOrder() {
        // Given
        List<URI> testUris = List.of(
                URI.create("https://example.com/1"),
                URI.create("https://example.com/2"),
                URI.create("https://example.com/3")
        );

        // When
        testUris.forEach(frontierQueue::enqueue);

        // Then
        List<URI> dequeuedUris = new ArrayList<>();
        while (!frontierQueue.isEmpty()) {
            dequeuedUris.add(frontierQueue.dequeue());
        }

        assertThat(dequeuedUris).isEqualTo(testUris);
    }

    @Test
    void shouldCorrectlyReportQueueState() {
        // Given - initially empty
        assertThat(frontierQueue.isEmpty()).isTrue();

        // When adding items
        frontierQueue.enqueue(URI.create("https://example.com/1"));
        frontierQueue.enqueue(URI.create("https://example.com/2"));

        // Then
        assertThat(frontierQueue.isEmpty()).isFalse();

        // When removing all items
        frontierQueue.dequeue();
        frontierQueue.dequeue();

        // Then
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleVariousUriFormats() {
        // Given
        List<URI> diverseUris = List.of(
                URI.create("https://example.com/path?query=value"),
                URI.create("http://subdomain.example.org:8080/path"),
                URI.create("https://example.com/path/with/many/segments"),
                URI.create("https://example.com/#fragment"),
                URI.create("https://user:pass@example.com/path")
        );

        // When
        diverseUris.forEach(frontierQueue::enqueue);

        // Then
        List<URI> dequeuedUris = new ArrayList<>();
        while (!frontierQueue.isEmpty()) {
            dequeuedUris.add(frontierQueue.dequeue());
        }

        assertThat(dequeuedUris).isEqualTo(diverseUris);
    }

    @Test
    void shouldHandleMixedEnqueueDequeueOperations() {
        // Given
        URI uri1 = URI.create("https://example.com/1");
        URI uri2 = URI.create("https://example.com/2");
        URI uri3 = URI.create("https://example.com/3");

        // When performing mixed operations
        frontierQueue.enqueue(uri1);
        frontierQueue.enqueue(uri2);

        URI first = frontierQueue.dequeue();

        frontierQueue.enqueue(uri3);

        URI second = frontierQueue.dequeue();
        URI third = frontierQueue.dequeue();

        // Then
        assertThat(first).isEqualTo(uri1);
        assertThat(second).isEqualTo(uri2);
        assertThat(third).isEqualTo(uri3);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleDuplicateUris() {
        // Given
        URI duplicateUri = URI.create("https://example.com/duplicate");

        // When
        frontierQueue.enqueue(duplicateUri);
        frontierQueue.enqueue(duplicateUri);
        frontierQueue.enqueue(duplicateUri);

        // Then
        assertThat(frontierQueue.dequeue()).isEqualTo(duplicateUri);
        assertThat(frontierQueue.dequeue()).isEqualTo(duplicateUri);
        assertThat(frontierQueue.dequeue()).isEqualTo(duplicateUri);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleEncodedUris() {
        // Given
        URI encodedUri = URI.create("https://example.com/page%20with%20spaces");
        URI anotherEncodedUri = URI.create("https://example.com/page%3Fquery%3Dvalue");

        // When
        frontierQueue.enqueue(encodedUri);
        frontierQueue.enqueue(anotherEncodedUri);

        // Then
        assertThat(frontierQueue.dequeue()).isEqualTo(encodedUri);
        assertThat(frontierQueue.dequeue()).isEqualTo(anotherEncodedUri);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleUrisWithSpecialCharacters() throws URISyntaxException {
        // Given - Use URI constructor for proper encoding
        URI uriWithSpaces = new URI("https", "example.com", "/page with spaces", null);
        URI uriWithSpecialChars = new URI("https", "example.com", "/page@special&chars", "query=test", null);

        // When
        frontierQueue.enqueue(uriWithSpaces);
        frontierQueue.enqueue(uriWithSpecialChars);

        // Then
        assertThat(frontierQueue.dequeue()).isEqualTo(uriWithSpaces);
        assertThat(frontierQueue.dequeue()).isEqualTo(uriWithSpecialChars);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleUrisWithDifferentSchemes() {
        // Given
        URI httpsUri = URI.create("https://example.com/page");
        URI httpUri = URI.create("http://example.com/page");

        // When
        frontierQueue.enqueue(httpsUri);
        frontierQueue.enqueue(httpUri);

        // Then
        assertThat(frontierQueue.dequeue()).isEqualTo(httpsUri);
        assertThat(frontierQueue.dequeue()).isEqualTo(httpUri);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleUrisWithDifferentPorts() {
        // Given
        URI uriWithDefaultPort = URI.create("https://example.com/page");
        URI uriWithExplicitPort = URI.create("https://example.com:443/page");
        URI uriWithCustomPort = URI.create("https://example.com:8080/page");

        // When
        frontierQueue.enqueue(uriWithDefaultPort);
        frontierQueue.enqueue(uriWithExplicitPort);
        frontierQueue.enqueue(uriWithCustomPort);

        // Then
        assertThat(frontierQueue.dequeue()).isEqualTo(uriWithDefaultPort);
        assertThat(frontierQueue.dequeue()).isEqualTo(uriWithExplicitPort);
        assertThat(frontierQueue.dequeue()).isEqualTo(uriWithCustomPort);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldPersistDataAcrossInstances() {
        // Given
        URI uri = URI.create("https://example.com/page1");
        frontierQueue.enqueue(uri);

        // When - create new queue with same Redis connection
        RedisFrontierQueue newQueue = new RedisFrontierQueue(redisCommands);

        // Then
        assertThat(newQueue.isEmpty()).isFalse();
        assertThat(newQueue.dequeue()).isEqualTo(uri);
        assertThat(newQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleLargeNumberOfUris() {
        // Given
        int numberOfUris = 500; // Reduced for isolated test performance
        List<URI> testUris = IntStream.range(0, numberOfUris)
                .mapToObj(i -> URI.create("https://example.com/page/" + i))
                .toList();

        // When
        testUris.forEach(frontierQueue::enqueue);

        // Then
        assertThat(frontierQueue.isEmpty()).isFalse();

        List<URI> dequeuedUris = new ArrayList<>();
        while (!frontierQueue.isEmpty()) {
            dequeuedUris.add(frontierQueue.dequeue());
        }

        assertThat(dequeuedUris).hasSize(numberOfUris);
        assertThat(dequeuedUris).isEqualTo(testUris);
    }

    @Test
    void shouldBeThreadSafeForConcurrentEnqueues() throws Exception {
        // Given
        int numberOfThreads = 5; // Reduced for isolated test
        int urisPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        // When - multiple threads enqueueing different URIs
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

        // Then - verify all URIs were enqueued
        assertThat(frontierQueue.isEmpty()).isFalse();

        // Verify total count by dequeuing all
        List<URI> allUris = new ArrayList<>();
        while (!frontierQueue.isEmpty()) {
            allUris.add(frontierQueue.dequeue());
        }

        assertThat(allUris).hasSize(numberOfThreads * urisPerThread);

        executor.shutdown();
    }

    @Test
    void shouldBeThreadSafeForConcurrentDequeues() throws Exception {
        // Given - pre-populate queue
        int numberOfUris = 50; // Reduced for isolated test
        for (int i = 0; i < numberOfUris; i++) {
            frontierQueue.enqueue(URI.create("https://example.com/page" + i));
        }

        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        // When - multiple threads dequeuing simultaneously
        List<CompletableFuture<List<URI>>> futures = new ArrayList<>();
        for (int t = 0; t < numberOfThreads; t++) {
            CompletableFuture<List<URI>> future = CompletableFuture.supplyAsync(() -> {
                List<URI> dequeuedUris = new ArrayList<>();
                URI uri;
                while ((uri = frontierQueue.dequeue()) != null) {
                    dequeuedUris.add(uri);
                }
                return dequeuedUris;
            }, executor);
            futures.add(future);
        }

        // Then - collect all dequeued URIs
        List<URI> allDequeuedUris = new ArrayList<>();
        for (CompletableFuture<List<URI>> future : futures) {
            allDequeuedUris.addAll(future.get(30, TimeUnit.SECONDS));
        }

        assertThat(allDequeuedUris).hasSize(numberOfUris);
        assertThat(frontierQueue.isEmpty()).isTrue();

        executor.shutdown();
    }

    @Test
    void shouldHandleMixedConcurrentOperations() throws Exception {
        // Given - simplified approach to avoid timing issues
        int numberOfProducers = 2;
        int numberOfConsumers = 2;
        int itemsPerProducer = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfProducers + numberOfConsumers);

        // Use CountDownLatch to coordinate
       CountDownLatch producersStarted = new CountDownLatch(numberOfProducers);
       CountDownLatch producersFinished = new CountDownLatch(numberOfProducers);
       AtomicInteger totalProduced = new AtomicInteger(0);

        List<CompletableFuture<Void>> producerFutures = new ArrayList<>();
        List<CompletableFuture<List<URI>>> consumerFutures = new ArrayList<>();

        // Start producers
        for (int p = 0; p < numberOfProducers; p++) {
            final int producerId = p;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                producersStarted.countDown();
                for (int i = 0; i < itemsPerProducer; i++) {
                    URI uri = URI.create("https://example.com/producer-" + producerId + "-item-" + i);
                    frontierQueue.enqueue(uri);
                    totalProduced.incrementAndGet();
                }
                producersFinished.countDown();
            }, executor);
            producerFutures.add(future);
        }

        // Wait for producers to start, then start consumers
        producersStarted.await(10, TimeUnit.SECONDS);

        // Start consumers
        for (int c = 0; c < numberOfConsumers; c++) {
            CompletableFuture<List<URI>> future = CompletableFuture.supplyAsync(() -> {
                List<URI> consumedUris = new ArrayList<>();

                // Keep consuming until producers are done and queue is empty
                while (true) {
                    URI uri = frontierQueue.dequeue();
                    if (uri != null) {
                        consumedUris.add(uri);
                    } else {
                        // Check if producers are done
                        if (producersFinished.getCount() == 0) {
                            // Producers are done, try once more to ensure queue is empty
                            uri = frontierQueue.dequeue();
                            if (uri != null) {
                                consumedUris.add(uri);
                            } else {
                                break; // Queue is empty and producers are done
                            }
                        } else {
                            // Producers still working, brief pause
                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                }

                return consumedUris;
            }, executor);
            consumerFutures.add(future);
        }

        // Wait for all operations to complete
        CompletableFuture.allOf(producerFutures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        List<URI> allConsumedUris = new ArrayList<>();
        for (CompletableFuture<List<URI>> future : consumerFutures) {
            allConsumedUris.addAll(future.get(30, TimeUnit.SECONDS));
        }

        // Collect any remaining items in queue (shouldn't be any)
        URI remaining;
        while ((remaining = frontierQueue.dequeue()) != null) {
            allConsumedUris.add(remaining);
        }

        // Then - verify all produced items were consumed
        int expectedTotal = numberOfProducers * itemsPerProducer;
        assertThat(allConsumedUris).hasSize(expectedTotal);
        assertThat(frontierQueue.isEmpty()).isTrue();

        executor.shutdown();
    }

    @Test
    void shouldHandleVeryLongUris() {
        // Given - construct a very long URI
        StringBuilder longPath = new StringBuilder("https://example.com/");
        for (int i = 0; i < 100; i++) { // Reduced for isolated test
            longPath.append("very-long-path-segment-").append(i).append("/");
        }
        URI longUri = URI.create(longPath.toString());

        // When
        frontierQueue.enqueue(longUri);

        // Then
        assertThat(frontierQueue.isEmpty()).isFalse();
        assertThat(frontierQueue.dequeue()).isEqualTo(longUri);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleEmptyPath() {
        // Given
        URI uriWithEmptyPath = URI.create("https://example.com");
        URI uriWithSlashPath = URI.create("https://example.com/");

        // When
        frontierQueue.enqueue(uriWithEmptyPath);
        frontierQueue.enqueue(uriWithSlashPath);

        // Then
        assertThat(frontierQueue.dequeue()).isEqualTo(uriWithEmptyPath);
        assertThat(frontierQueue.dequeue()).isEqualTo(uriWithSlashPath);
        assertThat(frontierQueue.isEmpty()).isTrue();
    }

    @Test
    void shouldVerifyRedisListOperations() {
        // Given
        URI uri1 = URI.create("https://example.com/page1");
        URI uri2 = URI.create("https://example.com/page2");

        // When
        frontierQueue.enqueue(uri1);
        frontierQueue.enqueue(uri2);

        // Then - verify using direct Redis commands
        Long listLength = redisCommands.llen("frontier-queue");
        assertThat(listLength).isEqualTo(2);

        List<String> allItems = redisCommands.lrange("frontier-queue", 0, -1);
        assertThat(allItems).hasSize(2);
        assertThat(allItems).containsExactly(uri1.toString(), uri2.toString());

        // Verify dequeue behavior matches Redis LPOP
        String firstItem = redisCommands.lpop("frontier-queue");
        assertThat(firstItem).isEqualTo(uri1.toString());

        String secondItem = redisCommands.lpop("frontier-queue");
        assertThat(secondItem).isEqualTo(uri2.toString());

        assertThat(redisCommands.llen("frontier-queue")).isEqualTo(0);
    }
}
