package sun.security.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

final class SoftCacheEntry<K,V> extends SoftReference<V>
    implements CacheEntry<K,V> {

    private K key;
    private long expirationTime;

    SoftCacheEntry(K key, V value, long expirationTime,
        ReferenceQueue<V> queue) {
        super(value, queue);
        this.key = key;
        this.expirationTime = expirationTime;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return get();
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public boolean isValid(long currentTime) {
        boolean valid = (currentTime <= expirationTime) && (get() != null);
        if (!valid) {
            invalidate();
        }
        return valid;
    }

    public void invalidate() {
        clear();
        key = null;
        expirationTime = -1;
    }
}