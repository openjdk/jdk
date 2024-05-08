package sun.security.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

final class QueueCacheEntry<K,V> extends SoftReference<V>
    implements CacheEntry<K,V> {

    private K key;
    private long expirationTime;
    Queue<CacheEntry<K,V>> queue = new ConcurrentLinkedQueue<>();
    ReentrantLock lock;

    QueueCacheEntry(K key, V value, long expirationTime,
        ReferenceQueue<V> queue) {
        super(value, queue);
        this.key = key;
        this.expirationTime = expirationTime;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        do {
            var entry = queue.poll();
            if (entry != null) {
                return entry.getValue();
            }
        } while (!queue.isEmpty());

        return null;
    }

    public V getValue(long lifetime) {
        long time = (lifetime == 0) ? 0 : System.currentTimeMillis();
        do {
            var entry = queue.poll();
            if (entry != null && entry.isValid(time)) {
                return entry.getValue();
            }
        } while (!queue.isEmpty());

        return null;
    }

    public CacheEntry<K,V> getEntry() {
        //lock.lock();
        var entry = queue.poll();
        //lock.unlock();
        return entry;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(long time) {
        expirationTime = time;
    }

    //public boolean putValue(CacheEntry<K,V> entry) {
    public boolean putValue(CacheEntry<K,V> entry) {
       // lock.lock();
        queue.add(entry);
       // lock.unlock();
        return true;
    }

    public boolean isValid(long currentTime) {
        boolean valid = (currentTime <= expirationTime) && (get() != null);
        if (!valid) {
            invalidate();
        }
        return valid;
    }

    public boolean isValid() {
        return isValid(System.currentTimeMillis());
    }

    public void invalidate() {
        clear();
        key = null;
        expirationTime = -1;
    }

    public void clear() {
        queue.stream().forEach(e -> e.invalidate());
        queue.clear();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public Queue<CacheEntry<K,V>> getQueue() {
        return queue;
    }

    /**
     * Trim queue to at most the 'left' value.  This design saves repeated
     * queue.size() calls to make sure queue.size() == 'left'.
     */
    public void trim(int left) {
        int i = 0, size = queue.size();
        CacheEntry<K,V> entry;
        do {
            entry = queue.poll();
            i++;
        } while (entry != null && size < i );
    }

}
