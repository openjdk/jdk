/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.dyn;

import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lazily associate a computed value with (potentially) every class.
 * @author John Rose, JSR 292 EG
 */
public abstract class ClassValue<T> {
    /**
     * Compute the given class's derived value for this {@code ClassValue}.
     * <p>
     * This method will be invoked within the first thread that accesses
     * the value with the {@link #get}.
     * <p>
     * Normally, this method is invoked at most once per class,
     * but it may be invoked again in case of subsequent invocations
     * of {@link #remove} followed by {@link #get}.
     *
     * @return the computed value for this thread-local
     */
    protected abstract T computeValue(Class<?> type);

    /**
     * Creates a new class value.
     */
    protected ClassValue() {
    }

    /**
     * Returns the value for the given class.
     * If no value has yet been computed, it is obtained by
     * by an invocation of the {@link #computeValue} method.
     * <p>
     * The actual installation of the value on the class
     * is performed while the class's synchronization lock
     * is held.  At that point, if racing threads have
     * computed values, one is chosen, and returned to
     * all the racing threads.
     *
     * @return the current thread's value of this thread-local
     */
    public T get(Class<?> type) {
        ClassValueMap map = getMap(type);
        if (map != null) {
            Object x = map.get(this);
            if (x != null) {
                return (T) map.unmaskNull(x);
            }
        }
        return setComputedValue(type);
    }

    /**
     * Removes the associated value for the given class.
     * If this value is subsequently {@linkplain #get read} for the same class,
     * its value will be reinitialized by invoking its {@link #computeValue} method.
     * This may result in an additional invocation of the
     * {@code computeValue} method for the given class.
     */
    public void remove(Class<?> type) {
        ClassValueMap map = getMap(type);
        if (map != null) {
            synchronized (map) {
                map.remove(this);
            }
        }
    }

    /// Implementation...

    /** The hash code for this type is based on the identity of the object,
     *  and is well-dispersed for power-of-two tables.
     */
    public final int hashCode() { return hashCode; }
    private final int hashCode = HASH_CODES.getAndAdd(0x61c88647);
    private static final AtomicInteger HASH_CODES = new AtomicInteger();

    private static final AtomicInteger STORE_BARRIER = new AtomicInteger();

    /** Slow path for {@link #get}. */
    private T setComputedValue(Class<?> type) {
        ClassValueMap map = getMap(type);
        if (map == null) {
            map = initializeMap(type);
        }
        T value = computeValue(type);
        STORE_BARRIER.lazySet(0);
        // All stores pending from computeValue are completed.
        synchronized (map) {
            // Warm up the table with a null entry.
            map.preInitializeEntry(this);
        }
        // All stores pending from table expansion are completed.
        synchronized (map) {
            value = (T) map.initializeEntry(this, value);
            // One might fear a possible race condition here
            // if the code for map.put has flushed the write
            // to map.table[*] before the writes to the Map.Entry
            // are done.  This is not possible, since we have
            // warmed up the table with an empty entry.
        }
        return value;
    }

    // Replace this map by a per-class slot.
    private static final WeakHashMap<Class<?>, ClassValueMap> ROOT
        = new WeakHashMap<Class<?>, ClassValueMap>();

    private static ClassValueMap getMap(Class<?> type) {
        return ROOT.get(type);
    }

    private static ClassValueMap initializeMap(Class<?> type) {
        synchronized (ClassValue.class) {
            ClassValueMap map = ROOT.get(type);
            if (map == null)
                ROOT.put(type, map = new ClassValueMap());
            return map;
        }
    }

    static class ClassValueMap extends WeakHashMap<ClassValue, Object> {
        /** Make sure this table contains an Entry for the given key, even if it is empty. */
        void preInitializeEntry(ClassValue key) {
            if (!this.containsKey(key))
                this.put(key, null);
        }
        /** Make sure this table contains a non-empty Entry for the given key. */
        Object initializeEntry(ClassValue key, Object value) {
            Object prior = this.get(key);
            if (prior != null) {
                return unmaskNull(prior);
            }
            this.put(key, maskNull(value));
            return value;
        }

        Object maskNull(Object x) {
            return x == null ? this : x;
        }
        Object unmaskNull(Object x) {
            return x == this ? null : x;
        }
    }
}
