package com.monzo.crawler.domain.port.out;

import java.net.URI;
import java.util.Optional;

/**
 * Port for managing the queue of URIs to be crawled.
 */
public interface FrontierQueue {
    void enqueue(URI uri);
    URI dequeue();
    boolean isEmpty();
}
