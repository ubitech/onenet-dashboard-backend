package eu.ubitech.onenet.service;

import org.springframework.stereotype.Service;

import eu.ubitech.onenet.config.PropertiesConfiguration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;

@Slf4j
@Service
// Creates a buckets (if there isn't one already) for each API key. These
// buckets are used to rate limit excessive API calls
public class RateLimitingService {

    private final Map<String, Bucket> cache;
    private final PropertiesConfiguration config;

    @Autowired
    public RateLimitingService(PropertiesConfiguration config) {
        this.cache = new ConcurrentHashMap<>();
        this.config = config;
    }

    public Bucket resolveBucket(String apiKey) {
        log.info("hashmap size: {}", cache.size());
        return cache.computeIfAbsent(apiKey, this::newBucket);
    }

    private Bucket newBucket(String apiKey) {
        Bandwidth limit = Bandwidth.classic(this.config.getRateLimit().getCapacity(),
                Refill.intervally(this.config.getRateLimit().getTokenRefill(),
                        Duration.ofMinutes(this.config.getRateLimit().getRefillIntervalInMinutes())));
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

    // clear the hashmap at 4:00 AM every day so that it doesn't overflow with keys
    // and search time increases
    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
    public void scheduleFixedDelayTask() {
        cache.clear();
    }

}