package com.parishod.watomatic.model.classifier;

import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory LRU cache of classification results keyed by {@code packageName + "|" + sender}.
 *
 * <p>Two invariants are load-bearing:</p>
 * <ul>
 *   <li><b>TTL</b>: entries older than {@link #ttlMs} are treated as misses on read.</li>
 *   <li><b>Fallbacks are not cached</b>: a transient network or parse error must not poison the
 *       cache for the next 10 minutes.</li>
 * </ul>
 */
public class ClassificationCache {

    private static final int MAX_ENTRIES = 256;

    private final long ttlMs;

    private static class CacheEntry {
        final ClassificationResult result;
        final long tsMs;

        CacheEntry(ClassificationResult result, long tsMs) {
            this.result = result;
            this.tsMs = tsMs;
        }
    }

    private final Map<String, CacheEntry> map = new LinkedHashMap<String, CacheEntry>(64, 0.75f, /*accessOrder=*/true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public ClassificationCache(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    @Nullable
    public synchronized ClassificationResult get(String packageName, String sender) {
        CacheEntry e = map.get(key(packageName, sender));
        if (e == null) return null;
        if (now() - e.tsMs > ttlMs) {
            map.remove(key(packageName, sender));
            return null;
        }
        return e.result;
    }

    public synchronized void put(String packageName, String sender, ClassificationResult r) {
        if (r == null || r.isFallback()) return;
        map.put(key(packageName, sender), new CacheEntry(r, now()));
    }

    public synchronized void clear() {
        map.clear();
    }

    public synchronized int size() {
        return map.size();
    }

    /** Override hook for tests. */
    protected long now() {
        return System.currentTimeMillis();
    }

    private static String key(String pkg, String sender) {
        return (pkg == null ? "" : pkg) + "|" + (sender == null ? "" : sender);
    }
}
