// Copyright 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html
/*
*******************************************************************************
*   Copyright (C) 2010, International Business Machines
*   Corporation and others.  All Rights Reserved.
*******************************************************************************
*/
package jdk.internal.icu.impl;

/**
 * Base class for cache implementations.
 * To use, instantiate a subclass of a concrete implementation class, where the subclass
 * implements the createInstance() method, and call get() with the key and the data.
 * The get() call will use the data only if it needs to call createInstance(),
 * otherwise the data is ignored.
 *
 * @param <K> Cache lookup key type
 * @param <V> Cache instance value type
 * @param <D> Data type for creating a new instance value
 *
 * @author Markus Scherer, Mark Davis
 */
public abstract class CacheBase<K, V, D> {
    /**
     * Retrieves an instance from the cache. Calls createInstance(key, data) if the cache
     * does not already contain an instance with this key.
     * Ignores data if the cache already contains an instance with this key.
     * @param key Cache lookup key for the requested instance
     * @param data Data for createInstance() if the instance is not already cached
     * @return The requested instance
     */
    public abstract V getInstance(K key, D data);
    /**
     * Creates an instance for the key and data. Must be overridden.
     * @param key Cache lookup key for the requested instance
     * @param data Data for the instance creation
     * @return The requested instance
     */
    protected abstract V createInstance(K key, D data);
}
