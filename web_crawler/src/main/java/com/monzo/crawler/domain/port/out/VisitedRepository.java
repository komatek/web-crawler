package com.monzo.crawler.domain.port.out;

import java.net.URI;

/**
 * Port for managing the set of URIs that have already been visited.
 */
public interface VisitedRepository {
    boolean isVisited(URI uri);
    boolean markVisited(URI uri);
}
