package org.example.supabase;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory cache for Supabase query results.
 * Entries expire after TTL seconds.
 *
 * Usage:
 *   DataCache cache = DataCache.getInstance();
 *   JsonNode cached = cache.get("subjects");
 *   if (cached == null) {
 *       cached = SupabaseClient.getInstance().from("subjects")...execute();
 *       cache.put("subjects", cached, 300); // cache 5 minutes
 *   }
 */
public class DataCache {

    private static DataCache instance;

    private record Entry(JsonNode data, Instant expiresAt) {
        boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private DataCache() {}

    public static synchronized DataCache getInstance() {
        if (instance == null) instance = new DataCache();
        return instance;
    }

    /** Get cached value, returns null if missing or expired. */
    public JsonNode get(String key) {
        Entry e = store.get(key);
        if (e == null || e.isExpired()) {
            store.remove(key);
            return null;
        }
        return e.data();
    }

    /** Store value with TTL in seconds. */
    public void put(String key, JsonNode data, int ttlSeconds) {
        if (data != null)
            store.put(key, new Entry(data, Instant.now().plusSeconds(ttlSeconds)));
    }

    /** Invalidate a specific key (e.g. after a write). */
    public void invalidate(String key) { store.remove(key); }

    /** Invalidate all keys matching a prefix. */
    public void invalidatePrefix(String prefix) {
        store.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** Clear everything. */
    public void clear() { store.clear(); }
}