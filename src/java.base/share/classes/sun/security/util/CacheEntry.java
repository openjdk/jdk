package sun.security.util;

public interface CacheEntry<K,V> {

    boolean isValid(long currentTime);

    void invalidate();

    K getKey();

    V getValue();

    long getExpirationTime();
}
