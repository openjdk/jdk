/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.util;

import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;

import org.graalvm.util.impl.EconomicMapImpl;

/**
 * Memory efficient map data structure.
 */
public interface EconomicMap<K, V> extends UnmodifiableEconomicMap<K, V> {

    V put(K key, V value);

    default void putAll(EconomicMap<K, V> other) {
        MapCursor<K, V> e = other.getEntries();
        while (e.advance()) {
            put(e.getKey(), e.getValue());
        }
    }

    void clear();

    V removeKey(K key);

    @Override
    MapCursor<K, V> getEntries();

    void replaceAll(BiFunction<? super K, ? super V, ? extends V> function);

    default void putAll(UnmodifiableEconomicMap<? extends K, ? extends V> other) {
        UnmodifiableMapCursor<? extends K, ? extends V> entry = other.getEntries();
        while (entry.advance()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Creates a new map that guarantees insertion order on the key set with the default
     * {@link Equivalence#DEFAULT} comparison strategy for keys.
     */
    static <K, V> EconomicMap<K, V> create() {
        return EconomicMap.create(Equivalence.DEFAULT);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set with the default
     * {@link Equivalence#DEFAULT} comparison strategy for keys and initializes with a specified
     * capacity.
     */
    static <K, V> EconomicMap<K, V> create(int initialCapacity) {
        return EconomicMap.create(Equivalence.DEFAULT, initialCapacity);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set with the given comparison
     * strategy for keys.
     */
    static <K, V> EconomicMap<K, V> create(Equivalence strategy) {
        return EconomicMapImpl.create(strategy);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set with the default
     * {@link Equivalence#DEFAULT} comparison strategy for keys and copies all elements from the
     * specified existing map.
     */
    static <K, V> EconomicMap<K, V> create(UnmodifiableEconomicMap<K, V> m) {
        return EconomicMap.create(Equivalence.DEFAULT, m);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set and copies all elements from
     * the specified existing map.
     */
    static <K, V> EconomicMap<K, V> create(Equivalence strategy, UnmodifiableEconomicMap<K, V> m) {
        return EconomicMapImpl.create(strategy, m);
    }

    /**
     * Creates a new map that guarantees insertion order on the key set and initializes with a
     * specified capacity.
     */
    static <K, V> EconomicMap<K, V> create(Equivalence strategy, int initialCapacity) {
        return EconomicMapImpl.create(strategy, initialCapacity);
    }

    /**
     * Wraps an existing {@link java.util.Map} as an {@link org.graalvm.util.EconomicMap}.
     */
    static <K, V> EconomicMap<K, V> wrapMap(Map<K, V> map) {
        return new EconomicMap<K, V>() {

            @Override
            public V get(K key) {
                V result = map.get(key);
                return result;
            }

            @Override
            public V put(K key, V value) {
                V result = map.put(key, value);
                return result;
            }

            @Override
            public int size() {
                int result = map.size();
                return result;
            }

            @Override
            public boolean containsKey(K key) {
                return map.containsKey(key);
            }

            @Override
            public void clear() {
                map.clear();
            }

            @Override
            public V removeKey(K key) {
                V result = map.remove(key);
                return result;
            }

            @Override
            public Iterable<V> getValues() {
                return map.values();
            }

            @Override
            public Iterable<K> getKeys() {
                return map.keySet();
            }

            @Override
            public boolean isEmpty() {
                return map.isEmpty();
            }

            @Override
            public MapCursor<K, V> getEntries() {
                Iterator<java.util.Map.Entry<K, V>> iterator = map.entrySet().iterator();
                return new MapCursor<K, V>() {

                    private Map.Entry<K, V> current;

                    @Override
                    public boolean advance() {
                        boolean result = iterator.hasNext();
                        if (result) {
                            current = iterator.next();
                        }

                        return result;
                    }

                    @Override
                    public K getKey() {
                        return current.getKey();
                    }

                    @Override
                    public V getValue() {
                        return current.getValue();
                    }

                    @Override
                    public void remove() {
                        iterator.remove();
                    }
                };
            }

            @Override
            public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
                map.replaceAll(function);
            }
        };
    }
}
