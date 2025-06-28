package com.monzo.crawler.infrastructure;

import com.monzo.crawler.domain.port.out.FrontierQueue;
import io.lettuce.core.api.sync.RedisCommands;
import java.net.URI;

public class RedisFrontierQueue implements FrontierQueue {

    private final RedisCommands<String, String> redis;
    private static final String QUEUE_NAME = "frontier-queue";

    public RedisFrontierQueue(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public void enqueue(URI uri) {
        redis.rpush(QUEUE_NAME, uri.toString());
    }

    @Override
    public URI dequeue() {
        String uriString = redis.lpop(QUEUE_NAME);
        return uriString != null ? URI.create(uriString) : null;
    }

    @Override
    public boolean isEmpty() {
        return redis.llen(QUEUE_NAME) == 0;
    }
}
