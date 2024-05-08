package sun.security.util;

final class HardCacheEntry<K,V> implements CacheEntry<K,V> {

    private K key;
    private V value;
    private long expirationTime;

    HardCacheEntry(K key, V value, long expirationTime) {
        this.key = key;
        this.value = value;
        this.expirationTime = expirationTime;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public boolean isValid(long currentTime) {
        boolean valid = (currentTime <= expirationTime);
        if (!valid) {
            invalidate();
        }
        return valid;
    }

    public void invalidate() {
        key = null;
        value = null;
        expirationTime = -1;
    }
}
