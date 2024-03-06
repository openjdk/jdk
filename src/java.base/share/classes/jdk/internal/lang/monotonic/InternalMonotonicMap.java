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

package jdk.internal.lang.monotonic;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandle;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import static jdk.internal.lang.monotonic.MonotonicUtil.UNSAFE;
import static jdk.internal.lang.monotonic.MonotonicUtil.uoe;

public sealed interface InternalMonotonicMap<K, V>
        extends Monotonic.Map<K, V> {

    final class MonotonicMapImpl<K, V>
            extends AbstractMap<K, V>
            implements InternalMonotonicMap<K, V> {

        private static final long SALT32L = Long.MAX_VALUE; // TODO dummy; also investigate order
        private static final int EXPAND_FACTOR = 2;

        @Stable
        private final Class<V> backingValueType;
        @Stable
        private final Object[] table; // pairs of key, value
        @Stable
        private final int size; // number of pairs

        // keys array not trusted
        @SuppressWarnings("unchecked")
        public MonotonicMapImpl(Class<? extends V> backingValueType, Object[] keys) {
            this.backingValueType = (Class<V>)backingValueType;
            this.size = keys.length;

            int len = EXPAND_FACTOR * keys.length * 2;
            len = (len + 1) & ~1; // ensure table is even length
            Object[] table = new Object[len];

            for (Object key : keys) {
                @SuppressWarnings("unchecked")
                K k = Objects.requireNonNull((K) key);
                int idx = probe(k);
                if (idx >= 0) {
                    throw new IllegalArgumentException("duplicate key: " + k);
                } else {
                    int dest = -(idx + 1);
                    table[dest] = k;
                }
            }

            UNSAFE.storeFence(); // ensure keys are visible if table is visible
            this.table = table;
        }

        // returns index at which the probe key is present; or if absent,
        // (-i - 1) where i is location where element should be inserted.
        // Callers are relying on this method to perform an implicit nullcheck
        // of pk.
        private int probe(Object pk) {
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

        private V value(int keyIndex) {
            @SuppressWarnings("unchecked")
            V cc = (V) table[keyIndex + 1];
            if (cc != null) {
                return cc;
            }
            return slowValue(keyIndex);
        }

        @SuppressWarnings("unchecked")
        private V slowValue(int keyIndex) {
            V cc = (V) getTableItemVolatile(keyIndex + 1);
            if (cc != null) {
                return cc;
            }
            // racy, only use the one who uploaded first
            return (V) caeTableItemVolatile(keyIndex, Monotonic.of(backingValueType));
        }

        private Object getTableItemVolatile(int index) {
            return UNSAFE.getReferenceVolatile(table, offset(index));
        }

        private Object caeTableItemVolatile(int index, Object o) {
            var w = UNSAFE.compareAndExchangeReference(table, offset(index), null, o);
            return w == null ? o : w;
        }

        private static long offset(int index) {
            return Unsafe.ARRAY_OBJECT_BASE_OFFSET + (long) index * Unsafe.ARRAY_OBJECT_INDEX_SCALE;
        }


/*        @Override
        public V computeIfAbsent(K key, MethodHandle mapper) {
            throw new UnsupportedOperationException();
        }*/

/*
        @Override
        public MethodHandle getter() {
            throw new UnsupportedOperationException();
        }
*/

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
        public V get(Object key) {
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
        public Set<Entry<K, V>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public int size() {
                    return size;
                }

                @Override
                public Iterator<Entry<K, V>> iterator() {
                    return new Itr();
                }
            };
        }

        final class Itr implements Iterator<Entry<K, V>> {
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
            public Map.Entry<K, V> next() {
                if (remaining > 0) {
                    int idx;
                    while (table[idx = nextIndex()] == null) {
                    }
                    @SuppressWarnings("unchecked")
                    Map.Entry<K, V> e =
                            java.util.Map.entry((K) table[idx], value(idx));
                    remaining--;
                    return e;
                } else {
                    throw new NoSuchElementException();
                }
            }
        }

        // all mutating methods throw UnsupportedOperationException
        @Override public V put(K key, V value) {throw uoe();}
        @Override public V remove(Object key) {throw uoe();}
        @Override public void putAll(Map<? extends K, ? extends V> m) {throw uoe();}
        @Override public void clear() {throw uoe();}
        @Override public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {throw uoe();}
        @Override public V putIfAbsent(K key, V value) {throw uoe();}
        @Override public boolean remove(Object key, Object value) {throw uoe();}
        @Override public boolean replace(K key, V oldValue, V newValue) {throw uoe();}
        @Override public V replace(K key, V value) {throw uoe();}
        @Override public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {throw uoe();}
        @Override public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {throw uoe();}
        @Override public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {throw uoe();}
        @Override public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {throw uoe();}
    }
}