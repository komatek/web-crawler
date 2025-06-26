package com.monzo.crawler.infrastructure;

import com.monzo.crawler.domain.port.out.FrontierQueue;
import io.lettuce.core.api.sync.RedisCommands;
import java.net.URI;

public class RedisFrontierQueue implements FrontierQueue {

    private final RedisCommands<String, String> redis;

    public RedisFrontierQueue(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public void enqueue(URI uri) {
        redis.rpush("frontier-queue", uri.toString());
    }

    @Override
    public URI dequeue() {
        String uriString = redis.lpop("frontier-queue");
        return uriString != null ? URI.create(uriString) : null;
    }

    @Override
    public boolean isEmpty() {
        return redis.llen("frontier-queue") == 0;
    }
}
