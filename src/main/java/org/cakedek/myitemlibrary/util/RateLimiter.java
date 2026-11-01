package org.cakedek.myitemlibrary.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {
    private final Map<String, RequestCount> requestCounts = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long timeWindowMs;
    private volatile long lastCleanup = System.currentTimeMillis();

    public RateLimiter(int maxRequests, long timeWindowMs) {
        this.maxRequests = maxRequests;
        this.timeWindowMs = timeWindowMs;
    }

    public boolean allowRequest(String ip) {
        long now = System.currentTimeMillis();
        cleanup(now);
        RequestCount count = requestCounts.compute(ip, (key, val) ->
                (val == null || now - val.timestamp > timeWindowMs) ? new RequestCount(now) : val);
        return count.increment() <= maxRequests;
    }

    /** Drops expired entries so many different client IPs cannot grow the map forever. */
    private void cleanup(long now) {
        if (now - lastCleanup < timeWindowMs) {
            return;
        }
        lastCleanup = now;
        requestCounts.values().removeIf(c -> now - c.timestamp > timeWindowMs);
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
