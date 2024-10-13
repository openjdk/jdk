/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.util;

/**
 * Provides a reversed-ordered view of a SortedMap. Not serializable.
 *
 * TODO: copy in equals and hashCode from AbstractMap
 */
class ReverseOrderSortedMapView<K, V> extends AbstractMap<K, V> implements SortedMap<K, V> {
    final SortedMap<K, V> base;
    final Comparator<? super K> cmp;

    private ReverseOrderSortedMapView(SortedMap<K, V> map) {
        base = map;
        cmp = Collections.reverseOrder(map.comparator());
    }

    public static <K, V> SortedMap<K, V> of(SortedMap<K, V> map) {
        if (map instanceof ReverseOrderSortedMapView<K, V> rosmv) {
            return rosmv.base;
        } else {
            return new ReverseOrderSortedMapView<>(map);
        }
    }

    // ========== Object ==========

    // equals: inherited from AbstractMap

    // hashCode: inherited from AbstractMap

    public String toString() {
        return toString(this, descendingEntryIterator(base));
    }

    // ========== Map ==========

    public void clear() {
        base.clear();
    }

    public boolean containsKey(Object key) {
        return base.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return base.containsValue(value);
    }

    public V get(Object key) {
        return base.get(key);
    }

    public boolean isEmpty() {
        return base.isEmpty();
    }

    public V put(K key, V value) {
        return base.put(key, value);
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        base.putAll(m);
    }

    public V remove(Object key) {
        return base.remove(key);
    }

    public int size() {
        return base.size();
    }

    public Set<K> keySet() {
        return new AbstractSet<>() {
            // inherit add(), which throws UOE
            public Iterator<K> iterator() { return descendingKeyIterator(base); }
            public int size() { return base.size(); }
            public void clear() { base.keySet().clear(); }
            public boolean contains(Object o) { return base.keySet().contains(o); }
            public boolean remove(Object o) { return base.keySet().remove(o); }
        };
    }

    public Collection<V> values() {
        return new AbstractCollection<>() {
            // inherit add(), which throws UOE
            public Iterator<V> iterator() { return descendingValueIterator(base); }
            public int size() { return base.size(); }
            public void clear() { base.values().clear(); }
            public boolean contains(Object o) { return base.values().contains(o); }
            public boolean remove(Object o) { return base.values().remove(o); }
        };
    }

    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            // inherit add(), which throws UOE
            public Iterator<Entry<K, V>> iterator() { return descendingEntryIterator(base); }
            public int size() { return base.size(); }
            public void clear() { base.entrySet().clear(); }
            public boolean contains(Object o) { return base.entrySet().contains(o); }
            public boolean remove(Object o) { return base.entrySet().remove(o); }
        };
    }

    // ========== SequencedMap ==========

    public SortedMap<K, V> reversed() {
        return base;
    }

    public K firstKey() {
        return base.lastKey();
    }

    public K lastKey() {
        return base.firstKey();
    }

    public Map.Entry<K, V> firstEntry() {
        return base.lastEntry();
    }

    public Map.Entry<K, V> lastEntry() {
        return base.firstEntry();
    }

    public Map.Entry<K,V> pollFirstEntry() {
        return base.pollLastEntry();
    }

    public Map.Entry<K,V> pollLastEntry() {
        return base.pollFirstEntry();
    }

    public V putFirst(K k, V v) {
        return base.putLast(k, v);
    }

    public V putLast(K k, V v) {
        return base.putFirst(k, v);
    }

    // ========== SortedMap ==========

    public Comparator<? super K> comparator() {
        return cmp;
    }

    public SortedMap<K, V> subMap(K fromKey, K toKey) {
        if (cmp.compare(fromKey, toKey) <= 0) {
            return new Submap(fromKey, toKey);
        } else {
            throw new IllegalArgumentException();
        }
    }

    public SortedMap<K, V> headMap(K toKey) {
        return new Submap(null, toKey);
    }

    public SortedMap<K, V> tailMap(K fromKey) {
        return new Submap(fromKey, null);
    }

    // ========== Infrastructure ==========

    static <K, V> Iterator<K> descendingKeyIterator(SortedMap<K, V> map) {
        return new Iterator<>() {
            SortedMap<K, V> root = map;
            SortedMap<K, V> view = map;
            K prev = null;

            public boolean hasNext() {
                return ! view.isEmpty();
            }

            public K next() {
                if (view.isEmpty())
                    throw new NoSuchElementException();
                K k = prev = view.lastKey();
                view = root.headMap(k);
                return k;
            }

            public void remove() {
                if (prev == null) {
                    throw new IllegalStateException();
                } else {
                    root.remove(prev);
                    prev = null;
                }
            }
        };
    }

    static <K, V> Iterator<V> descendingValueIterator(SortedMap<K, V> map) {
        return new Iterator<>() {
            Iterator<K> keyIterator = descendingKeyIterator(map);

            public boolean hasNext() {
                return keyIterator.hasNext();
            }

            public V next() {
                return map.get(keyIterator.next());
            }

            public void remove() {
                keyIterator.remove();
            }
        };
    }

    static <K, V> Iterator<Map.Entry<K, V>> descendingEntryIterator(SortedMap<K, V> map) {
        return new Iterator<>() {
            Iterator<K> keyIterator = descendingKeyIterator(map);

            public boolean hasNext() {
                return keyIterator.hasNext();
            }

            public Map.Entry<K, V> next() {
                K key = keyIterator.next();
                return new ViewEntry<>(map, key, map.get(key));
            }

            public void remove() {
                keyIterator.remove();
            }
        };
    }

    static class ViewEntry<K, V> implements Map.Entry<K, V> {
        final Map<K, V> map;
        final K key;
        final V value;

        ViewEntry(Map<K, V> map, K key, V value) {
            this.map = map;
            this.key = key;
            this.value = value;
        }

        public K getKey()             { return key; }
        public V getValue()           { return value; }
        public V setValue(V newValue) { return map.put(key, newValue); }

        public boolean equals(Object o) {
            return o instanceof Map.Entry<?, ?> e
                    && Objects.equals(key, e.getKey())
                    && Objects.equals(value, e.getValue());
        }

        public int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        public String toString() {
            return key + "=" + value;
        }
    }

    // copied and modified from AbstractMap
    static <K, V> String toString(Map<K, V> thisMap, Iterator<Entry<K,V>> i) {
        if (! i.hasNext())
            return "{}";

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (;;) {
            Entry<K,V> e = i.next();
            K key = e.getKey();
            V value = e.getValue();
            sb.append(key   == thisMap ? "(this Map)" : key);
            sb.append('=');
            sb.append(value == thisMap ? "(this Map)" : value);
            if (! i.hasNext())
                return sb.append('}').toString();
            sb.append(',').append(' ');
        }
    }

    /**
     * Used for various submap views. We can't use the base SortedMap's subMap,
     * because of the asymmetry between from-inclusive and to-exclusive.
     */
    class Submap extends AbstractMap<K, V> implements SortedMap<K, V> {
        final K head; // head key, or negative infinity if null
        final K tail; // tail key, or positive infinity if null

        @SuppressWarnings("unchecked")
        Submap(K head, K tail) {
            this.head = head;
            this.tail = tail;
        }

        // returns whether e is above the head, inclusive
        boolean aboveHead(K k) {
            return head == null || cmp.compare(k, head) >= 0;
        }

        // returns whether e is below the tail, exclusive
        boolean belowTail(K k) {
            return tail == null || cmp.compare(k, tail) < 0;
        }

        Iterator<Entry<K, V>> entryIterator() {
            return new Iterator<>() {
                Entry<K, V> cache = null;
                K prevKey = null;
                boolean dead = false;
                Iterator<Entry<K, V>> it = descendingEntryIterator(base);

                public boolean hasNext() {
                    if (dead)
                        return false;

                    if (cache != null)
                        return true;

                    while (it.hasNext()) {
                        Entry<K, V> e = it.next();

                        if (! aboveHead(e.getKey()))
                            continue;

                        if (! belowTail(e.getKey())) {
                            dead = true;
                            return false;
                        }

                        cache = e;
                        return true;
                    }

                    return false;
                }

                public Entry<K, V> next() {
                    if (hasNext()) {
                        Entry<K, V> e = cache;
                        cache = null;
                        prevKey = e.getKey();
                        return e;
                    } else {
                        throw new NoSuchElementException();
                    }
                }

                public void remove() {
                    if (prevKey == null) {
                        throw new IllegalStateException();
                    } else {
                        base.remove(prevKey);
                    }
                }
            };
        }

        // equals: inherited from AbstractMap

        // hashCode: inherited from AbstractMap

        public String toString() {
            return ReverseOrderSortedMapView.toString(this, entryIterator());
        }

        public Set<Entry<K, V>> entrySet() {
            return new AbstractSet<>() {
                public Iterator<Entry<K, V>> iterator() {
                    return entryIterator();
                }

                public int size() {
                    int sz = 0;
                    for (var it = entryIterator(); it.hasNext();) {
                        it.next();
                        sz++;
                    }
                    return sz;
                }
            };
        }

        public V put(K key, V value) {
            if (aboveHead(key) && belowTail(key))
                return base.put(key, value);
            else
                throw new IllegalArgumentException();
        }

        public V remove(Object o) {
            @SuppressWarnings("unchecked")
            K key = (K) o;
            if (aboveHead(key) && belowTail(key))
                return base.remove(o);
            else
                return null;
        }

        public int size() {
            return entrySet().size();
        }

        public Comparator<? super K> comparator() {
            return cmp;
        }

        public K firstKey() {
            return this.entryIterator().next().getKey();
        }

        public K lastKey() {
            var it = this.entryIterator();
            if (! it.hasNext())
                throw new NoSuchElementException();
            var last = it.next();
            while (it.hasNext())
                last = it.next();
            return last.getKey();
        }

        public SortedMap<K, V> subMap(K from, K to) {
            if (aboveHead(from) && belowTail(from) &&
                aboveHead(to) && belowTail(to) &&
                cmp.compare(from, to) <= 0) {
                return new Submap(from, to);
            } else {
                throw new IllegalArgumentException();
            }
        }

        public SortedMap<K, V> headMap(K to) {
            if (aboveHead(to) && belowTail(to))
                return new Submap(head, to);
            else
                throw new IllegalArgumentException();
        }

        public SortedMap<K, V> tailMap(K from) {
            if (aboveHead(from) && belowTail(from))
                return new Submap(from, tail);
            else
                throw new IllegalArgumentException();
        }
    }
}
