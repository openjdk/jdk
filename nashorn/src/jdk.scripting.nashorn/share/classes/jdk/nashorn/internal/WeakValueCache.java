/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.function.Function;

/**
 * This class provides a map based cache with weakly referenced values. Cleared references are
 * purged from the underlying map when values are retrieved or created.
 * It uses a {@link java.util.HashMap} to store values and needs to be externally synchronized.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public final class WeakValueCache<K, V> {

    private final HashMap<K, KeyValueReference<K, V>> map = new HashMap<>();
    private final ReferenceQueue<V> refQueue = new ReferenceQueue<>();

    /**
     * Returns the value associated with {@code key}, or {@code null} if no such value exists.
     *
     * @param key the key
     * @return the value or null if none exists
     */
    public V get(final K key) {
        removeClearedEntries();
        return findValue(key);
    }

    /**
     * Returns the value associated with {@code key}, or creates and returns a new value if
     * no value exists using the {@code creator} function.
     *
     * @param key the key
     * @param creator function to create a new value
     * @return the existing value, or a new one if none existed
     */
    public V getOrCreate(final K key, final Function<? super K, ? extends V> creator) {
        removeClearedEntries();

        V value = findValue(key);

        if (value == null) {
            // Define a new value if it does not exist
            value = creator.apply(key);
            map.put(key, new KeyValueReference<>(key, value));
        }

        return value;
    }

    private V findValue(final K key) {
        final KeyValueReference<K, V> ref = map.get(key);
        if (ref != null) {
            return ref.get();
        }
        return null;
    }

    private void removeClearedEntries() {
        // Remove cleared entries
        for (;;) {
            final KeyValueReference ref = (KeyValueReference) refQueue.poll();
            if (ref == null) {
                break;
            }
            map.remove(ref.key, ref);
        }
    }

    private static class KeyValueReference<K, V> extends WeakReference<V> {
        final K key;

        KeyValueReference(final K key, final V value) {
            super(value);
            this.key = key;
        }
    }

}
