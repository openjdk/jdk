/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 * A map that combines {@link IdentityHashMap} with {@link LinkedHashMap} for the purpose of
 * ensuring a deterministic execution order during a capturing compilation.
 */
final class LinkedIdentityHashMap<K, V> implements Map<K, V> {

    private final LinkedHashMap<Id<K>, V> map;

    LinkedIdentityHashMap() {
        map = new LinkedHashMap<>();
    }

    LinkedIdentityHashMap(Map<K, V> m) {
        map = new LinkedHashMap<>(m.size());
        putAll(m);
    }

    LinkedIdentityHashMap(int expectedMaxSize) {
        map = new LinkedHashMap<>(expectedMaxSize);
    }

    /**
     * Wrapper for an object that gives uses the object's identity for the purpose of equality
     * comparisons and computing a hash code.
     */
    static final class Id<T> {
        final T object;

        Id(T object) {
            assert object != null;
            this.object = object;
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean equals(Object obj) {
            return obj instanceof Id && ((Id<T>) obj).object == object;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(object);
        }
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(id(key));
    }

    @SuppressWarnings("unchecked")
    private Id<K> id(Object key) {
        if (key == null) {
            return null;
        }
        return new Id<>((K) key);
    }

    @Override
    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    @Override
    public V get(Object key) {
        return map.get(id(key));
    }

    @Override
    public V put(K key, V value) {
        return map.put(id(key), value);
    }

    @Override
    public V remove(Object key) {
        return map.remove(id(key));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void putAll(Map<? extends K, ? extends V> m) {
        if (m == null) {
            throw new NullPointerException();
        }
        if (m.getClass() == getClass()) {
            LinkedIdentityHashMap<K, V> that = (LinkedIdentityHashMap<K, V>) m;
            map.putAll(that.map);

        } else {
            for (K key : m.keySet()) {
                map.put(id(key), m.get(key));
            }
        }
    }

    @Override
    public void clear() {
        map.clear();
    }

    final class KeySet extends AbstractSet<K> {
        @Override
        public int size() {
            return map.size();
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public Iterator<K> iterator() {
            return new Iterator<K>() {
                final Iterator<Id<K>> i = map.keySet().iterator();

                @Override
                public boolean hasNext() {
                    return i.hasNext();
                }

                @Override
                public K next() {
                    return i.next().object;
                }

                @Override
                public void remove() {
                    i.remove();
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            return containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            return LinkedIdentityHashMap.this.remove(o) != null;
        }

        @Override
        public Spliterator<K> spliterator() {
            return Spliterators.spliterator(this, Spliterator.SIZED | Spliterator.ORDERED | Spliterator.DISTINCT);
        }

        @Override
        public void forEach(Consumer<? super K> action) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Set<K> keySet() {
        return new KeySet();
    }

    @Override
    public Collection<V> values() {
        return map.values();
    }

    final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        @Override
        public int size() {
            return map.size();
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new Iterator<Map.Entry<K, V>>() {
                final Iterator<Map.Entry<Id<K>, V>> i = map.entrySet().iterator();

                @Override
                public boolean hasNext() {
                    return i.hasNext();
                }

                @Override
                public Map.Entry<K, V> next() {
                    Map.Entry<Id<K>, V> e = i.next();
                    return new Map.Entry<K, V>() {

                        @Override
                        public K getKey() {
                            return e.getKey().object;
                        }

                        @Override
                        public V getValue() {
                            return e.getValue();
                        }

                        @Override
                        public V setValue(V value) {
                            return e.setValue(value);
                        }
                    };
                }

                @Override
                public void remove() {
                    i.remove();
                }
            };
        }

        @Override
        public boolean contains(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Spliterator<Map.Entry<K, V>> spliterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void forEach(Consumer<? super Map.Entry<K, V>> action) {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new EntrySet();
    }
}
