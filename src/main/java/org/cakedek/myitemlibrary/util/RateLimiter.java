package org.cakedek.myitemlibrary.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {
    private final Map<String, RequestCount> requestCounts = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long timeWindowMs;

    public RateLimiter(int maxRequests, long timeWindowMs) {
        this.maxRequests = maxRequests;
        this.timeWindowMs = timeWindowMs;
    }

    public boolean allowRequest(String ip) {
        long now = System.currentTimeMillis();
        RequestCount count = requestCounts.compute(ip, (key, val) ->
                (val == null || now - val.timestamp > timeWindowMs) ?
                        new RequestCount(now) : val);
        return count.increment() <= maxRequests;
    }

    private static class RequestCount {
        final long timestamp;
        final AtomicInteger count;

        RequestCount(long timestamp) {
            this.timestamp = timestamp;
            this.count = new AtomicInteger(0);
        }

        int increment() {
            return count.incrementAndGet();
        }
    }
}