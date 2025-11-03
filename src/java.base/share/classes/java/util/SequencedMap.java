/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.util.NullableKeyValueHolder;

/**
 * A Map that has a well-defined encounter order, that supports operations at both ends, and
 * that is reversible. The <a href="SequencedCollection.html#encounter">encounter order</a>
 * of a {@code SequencedMap} is similar to that of the elements of a {@link SequencedCollection},
 * but the ordering applies to mappings instead of individual elements.
 * <p>
 * The bulk operations on this map, including the {@link #forEach forEach} and the
 * {@link #replaceAll replaceAll} methods, operate on this map's mappings in
 * encounter order.
 * <p>
 * The view collections provided by the
 * {@link #keySet keySet},
 * {@link #values values},
 * {@link #entrySet entrySet},
 * {@link #sequencedKeySet sequencedKeySet},
 * {@link #sequencedValues sequencedValues},
 * and
 * {@link #sequencedEntrySet sequencedEntrySet} methods all reflect the encounter order
 * of this map. Even though the return values of the {@code keySet}, {@code values}, and
 * {@code entrySet} methods are not sequenced <i>types</i>, the elements
 * in those view collections do reflect the encounter order of this map. Thus, the
 * iterators returned by the statements
 * {@snippet :
 *     var it1 = sequencedMap.entrySet().iterator();
 *     var it2 = sequencedMap.sequencedEntrySet().iterator();
 * }
 * both provide the mappings of {@code sequencedMap} in that map's encounter order.
 * <p>
 * This interface provides methods to add mappings, to retrieve mappings, and to remove
 * mappings at either end of the map's encounter order.
 * <p>
 * This interface also defines the {@link #reversed} method, which provides a
 * reverse-ordered <a href="Collection.html#view">view</a> of this map.
 * In the reverse-ordered view, the concepts of first and last are inverted, as
 * are the concepts of successor and predecessor. The first mapping of this map
 * is the last mapping of the reverse-ordered view, and vice-versa. The successor of some
 * mapping in this map is its predecessor in the reversed view, and vice-versa. All
 * methods that respect the encounter order of the map operate as if the encounter order
 * is inverted. For instance, the {@link #forEach forEach} method of the reversed view reports
 * the mappings in order from the last mapping of this map to the first. In addition, all of
 * the view collections of the reversed view also reflect the inverse of this map's
 * encounter order. For example,
 * {@snippet :
 *     var itr = sequencedMap.reversed().entrySet().iterator();
 * }
 * provides the mappings of this map in the inverse of the encounter order, that is, from
 * the last mapping to the first mapping. The availability of the {@code reversed} method,
 * and its impact on the ordering semantics of all applicable methods and views, allow convenient
 * iteration, searching, copying, and streaming of this map's mappings in either forward order or
 * reverse order.
 * <p>
 * A map's reverse-ordered view is generally not serializable, even if the original
 * map is serializable.
 * <p>
 * The {@link Map.Entry} instances obtained by iterating the {@link #entrySet} view, the
 * {@link #sequencedEntrySet} view, and its reverse-ordered view, maintain a connection to the
 * underlying map. This connection is guaranteed only during the iteration. It is unspecified
 * whether the connection is maintained outside of the iteration. If the underlying map permits
 * it, calling an Entry's {@link Map.Entry#setValue setValue} method will modify the value of the
 * underlying mapping. It is, however, unspecified whether modifications to the value in the
 * underlying mapping are visible in the {@code Entry} instance.
 * <p>
 * The methods
 * {@link #firstEntry},
 * {@link #lastEntry},
 * {@link #pollFirstEntry}, and
 * {@link #pollLastEntry}
 * return {@link Map.Entry} instances that represent snapshots of mappings as
 * of the time of the call. They do <em>not</em> support mutation of the
 * underlying map via the optional {@link Map.Entry#setValue setValue} method.
 * <p>
 * Depending upon the implementation, the {@code Entry} instances returned by other
 * means might or might not be connected to the underlying map. For example, consider
 * an {@code Entry} obtained in the following manner:
 * {@snippet :
 *     var entry = sequencedMap.sequencedEntrySet().getFirst();
 * }
 * It is not specified by this interface whether the {@code setValue} method of the
 * {@code Entry} thus obtained will update a mapping in the underlying map, or whether
 * it will throw an exception, or whether changes to the underlying map are visible in
 * that {@code Entry}.
 * <p>
 * This interface has the same requirements on the {@code equals} and {@code hashCode}
 * methods as defined by {@link Map#equals Map.equals} and {@link Map#hashCode Map.hashCode}.
 * Thus, a {@code Map} and a {@code SequencedMap} will compare equals if and only
 * if they have equal mappings, irrespective of ordering.
 *
 * <h2><a id="unmodifiable">Unmodifiable Maps with Well-defined Encounter Order</a></h2>
 * <p>The {@link #of() SequencedMap.of},
 * {@link #ofEntries(Map.Entry...) SequencedMap.ofEntries}, and
 * {@link #copyOf SequencedMap.copyOf}
 * static factory methods provide a convenient way to create unmodifiable maps
 * with well-defined encounter order.
 * The {@code SequencedMap}
 * instances created by these methods have the following characteristics:
 *
 * <ul>
 * <li>They are <a href="Collection.html#unmodifiable"><i>unmodifiable</i></a>. Keys and values
 * cannot be added, removed, or updated. Calling any mutator method on the map
 * will always cause {@code UnsupportedOperationException} to be thrown.
 * However, if the contained keys or values are themselves mutable, this may cause the
 * Map to behave inconsistently or its contents to appear to change.
 * <li>They disallow {@code null} keys and values. Attempts to create them with
 * {@code null} keys or values result in {@code NullPointerException}.
 * <li>They are serializable if all keys and values are serializable, but their
 * view collections and reversed view maps are never serializable.
 * <li>They reject duplicate keys at creation time. Duplicate keys
 * passed to a static factory method result in {@code IllegalArgumentException}.
 * <li>The iteration order of mappings is explicitly specified at creation.
 * <li>They are <a href="../lang/doc-files/ValueBased.html">value-based</a>.
 * Programmers should treat instances that are {@linkplain #equals(Object) equal}
 * as interchangeable and should not use them for synchronization, or
 * unpredictable behavior may occur. For example, in a future release,
 * synchronization may fail. Callers should make no assumptions
 * about the identity of the returned instances. Factories are free to
 * create new instances or reuse existing ones.
 * <li>They are serialized as specified on the
 * <a href="{@docRoot}/serialized-form.html#java.util.CollSer">Serialized Form</a>
 * page.
 * </ul>
 *
 * <p>
 * This class is a member of the
 * <a href="{@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework">
 * Java Collections Framework</a>.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 * @since 21
 */
public interface SequencedMap<K, V> extends Map<K, V> {
    /**
     * Returns a reverse-ordered <a href="Collection.html#view">view</a> of this map.
     * The encounter order of mappings in the returned view is the inverse of the encounter
     * order of mappings in this map. The reverse ordering affects all order-sensitive operations,
     * including those on the view collections of the returned view. If the implementation permits
     * modifications to this view, the modifications "write through" to the underlying map.
     * Changes to the underlying map might or might not be visible in this reversed view,
     * depending upon the implementation.
     *
     * @return a reverse-ordered view of this map
     */
    SequencedMap<K, V> reversed();

    /**
     * Returns the first key-value mapping in this map,
     * or {@code null} if the map is empty.
     *
     * @implSpec
     * The implementation in this interface obtains the iterator of this map's entrySet.
     * If the iterator has an element, it returns an unmodifiable copy of that element.
     * Otherwise, it returns null.
     *
     * @return the first key-value mapping,
     *         or {@code null} if this map is empty
     */
    default Map.Entry<K,V> firstEntry() {
        var it = entrySet().iterator();
        return it.hasNext() ? new NullableKeyValueHolder<>(it.next()) : null;
    }

    /**
     * Returns the last key-value mapping in this map,
     * or {@code null} if the map is empty.
     *
     * @implSpec
     * The implementation in this interface obtains the iterator of the entrySet of this map's
     * reversed view. If the iterator has an element, it returns an unmodifiable copy of
     * that element. Otherwise, it returns null.
     *
     * @return the last key-value mapping,
     *         or {@code null} if this map is empty
     */
    default Map.Entry<K,V> lastEntry() {
        var it = reversed().entrySet().iterator();
        return it.hasNext() ? new NullableKeyValueHolder<>(it.next()) : null;
    }

    /**
     * Removes and returns the first key-value mapping in this map,
     * or {@code null} if the map is empty (optional operation).
     *
     * @implSpec
     * The implementation in this interface obtains the iterator of this map's entrySet.
     * If the iterator has an element, it calls {@code remove} on the iterator and
     * then returns an unmodifiable copy of that element. Otherwise, it returns null.
     *
     * @return the removed first entry of this map,
     *         or {@code null} if this map is empty
     * @throws UnsupportedOperationException if this collection implementation does not
     *         support this operation
     */
    default Map.Entry<K,V> pollFirstEntry() {
        var it = entrySet().iterator();
        if (it.hasNext()) {
            var entry = new NullableKeyValueHolder<>(it.next());
            it.remove();
            return entry;
        } else {
            return null;
        }
    }

    /**
     * Removes and returns the last key-value mapping in this map,
     * or {@code null} if the map is empty (optional operation).
     *
     * @implSpec
     * The implementation in this interface obtains the iterator of the entrySet of this map's
     * reversed view. If the iterator has an element, it calls {@code remove} on the iterator
     * and then returns an unmodifiable copy of that element. Otherwise, it returns null.
     *
     * @return the removed last entry of this map,
     *         or {@code null} if this map is empty
     * @throws UnsupportedOperationException if this collection implementation does not
     *         support this operation
     */
    default Map.Entry<K,V> pollLastEntry() {
        var it = reversed().entrySet().iterator();
        if (it.hasNext()) {
            var entry = new NullableKeyValueHolder<>(it.next());
            it.remove();
            return entry;
        } else {
            return null;
        }
    }

    /**
     * Inserts the given mapping into the map if it is not already present, or replaces the
     * value of a mapping if it is already present (optional operation). After this operation
     * completes normally, the given mapping will be present in this map, and it will be the
     * first mapping in this map's encounter order.
     *
     * @implSpec The implementation in this interface always throws
     * {@code UnsupportedOperationException}.
     *
     * @param k the key
     * @param v the value
     * @return the value previously associated with k, or null if none
     * @throws UnsupportedOperationException if this collection implementation does not
     *         support this operation
     */
    default V putFirst(K k, V v) {
        throw new UnsupportedOperationException();
    }

    /**
     * Inserts the given mapping into the map if it is not already present, or replaces the
     * value of a mapping if it is already present (optional operation). After this operation
     * completes normally, the given mapping will be present in this map, and it will be the
     * last mapping in this map's encounter order.
     *
     * @implSpec The implementation in this interface always throws
     * {@code UnsupportedOperationException}.
     *
     * @param k the key
     * @param v the value
     * @return the value previously associated with k, or null if none
     * @throws UnsupportedOperationException if this collection implementation does not
     *         support this operation
     */
    default V putLast(K k, V v) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a {@code SequencedSet} view of this map's {@link #keySet keySet}.
     *
     * @implSpec
     * The implementation in this interface returns a {@code SequencedSet} instance
     * that behaves as follows. Its {@link SequencedSet#add add}, {@link
     * SequencedSet#addAll addAll}, {@link SequencedSet#addFirst addFirst}, and {@link
     * SequencedSet#addLast addLast} methods throw {@link UnsupportedOperationException}.
     * Its {@link SequencedSet#getFirst getFirst} and {@link SequencedSet#getLast getLast}
     * methods are implemented in terms of the {@link #firstEntry firstEntry} and {@link
     * #lastEntry lastEntry} methods of this interface, respectively. Its {@link
     * SequencedSet#removeFirst removeFirst} and {@link SequencedSet#removeLast removeLast}
     * methods are implemented in terms of the {@link #pollFirstEntry pollFirstEntry} and
     * {@link #pollLastEntry pollLastEntry} methods of this interface, respectively.
     * Its {@link SequencedSet#reversed reversed} method returns the {@link
     * #sequencedKeySet sequencedKeySet} view of the {@link #reversed reversed} view of
     * this map. Each of its other methods calls the corresponding method of the {@link
     * #keySet keySet} view of this map.
     *
     * @return a {@code SequencedSet} view of this map's {@code keySet}
     */
    default SequencedSet<K> sequencedKeySet() {
        class SeqKeySet extends AbstractMap.ViewCollection<K> implements SequencedSet<K> {
            Collection<K> view() {
                return SequencedMap.this.keySet();
            }
            public SequencedSet<K> reversed() {
                return SequencedMap.this.reversed().sequencedKeySet();
            }
            public boolean equals(Object other) {
                return view().equals(other);
            }
            public int hashCode() {
                return view().hashCode();
            }
            public void addFirst(K k) { throw new UnsupportedOperationException(); }
            public void addLast(K k) { throw new UnsupportedOperationException(); }
            public K getFirst() { return nsee(SequencedMap.this.firstEntry()).getKey(); }
            public K getLast() { return nsee(SequencedMap.this.lastEntry()).getKey(); }
            public K removeFirst() {
                return nsee(SequencedMap.this.pollFirstEntry()).getKey();
            }
            public K removeLast() {
                return nsee(SequencedMap.this.pollLastEntry()).getKey();
            }
        }
        return new SeqKeySet();
    }

    /**
     * Returns a {@code SequencedCollection} view of this map's {@link #values values} collection.
     *
     * @implSpec
     * The implementation in this interface returns a {@code SequencedCollection} instance
     * that behaves as follows. Its {@link SequencedCollection#add add}, {@link
     * SequencedCollection#addAll addAll}, {@link SequencedCollection#addFirst addFirst}, and {@link
     * SequencedCollection#addLast addLast} methods throw {@link UnsupportedOperationException}.
     * Its {@link SequencedCollection#getFirst getFirst} and {@link SequencedCollection#getLast getLast}
     * methods are implemented in terms of the {@link #firstEntry firstEntry} and {@link
     * #lastEntry lastEntry} methods of this interface, respectively. Its {@link
     * SequencedCollection#removeFirst removeFirst} and {@link SequencedCollection#removeLast removeLast}
     * methods are implemented in terms of the {@link #pollFirstEntry pollFirstEntry} and
     * {@link #pollLastEntry pollLastEntry} methods of this interface, respectively.
     * Its {@link SequencedCollection#reversed reversed} method returns the {@link
     * #sequencedValues sequencedValues} view of the {@link #reversed reversed} view of
     * this map. Its {@link Object#equals equals} and {@link Object#hashCode hashCode} methods
     * are inherited from {@link Object}. Each of its other methods calls the corresponding
     * method of the {@link #values values} view of this map.
     *
     * @return a {@code SequencedCollection} view of this map's {@code values} collection
     */
    default SequencedCollection<V> sequencedValues() {
        class SeqValues extends AbstractMap.ViewCollection<V> implements SequencedCollection<V> {
            Collection<V> view() {
                return SequencedMap.this.values();
            }
            public SequencedCollection<V> reversed() {
                return SequencedMap.this.reversed().sequencedValues();
            }
            public void addFirst(V v) { throw new UnsupportedOperationException(); }
            public void addLast(V v) { throw new UnsupportedOperationException(); }
            public V getFirst() { return nsee(SequencedMap.this.firstEntry()).getValue(); }
            public V getLast() { return nsee(SequencedMap.this.lastEntry()).getValue(); }
            public V removeFirst() {
                return nsee(SequencedMap.this.pollFirstEntry()).getValue();
            }
            public V removeLast() {
                return nsee(SequencedMap.this.pollLastEntry()).getValue();
            }
        }
        return new SeqValues();
    }

    /**
     * Returns a {@code SequencedSet} view of this map's {@link #entrySet entrySet}.
     *
     * @implSpec
     * The implementation in this interface returns a {@code SequencedSet} instance
     * that behaves as follows. Its {@link SequencedSet#add add}, {@link
     * SequencedSet#addAll addAll}, {@link SequencedSet#addFirst addFirst}, and {@link
     * SequencedSet#addLast addLast} methods throw {@link UnsupportedOperationException}.
     * Its {@link SequencedSet#getFirst getFirst} and {@link SequencedSet#getLast getLast}
     * methods are implemented in terms of the {@link #firstEntry firstEntry} and {@link
     * #lastEntry lastEntry} methods of this interface, respectively. Its {@link
     * SequencedSet#removeFirst removeFirst} and {@link SequencedSet#removeLast removeLast}
     * methods are implemented in terms of the {@link #pollFirstEntry pollFirstEntry} and
     * {@link #pollLastEntry pollLastEntry} methods of this interface, respectively.
     * Its {@link SequencedSet#reversed reversed} method returns the {@link
     * #sequencedEntrySet sequencedEntrySet} view of the {@link #reversed reversed} view of
     * this map. Each of its other methods calls the corresponding method of the {@link
     * #entrySet entrySet} view of this map.
     *
     * @return a {@code SequencedSet} view of this map's {@code entrySet}
     */
    default SequencedSet<Map.Entry<K, V>> sequencedEntrySet() {
        class SeqEntrySet extends AbstractMap.ViewCollection<Map.Entry<K, V>>
                implements SequencedSet<Map.Entry<K, V>> {
            Collection<Map.Entry<K, V>> view() {
                return SequencedMap.this.entrySet();
            }
            public SequencedSet<Map.Entry<K, V>> reversed() {
                return SequencedMap.this.reversed().sequencedEntrySet();
            }
            public boolean equals(Object other) {
                return view().equals(other);
            }
            public int hashCode() {
                return view().hashCode();
            }
            public void addFirst(Map.Entry<K, V> e) { throw new UnsupportedOperationException(); }
            public void addLast(Map.Entry<K, V> e) { throw new UnsupportedOperationException(); }
            public Map.Entry<K, V> getFirst() { return nsee(SequencedMap.this.firstEntry()); }
            public Map.Entry<K, V> getLast() { return nsee(SequencedMap.this.lastEntry()); }
            public Map.Entry<K, V> removeFirst() {
                return nsee(SequencedMap.this.pollFirstEntry());
            }
            public Map.Entry<K, V> removeLast() {
                return nsee(SequencedMap.this.pollLastEntry());
            }
        }
        return new SeqEntrySet();
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} map containing zero
     * mappings in an explicit encounter order.
     *
     * @param <K> the {@code SequencedMap}'s key type
     * @param <V> the {@code SequencedMap}'s value type
     * @return an empty {@code SequencedMap}
     *
     * @since 26
     */
    static <K, V> SequencedMap<K, V> of() {
        return ImmutableCollections.sequencedMap(List.of());
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} map containing a
     * single mapping in an explicit encounter order.
     *
     * @param <K> the {@code SequencedMap}'s key type
     * @param <V> the {@code SequencedMap}'s value type
     * @param k1 the mapping's key
     * @param v1 the mapping's value
     * @return a {@code SequencedMap} containing the specified mapping
     * @throws NullPointerException if the key or the value is {@code null}
     *
     * @since 26
     */
    static <K, V> SequencedMap<K, V> of(K k1, V v1) {
        return ImmutableCollections.sequencedMap(List.of(Map.entry(k1, v1)));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} map containing two
     * mappings in the given order.
     *
     * @param <K> the {@code SequencedMap}'s key type
     * @param <V> the {@code SequencedMap}'s value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @return a {@code SequencedMap} containing the specified mappings in the specified order
     * @throws IllegalArgumentException if the keys are duplicates
     * @throws NullPointerException if any key or value is {@code null}
     *
     * @since 26
     */
    static <K, V> SequencedMap<K, V> of(K k1, V v1, K k2, V v2) {
        return ImmutableCollections.sequencedMap(List.of(Map.entry(k1, v1), Map.entry(k2, v2)));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} map containing three
     * mappings in the given order.
     *
     * @param <K> the {@code SequencedMap}'s key type
     * @param <V> the {@code SequencedMap}'s value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @return a {@code SequencedMap} containing the specified mappings in the specified order
     * @throws IllegalArgumentException if there are any duplicate keys
     * @throws NullPointerException if any key or value is {@code null}
     *
     * @since 26
     */
    static <K, V> SequencedMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
        return ImmutableCollections.sequencedMap(List.of(Map.entry(k1, v1), Map.entry(k2, v2),
                Map.entry(k3, v3)));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} map containing four
     * mappings in the given order.
     *
     * @param <K> the {@code SequencedMap}'s key type
     * @param <V> the {@code SequencedMap}'s value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @return a {@code SequencedMap} containing the specified mappings in the specified order
     * @throws IllegalArgumentException if there are any duplicate keys
     * @throws NullPointerException if any key or value is {@code null}
     *
     * @since 26
     */
    static <K, V> SequencedMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        return ImmutableCollections.sequencedMap(List.of(Map.entry(k1, v1), Map.entry(k2, v2),
                Map.entry(k3, v3), Map.entry(k4, v4)));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} map containing five
     * mappings in the given order.
     *
     * @param <K> the {@code SequencedMap}'s key type
     * @param <V> the {@code SequencedMap}'s value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @param k5 the fifth mapping's key
     * @param v5 the fifth mapping's value
     * @return a {@code SequencedMap} containing the specified mappings in the specified order
     * @throws IllegalArgumentException if there are any duplicate keys
     * @throws NullPointerException if any key or value is {@code null}
     *
     * @since 26
     */
    static <K, V> SequencedMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        return ImmutableCollections.sequencedMap(List.of(Map.entry(k1, v1), Map.entry(k2, v2),
                Map.entry(k3, v3), Map.entry(k4, v4), Map.entry(k5, v5)));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} map containing six
     * mappings in the given order.
     *
     * @param <K> the {@code SequencedMap}'s key type
     * @param <V> the {@code SequencedMap}'s value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @param k5 the fifth mapping's key
     * @param v5 the fifth mapping's value
     * @param k6 the sixth mapping's key
     * @param v6 the sixth mapping's value
     * @return a {@code SequencedMap} containing the specified mappings in the specified order
     * @throws IllegalArgumentException if there are any duplicate keys
     * @throws NullPointerException if any key or value is {@code null}
     *
     * @since 26
     */
    static <K, V> SequencedMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                        K k6, V v6) {
        return ImmutableCollections.sequencedMap(List.of(Map.entry(k1, v1), Map.entry(k2, v2),
                Map.entry(k3, v3), Map.entry(k4, v4), Map.entry(k5, v5), Map.entry(k6, v6)));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} map containing seven
     * mappings in the given order.
     *
     * @param <K> the {@code SequencedMap}'s key type
     * @param <V> the {@code SequencedMap}'s value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @param k5 the fifth mapping's key
     * @param v5 the fifth mapping's value
     * @param k6 the sixth mapping's key
     * @param v6 the sixth mapping's value
     * @param k7 the seventh mapping's key
     * @param v7 the seventh mapping's value
     * @return a {@code SequencedMap} containing the specified mappings in the specified order
     * @throws IllegalArgumentException if there are any duplicate keys
     * @throws NullPointerException if any key or value is {@code null}
     *
     * @since 26
     */
    static <K, V> SequencedMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                        K k6, V v6, K k7, V v7) {
        return ImmutableCollections.sequencedMap(List.of(Map.entry(k1, v1), Map.entry(k2, v2),
                Map.entry(k3, v3), Map.entry(k4, v4), Map.entry(k5, v5), Map.entry(k6, v6),
                Map.entry(k7, v7)));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} map containing eight
     * mappings in the given order.
     *
     * @param <K> the {@code SequencedMap}'s key type
     * @param <V> the {@code SequencedMap}'s value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @param k5 the fifth mapping's key
     * @param v5 the fifth mapping's value
     * @param k6 the sixth mapping's key
     * @param v6 the sixth mapping's value
     * @param k7 the seventh mapping's key
     * @param v7 the seventh mapping's value
     * @param k8 the eighth mapping's key
     * @param v8 the eighth mapping's value
     * @return a {@code SequencedMap} containing the specified mappings in the specified order
     * @throws IllegalArgumentException if there are any duplicate keys
     * @throws NullPointerException if any key or value is {@code null}
     *
     * @since 26
     */
    static <K, V> SequencedMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                        K k6, V v6, K k7, V v7, K k8, V v8) {
        return ImmutableCollections.sequencedMap(List.of(Map.entry(k1, v1), Map.entry(k2, v2),
                Map.entry(k3, v3), Map.entry(k4, v4), Map.entry(k5, v5), Map.entry(k6, v6),
                Map.entry(k7, v7), Map.entry(k8, v8)));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} map containing nine
     * mappings in the given order.
     *
     * @param <K> the {@code SequencedMap}'s key type
     * @param <V> the {@code SequencedMap}'s value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @param k5 the fifth mapping's key
     * @param v5 the fifth mapping's value
     * @param k6 the sixth mapping's key
     * @param v6 the sixth mapping's value
     * @param k7 the seventh mapping's key
     * @param v7 the seventh mapping's value
     * @param k8 the eighth mapping's key
     * @param v8 the eighth mapping's value
     * @param k9 the ninth mapping's key
     * @param v9 the ninth mapping's value
     * @return a {@code SequencedMap} containing the specified mappings in the specified order
     * @throws IllegalArgumentException if there are any duplicate keys
     * @throws NullPointerException if any key or value is {@code null}
     *
     * @since 26
     */
    static <K, V> SequencedMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                        K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9) {
        return ImmutableCollections.sequencedMap(List.of(Map.entry(k1, v1), Map.entry(k2, v2),
                Map.entry(k3, v3), Map.entry(k4, v4), Map.entry(k5, v5), Map.entry(k6, v6),
                Map.entry(k7, v7), Map.entry(k8, v8), Map.entry(k9, v9)));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} map containing ten
     * mappings in the given order.
     *
     * @param <K> the {@code SequencedMap}'s key type
     * @param <V> the {@code SequencedMap}'s value type
     * @param k1 the first mapping's key
     * @param v1 the first mapping's value
     * @param k2 the second mapping's key
     * @param v2 the second mapping's value
     * @param k3 the third mapping's key
     * @param v3 the third mapping's value
     * @param k4 the fourth mapping's key
     * @param v4 the fourth mapping's value
     * @param k5 the fifth mapping's key
     * @param v5 the fifth mapping's value
     * @param k6 the sixth mapping's key
     * @param v6 the sixth mapping's value
     * @param k7 the seventh mapping's key
     * @param v7 the seventh mapping's value
     * @param k8 the eighth mapping's key
     * @param v8 the eighth mapping's value
     * @param k9 the ninth mapping's key
     * @param v9 the ninth mapping's value
     * @param k10 the tenth mapping's key
     * @param v10 the tenth mapping's value
     * @return a {@code SequencedMap} containing the specified mappings in the specified order
     * @throws IllegalArgumentException if there are any duplicate keys
     * @throws NullPointerException if any key or value is {@code null}
     *
     * @since 26
     */
    static <K, V> SequencedMap<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5,
                                        K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10) {
        return ImmutableCollections.sequencedMap(List.of(Map.entry(k1, v1), Map.entry(k2, v2),
                Map.entry(k3, v3), Map.entry(k4, v4), Map.entry(k5, v5), Map.entry(k6, v6),
                Map.entry(k7, v7), Map.entry(k8, v8), Map.entry(k9, v9), Map.entry(k10, v10)));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} map containing keys
     * and values extracted from the given entries in the given order.
     * The entries themselves are not stored in the map.
     *
     * @apiNote
     * It is convenient to create the map entries using the {@link Map#entry
     * Map.entry()} method. For example,
     *
     * <pre>{@code
     *     import static java.util.Map.entry;
     *
     *     SequencedMap<Integer,String> map = SequencedMap.ofEntries(
     *         entry(1, "a"),
     *         entry(2, "b"),
     *         entry(3, "c"),
     *         ...
     *         entry(26, "z"));
     * }</pre>
     *
     * @param <K> the {@code SequencedMap}'s key type
     * @param <V> the {@code SequencedMap}'s value type
     * @param entries {@code Map.Entry}s containing the keys and values from which the map is populated
     * @return a {@code SequencedMap} containing the specified mappings in the specified order
     * @throws IllegalArgumentException if there are any duplicate keys
     * @throws NullPointerException if any entry, key, or value is {@code null}, or if
     *         the {@code entries} array is {@code null}
     *
     * @see Map#entry Map.entry()
     * @since 26
     */
    @SafeVarargs
    static <K, V> SequencedMap<K, V> ofEntries(Entry<? extends K, ? extends V>... entries) {
        Object[] safeEntries = new Object[entries.length];
        for (int i = 0; i < entries.length; i++) {
            safeEntries[i] = Map.Entry.copyOf(entries[i]);
        }
        List<Map.Entry<K, V>> safeEntriesList = ImmutableCollections.listFromTrustedArray(safeEntries);
        return ImmutableCollections.sequencedMap(safeEntriesList);
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} map containing the
     * entries of the given Map in the given order. The given Map must not be
     * null, and it must not contain any null keys or values. If the given Map
     * is subsequently modified, the returned SequencedMap will not reflect such
     * modifications.
     *
     * @implNote
     * If the given Map is an <a href="#unmodifiable">unmodifiable</a>
     * map with a well-defined order, calling copyOf will generally not create
     * a copy.
     *
     * @param <K> the {@code SequencedMap}'s key type
     * @param <V> the {@code SequencedMap}'s value type
     * @param map a {@code SequencedMap} from which entries are drawn, must be non-null
     * @return a {@code SequencedMap} containing the entries of the given {@code SequencedMap}
     * @throws NullPointerException if map is null, or if it contains any null keys or values
     * @since 26
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <K, V> SequencedMap<K, V> copyOf(Map<? extends K, ? extends V> map) {
        if (map instanceof ImmutableCollections.WrapperSequencedMap<? extends K,? extends V> m0)
            return (SequencedMap<K, V>) m0;
        return ofEntries(map.entrySet().toArray(new Entry[0]));
    }
}
