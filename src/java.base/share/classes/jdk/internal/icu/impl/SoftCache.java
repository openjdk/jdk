// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
*******************************************************************************
*   Copyright (C) 2010-2016, International Business Machines
*   Corporation and others.  All Rights Reserved.
*******************************************************************************
*/
package jdk.internal.icu.impl;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic, thread-safe cache implementation, usually storing cached instances
 * in {@link java.lang.ref.Reference}s via {@link CacheValue}s.
 * To use, instantiate a subclass which implements the createInstance() method,
 * and call get() with the key and the data. The get() call will use the data
 * only if it needs to call createInstance(), otherwise the data is ignored.
 *
 * <p>When caching instances while the CacheValue "strength" is {@code SOFT},
 * the Java runtime can later release these instances once they are not used any more at all.
 * If such an instance is then requested again,
 * the getInstance() method will call createInstance() again and reset the CacheValue.
 * The cache holds on to its map of keys to CacheValues forever.
 *
 * <p>A value can be null if createInstance() returns null.
 * In this case, it must do so consistently for the same key and data.
 *
 * @param <K> Cache lookup key type
 * @param <V> Cache instance value type (must not be a CacheValue)
 * @param <D> Data type for creating a new instance value
 *
 * @author Markus Scherer, Mark Davis
 */
public abstract class SoftCache<K, V, D> extends CacheBase<K, V, D> {
    private ConcurrentHashMap<K, Object> map = new ConcurrentHashMap<K, Object>();

    @SuppressWarnings("unchecked")
    @Override
    public final V getInstance(K key, D data) {
        // We synchronize twice, once in the ConcurrentHashMap and
        // once in valueRef.resetIfCleared(value),
        // because we prefer the fine-granularity locking of the ConcurrentHashMap
        // over coarser locking on the whole cache instance.
        // We use a CacheValue (a second level of indirection) because
        // ConcurrentHashMap.putIfAbsent() never replaces the key's value, and if it were
        // a simple Reference we would not be able to reset its value after it has been cleared.
        // (And ConcurrentHashMap.put() always replaces the value, which we don't want either.)
        Object mapValue = map.get(key);
        if(mapValue != null) {
            if(!(mapValue instanceof CacheValue)) {
                // The value was stored directly.
                return (V)mapValue;
            }
            CacheValue<V> cv = (CacheValue<V>)mapValue;
            if(cv.isNull()) {
                return null;
            }
            V value = cv.get();
            if(value != null) {
                return value;
            }
            // The instance has been evicted, its Reference cleared.
            // Create and set a new instance.
            value = createInstance(key, data);
            return cv.resetIfCleared(value);
        } else /* valueRef == null */ {
            // We had never cached an instance for this key.
            V value = createInstance(key, data);
            mapValue = (value != null && CacheValue.futureInstancesWillBeStrong()) ?
                    value : CacheValue.getInstance(value);
            mapValue = map.putIfAbsent(key, mapValue);
            if(mapValue == null) {
                // Normal "put": Our new value is now cached.
                return value;
            }
            // Race condition: Another thread beat us to putting a CacheValue
            // into the map. Return its value, but just in case the garbage collector
            // was aggressive, we also offer our new instance for caching.
            if(!(mapValue instanceof CacheValue)) {
                // The value was stored directly.
                return (V)mapValue;
            }
            CacheValue<V> cv = (CacheValue<V>)mapValue;
            return cv.resetIfCleared(value);
        }
    }
}
