package com.monzo.crawler.infrastructure;

import com.monzo.crawler.domain.port.out.VisitedRepository;
import io.lettuce.core.api.sync.RedisCommands;
import java.net.URI;

public class RedisVisitedRepository implements VisitedRepository {

    private final RedisCommands<String, String> redis;

    public RedisVisitedRepository(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public boolean isVisited(URI uri) {
        return redis.sismember("visited-urls", uri.toString());
    }

    @Override
    public boolean markVisited(URI uri) {
        return redis.sadd("visited-urls", uri.toString()) == 1;
    }
}
