package com.parishod.watomatic.model.classifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ClassificationCacheTest {

    /** Fake cache that lets tests advance the clock deterministically. */
    private static class TestCache extends ClassificationCache {
        long currentMs = 1_000_000L;

        TestCache(long ttlMs) { super(ttlMs); }

        @Override
        protected long now() { return currentMs; }
    }

    @Test
    public void put_and_get_returnsSameResult() {
        TestCache cache = new TestCache(60_000L);
        ClassificationResult r = new ClassificationResult("work", 0.9f, "meeting", 0L, false);

        cache.put("com.whatsapp", "Boss", r);
        ClassificationResult got = cache.get("com.whatsapp", "Boss");

        assertNotNull(got);
        assertEquals("work", got.getCategoryId());
    }

    @Test
    public void get_miss_returnsNull() {
        TestCache cache = new TestCache(60_000L);
        assertNull(cache.get("com.whatsapp", "Nobody"));
    }

    @Test
    public void ttl_expired_returnsNull() {
        TestCache cache = new TestCache(/*ttlMs=*/ 1_000L);
        ClassificationResult r = new ClassificationResult("work", 0.9f, "x", 0L, false);
        cache.put("p", "s", r);

        cache.currentMs += 1_500L;  // advance past TTL
        assertNull(cache.get("p", "s"));
    }

    @Test
    public void fallback_results_are_NOT_cached() {
        TestCache cache = new TestCache(60_000L);
        ClassificationResult fb = ClassificationResult.fallback("network");
        cache.put("p", "s", fb);
        assertNull("fallback must not be cached", cache.get("p", "s"));
    }

    @Test
    public void differentSenders_donotCollide() {
        TestCache cache = new TestCache(60_000L);
        cache.put("p", "Alice", new ClassificationResult("work", 0.9f, "", 0, false));
        cache.put("p", "Bob",   new ClassificationResult("spam", 0.99f, "", 0, false));

        assertEquals("work", cache.get("p", "Alice").getCategoryId());
        assertEquals("spam", cache.get("p", "Bob").getCategoryId());
    }

    @Test
    public void clear_emptiesCache() {
        TestCache cache = new TestCache(60_000L);
        cache.put("p", "s", new ClassificationResult("work", 0.9f, "", 0, false));
        cache.clear();
        assertEquals(0, cache.size());
    }
}
