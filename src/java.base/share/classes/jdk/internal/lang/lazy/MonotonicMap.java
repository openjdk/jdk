/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang.lazy;

import jdk.internal.vm.annotation.Stable;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class MonotonicMap<K, V>
        extends AbstractMap<K, Lazy<V>>
        implements Map<K, Lazy<V>> {

    private static final long SALT32L = Long.MAX_VALUE; // Todo: reuse from ImmutableCollections
    private static final int EXPAND_FACTOR = 2;

    @Stable
    private final Object[] table; // pairs of key, value
    @Stable
    private final int size; // number of pairs

    // keys array not trusted
    @SuppressWarnings("unchecked")
    MonotonicMap(Object[] keys) {
        this.size = keys.length;

        int len = EXPAND_FACTOR * keys.length * 2;
        len = (len + 1) & ~1; // ensure table is even length
        Object[] table = new Object[len];

        for (Object key : keys) {
            @SuppressWarnings("unchecked")
            K k = Objects.requireNonNull((K) key);
            int idx = probe(table, k);
            if (idx >= 0) {
                throw new IllegalArgumentException("duplicate key: " + k);
            } else {
                int dest = -(idx + 1);
                table[dest] = k;
            }
        }

        LazyUtil.freeze(); // ensure keys are visible if table is visible
        this.table = table;
    }

    // returns index at which the probe key is present; or if absent,
    // (-i - 1) where i is location where element should be inserted.
    // Callers are relying on this method to perform an implicit nullcheck
    // of pk.
    private int probe(Object pk) {
        return probe(table, pk);
    }

    private int probe(Object[] table, Object pk) {
        int idx = Math.floorMod(pk.hashCode(), table.length >> 1) << 1;
        while (true) {
            @SuppressWarnings("unchecked")
            K ek = (K) table[idx];
            if (ek == null) {
                return -idx - 1;
            } else if (pk.equals(ek)) {
                return idx;
            } else if ((idx += 2) == table.length) {
                idx = 0;
            }
        }
    }

    private Lazy<V> value(int keyIndex) {
        @SuppressWarnings("unchecked")
        Lazy<V> m = (Lazy<V>) table[keyIndex + 1];
        if (m != null) {
            return m;
        }
        return slowValue(keyIndex);
    }

    @SuppressWarnings("unchecked")
    private Lazy<V> slowValue(int keyIndex) {
        Lazy<V> m = (Lazy<V>) tableItemVolatile(keyIndex + 1);
        if (m != null) {
            return m;
        }
        // racy, only use the one who uploaded first
        return (Lazy<V>) caeTableItemWitness(keyIndex + 1, Lazy.of());
    }

    private Object tableItemVolatile(int index) {
        return LazyUtil.UNSAFE.getReferenceVolatile(table, LazyUtil.objectOffset(index));
    }

    private Object caeTableItemWitness(int index, Object o) {
        // Make sure no reordering of store operations
        LazyUtil.freeze();
        var w = LazyUtil.UNSAFE.compareAndExchangeReference(table, LazyUtil.objectOffset(index), null, o);
        return w == null ? o : w;
    }

    @Override
    public boolean containsKey(Object o) {
        Objects.requireNonNull(o);
        return size > 0 && probe(o) >= 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public Lazy<V> get(Object key) {
        if (size == 0) {
            return null;
        }
        int i = probe(key);
        if (i >= 0) {
            return value(i);
        } else {
            return null;
        }
    }

    @Override
    public Set<Entry<K, Lazy<V>>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public int size() {
                return size;
            }

            @Override
            public Iterator<Entry<K, Lazy<V>>> iterator() {
                return new Itr();
            }
        };
    }

    final class Itr implements Iterator<Entry<K, Lazy<V>>> {
        private int remaining;

        private int idx;

        Itr() {
            remaining = size;
            // pick an even starting index in the [0 .. table.length-1]
            // range randomly based on SALT32L
            idx = (int) ((SALT32L * (table.length >> 1)) >>> 32) << 1;
        }

        @Override
        public boolean hasNext() {
            return remaining > 0;
        }

        private int nextIndex() {
            int idx = this.idx;
            if ((idx += 2) >= table.length) {
                idx = 0;
            }
            return this.idx = idx;
        }

        @Override
        public Map.Entry<K, Lazy<V>> next() {
            if (remaining > 0) {
                int idx;
                while (table[idx = nextIndex()] == null) {
                }
                @SuppressWarnings("unchecked")
                Map.Entry<K, Lazy<V>> e =
                        java.util.Map.entry((K) table[idx], value(idx));
                remaining--;
                return e;
            } else {
                throw new NoSuchElementException();
            }
        }
    }

    // all mutating methods throw UnsupportedOperationException
    @Override public Lazy<V> put(K key, Lazy<V> value) {throw LazyUtil.uoe();}
    @Override public Lazy<V> remove(Object key) {throw LazyUtil.uoe();}
    @Override public void putAll(Map<? extends K, ? extends Lazy<V>> m) {throw LazyUtil.uoe();}
    @Override public void clear() {throw LazyUtil.uoe();}
    @Override public void replaceAll(BiFunction<? super K, ? super Lazy<V>, ? extends Lazy<V>> function) {throw LazyUtil.uoe();}
    @Override public Lazy<V> putIfAbsent(K key, Lazy<V> value) {throw LazyUtil.uoe();}
    @Override public boolean remove(Object key, Object value) {throw LazyUtil.uoe();}
    @Override public boolean replace(K key, Lazy<V> oldValue, Lazy<V> newValue) {throw LazyUtil.uoe();}
    @Override public Lazy<V> replace(K key, Lazy<V> value) {throw LazyUtil.uoe();}
    @Override public Lazy<V> computeIfAbsent(K key, Function<? super K, ? extends Lazy<V>> mappingFunction) {throw LazyUtil.uoe();}
    @Override public Lazy<V> computeIfPresent(K key, BiFunction<? super K, ? super Lazy<V>, ? extends Lazy<V>> remappingFunction) {throw LazyUtil.uoe();}
    @Override public Lazy<V> compute(K key, BiFunction<? super K, ? super Lazy<V>, ? extends Lazy<V>> remappingFunction) {throw LazyUtil.uoe();}
    @Override public Lazy<V> merge(K key, Lazy<V> value, BiFunction<? super Lazy<V>, ? super Lazy<V>, ? extends Lazy<V>> remappingFunction) {throw LazyUtil.uoe();}

    // Factories

    public static <K, V> Map<K, Lazy<V>> ofMap(Object[] keys) {
        return new MonotonicMap<>(keys);
    }

    public static <K, V> V computeIfUnbound(Map<K, Lazy<V>> map,
                                            K key,
                                            Function<? super K, ? extends V> mapper) {
        Lazy<V> lazy = monotonicOrThrow(map, key);
        if (lazy.isSet()) {
            return lazy.orThrow();
        }
        V newValue = mapper.apply(key);
        return lazy.setIfUnset(newValue);
    }

/*
    public static <K, V> Function<K, V> asFunction(Set<? extends K> keys,
                                                   Function<? super K, ? extends V> mapper) {
        Map<K, Monotonic<V>> map = Monotonic.ofMap(keys);
        Function<K, V> guardedMapper = new Function<>() {

            @Override
            public V apply(K key) {
                Monotonic<V> monotonic = monotonicOrThrow(map, key);
                synchronized (monotonic) {
                    if (monotonic.isBound()) {
                        return monotonic.orThrow();
                    }
                }
                return mapper.apply(key);
            }

        };
        return new Function<>() {
            @Override
            public V apply(K key) {
                return computeIfUnbound(map, key, guardedMapper);
            }
        };
    }
*/

    private static <K, V> Lazy<V> monotonicOrThrow(Map<K, Lazy<V>> map, K key) {
        Lazy<V> lazy = map.get(key);
        if (lazy == null) {
            throw new IllegalArgumentException("No such key:" + key);
        }
        return lazy;
    }

}
