/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.sun.org.apache.xml.internal.security.utils;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Abstract base class for pooling objects.  The two public methods are
 * {@link #getObject()} and ({@link #repool(Object)}.  Objects are held through
 * weak references so even objects that are not repooled are subject to garbage collection.
 *
 * Subclasses must implement the abstract {@link #createObject()}.
 * <p>
 *
 * Internally, the pool is stored in a java.util.concurrent.LinkedBlockingDeque
 * instance.
 *
 * @deprecated This class is no longer in use in Santuario 2.1.4
 */
@Deprecated
public abstract class WeakObjectPool<T, E extends Throwable> {

    private static final Integer MARKER_VALUE = Integer.MAX_VALUE;//once here rather than auto-box it?

    /** created, available objects to be checked out to clients */
    private final BlockingQueue<WeakReference<T>> available;

    /**
     * Synchronized, identity map of loaned out objects (WeakHashMap);
     * use to ensure we repool only object originating from here
     * and do it once.
     */
    private final Map<T, Integer> onLoan;

    /**
     * The lone constructor.
     */
    protected WeakObjectPool() {
        //alternative implementations: ArrayBlockingQueue has a fixed size
        //  PriorityBlockingQueue: requires a dummy comparator; less memory but more overhead
        available = new LinkedBlockingDeque<WeakReference<T>>();
        this.onLoan = Collections.synchronizedMap(new WeakHashMap<T, Integer>());
    }

    /**
     * Called whenever a new pool object is desired; subclasses must implement.
     *
     * @return object of the type desired by the subclass
     * @throws E Throwable's subclass
     */
    protected abstract T createObject() throws E;


    /**
     * Subclasses can subclass to return a more specific type.
     *
     * @return an object from the pool; will block until an object is available
     * @throws E
     */
    public T getObject() throws E {
        WeakReference<T> ref;
        T retValue = null;
        do {
            //remove any stale entries as well
            ref = available.poll();
        } while (ref != null && (retValue = ref.get()) == null);

        if (retValue == null) {
            //empty pool; create & add new one
            retValue = createObject();
        }
        onLoan.put(retValue, MARKER_VALUE);
        return retValue;
    }


    /**
     * Adds the given object to the pool, provided that the object
     * was created by this pool.
     *
     * @param obj the object to return to the pool
     * @return whether the object was successfully added as available
     */
    public boolean repool(T obj) {
        if (obj != null && onLoan.containsKey(obj)) {
            //synchronize to protect against a caller returning the same object again...
            synchronized (obj) {
                //...and check to see that it was removed
                if (onLoan.remove(obj) != null) {
                    return available.offer(new WeakReference<T>(obj));
                }
            }
        }
        return false;
    }
}
