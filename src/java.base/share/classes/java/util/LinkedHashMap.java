/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.io.IOException;
import java.util.function.Function;

/**
 * <p>Hash table and linked list implementation of the {@code Map} interface,
 * with well-defined encounter order.  This implementation differs from
 * {@code HashMap} in that it maintains a doubly-linked list running through all of
 * its entries.  This linked list defines the encounter order (the order of iteration),
 * which is normally the order in which keys were inserted into the map
 * (<i>insertion-order</i>). The least recently inserted entry (the eldest) is
 * first, and the youngest entry is last. Note that encounter order is not affected
 * if a key is <i>re-inserted</i> into the map with the {@code put} method. (A key
 * {@code k} is reinserted into a map {@code m} if {@code m.put(k, v)} is invoked when
 * {@code m.containsKey(k)} would return {@code true} immediately prior to
 * the invocation.) The reverse-ordered view of this map is in the opposite order, with
 * the youngest entry appearing first and the eldest entry appearing last.
 * The encounter order of entries already in the map can be changed by using
 * the {@link #putFirst putFirst} and {@link #putLast putLast} methods.
 *
 * <p>This implementation spares its clients from the unspecified, generally
 * chaotic ordering provided by {@link HashMap} (and {@link Hashtable}),
 * without incurring the increased cost associated with {@link TreeMap}.  It
 * can be used to produce a copy of a map that has the same order as the
 * original, regardless of the original map's implementation:
 * <pre>{@code
 *     void foo(Map<String, Integer> m) {
 *         Map<String, Integer> copy = new LinkedHashMap<>(m);
 *         ...
 *     }
 * }</pre>
 * This technique is particularly useful if a module takes a map on input,
 * copies it, and later returns results whose order is determined by that of
 * the copy.  (Clients generally appreciate having things returned in the same
 * order they were presented.)
 *
 * <p>A special {@link #LinkedHashMap(int,float,boolean) constructor} is
 * provided to create a linked hash map whose encounter order is the order
 * in which its entries were last accessed, from least-recently accessed to
 * most-recently (<i>access-order</i>).  This kind of map is well-suited to
 * building LRU caches.  Invoking the {@code put}, {@code putIfAbsent},
 * {@code get}, {@code getOrDefault}, {@code compute}, {@code computeIfAbsent},
 * {@code computeIfPresent}, or {@code merge} methods results
 * in an access to the corresponding entry (assuming it exists after the
 * invocation completes). The {@code replace} methods only result in an access
 * of the entry if the value is replaced.  The {@code putAll} method generates one
 * entry access for each mapping in the specified map, in the order that
 * key-value mappings are provided by the specified map's entry set iterator.
 * <i>No other methods generate entry accesses.</i> Invoking these methods on the
 * reversed view generates accesses to entries on the backing map. Note that in the
 * reversed view, an access to an entry moves it first in encounter order.
 * Explicit-positioning methods such as {@code putFirst} or {@code lastEntry}, whether on
 * the map or on its reverse-ordered view, perform the positioning operation and
 * do not generate entry accesses. Operations on the {@code keySet}, {@code values},
 * and {@code entrySet} views or on their sequenced counterparts do <i>not</i> affect
 * the encounter order of the backing map.
 *
 * <p>The {@link #removeEldestEntry(Map.Entry)} method may be overridden to
 * impose a policy for removing stale mappings automatically when new mappings
 * are added to the map. Alternatively, since the "eldest" entry is the first
 * entry in encounter order, programs can inspect and remove stale mappings through
 * use of the {@link #firstEntry firstEntry} and {@link #pollFirstEntry pollFirstEntry}
 * methods.
 *
 * <p>This class provides all of the optional {@code Map} and {@code SequencedMap} operations,
 * and it permits null elements.  Like {@code HashMap}, it provides constant-time
 * performance for the basic operations ({@code add}, {@code contains} and
 * {@code remove}), assuming the hash function disperses elements
 * properly among the buckets.  Performance is likely to be just slightly
 * below that of {@code HashMap}, due to the added expense of maintaining the
 * linked list, with one exception: Iteration over the collection-views
 * of a {@code LinkedHashMap} requires time proportional to the <i>size</i>
 * of the map, regardless of its capacity.  Iteration over a {@code HashMap}
 * is likely to be more expensive, requiring time proportional to its
 * <i>capacity</i>.
 *
 * <p>A linked hash map has two parameters that affect its performance:
 * <i>initial capacity</i> and <i>load factor</i>.  They are defined precisely
 * as for {@code HashMap}.  Note, however, that the penalty for choosing an
 * excessively high value for initial capacity is less severe for this class
 * than for {@code HashMap}, as iteration times for this class are unaffected
 * by capacity.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * If multiple threads access a linked hash map concurrently, and at least
 * one of the threads modifies the map structurally, it <em>must</em> be
 * synchronized externally.  This is typically accomplished by
 * synchronizing on some object that naturally encapsulates the map.
 *
 * If no such object exists, the map should be "wrapped" using the
 * {@link Collections#synchronizedMap Collections.synchronizedMap}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the map:<pre>
 *   Map m = Collections.synchronizedMap(new LinkedHashMap(...));</pre>
 *
 * A structural modification is any operation that adds or deletes one or more
 * mappings or, in the case of access-ordered linked hash maps, affects
 * iteration order.  In insertion-ordered linked hash maps, merely changing
 * the value associated with a key that is already contained in the map is not
 * a structural modification.  <strong>In access-ordered linked hash maps,
 * merely querying the map with {@code get} is a structural modification.
 * </strong>)
 *
 * <p>The iterators returned by the {@code iterator} method of the collections
 * returned by all of this class's collection view methods are
 * <em>fail-fast</em>: if the map is structurally modified at any time after
 * the iterator is created, in any way except through the iterator's own
 * {@code remove} method, the iterator will throw a {@link
 * ConcurrentModificationException}.  Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than risking
 * arbitrary, non-deterministic behavior at an undetermined time in the future.
 *
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw {@code ConcurrentModificationException} on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness:   <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 *
 * <p>The spliterators returned by the spliterator method of the collections
 * returned by all of this class's collection view methods are
 * <em><a href="Spliterator.html#binding">late-binding</a></em>,
 * <em>fail-fast</em>, and additionally report {@link Spliterator#ORDERED}.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework">
 * Java Collections Framework</a>.
 *
 * @implNote
 * The spliterators returned by the spliterator method of the collections
 * returned by all of this class's collection view methods are created from
 * the iterators of the corresponding collections.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @author  Josh Bloch
 * @see     Object#hashCode()
 * @see     Collection
 * @see     Map
 * @see     HashMap
 * @see     TreeMap
 * @see     Hashtable
 * @since   1.4
 */
public class LinkedHashMap<K,V>
    extends HashMap<K,V>
    implements SequencedMap<K,V>
{

    /*
     * Implementation note.  A previous version of this class was
     * internally structured a little differently. Because superclass
     * HashMap now uses trees for some of its nodes, class
     * LinkedHashMap.Entry is now treated as intermediary node class
     * that can also be converted to tree form. The name of this
     * class, LinkedHashMap.Entry, is confusing in several ways in its
     * current context, but cannot be changed.  Otherwise, even though
     * it is not exported outside this package, some existing source
     * code is known to have relied on a symbol resolution corner case
     * rule in calls to removeEldestEntry that suppressed compilation
     * errors due to ambiguous usages. So, we keep the name to
     * preserve unmodified compilability.
     *
     * The changes in node classes also require using two fields
     * (head, tail) rather than a pointer to a header node to maintain
     * the doubly-linked before/after list. This class also
     * previously used a different style of callback methods upon
     * access, insertion, and removal.
     */

    /**
     * HashMap.Node subclass for normal LinkedHashMap entries.
     */
    static class Entry<K,V> extends HashMap.Node<K,V> {
        Entry<K,V> before, after;
        Entry(int hash, K key, V value, Node<K,V> next) {
            super(hash, key, value, next);
        }
    }

    @java.io.Serial
    private static final long serialVersionUID = 3801124242820219131L;

    /**
     * The head (eldest) of the doubly linked list.
     */
    transient LinkedHashMap.Entry<K,V> head;

    /**
     * The tail (youngest) of the doubly linked list.
     */
    transient LinkedHashMap.Entry<K,V> tail;

    /**
     * The iteration ordering method for this linked hash map: {@code true}
     * for access-order, {@code false} for insertion-order.
     *
     * @serial
     */
    final boolean accessOrder;

    // internal utilities

    // link at the end of list
    private void linkNodeAtEnd(LinkedHashMap.Entry<K,V> p) {
        if (putMode == PUT_FIRST) {
            LinkedHashMap.Entry<K,V> first = head;
            head = p;
            if (first == null)
                tail = p;
            else {
                p.after = first;
                first.before = p;
            }
        } else {
            LinkedHashMap.Entry<K,V> last = tail;
            tail = p;
            if (last == null)
                head = p;
            else {
                p.before = last;
                last.after = p;
            }
        }
    }

    // apply src's links to dst
    private void transferLinks(LinkedHashMap.Entry<K,V> src,
                               LinkedHashMap.Entry<K,V> dst) {
        LinkedHashMap.Entry<K,V> b = dst.before = src.before;
        LinkedHashMap.Entry<K,V> a = dst.after = src.after;
        if (b == null)
            head = dst;
        else
            b.after = dst;
        if (a == null)
            tail = dst;
        else
            a.before = dst;
    }

    // overrides of HashMap hook methods

    void reinitialize() {
        super.reinitialize();
        head = tail = null;
    }

    Node<K,V> newNode(int hash, K key, V value, Node<K,V> e) {
        LinkedHashMap.Entry<K,V> p =
            new LinkedHashMap.Entry<>(hash, key, value, e);
        linkNodeAtEnd(p);
        return p;
    }

    Node<K,V> replacementNode(Node<K,V> p, Node<K,V> next) {
        LinkedHashMap.Entry<K,V> q = (LinkedHashMap.Entry<K,V>)p;
        LinkedHashMap.Entry<K,V> t =
            new LinkedHashMap.Entry<>(q.hash, q.key, q.value, next);
        transferLinks(q, t);
        return t;
    }

    TreeNode<K,V> newTreeNode(int hash, K key, V value, Node<K,V> next) {
        TreeNode<K,V> p = new TreeNode<>(hash, key, value, next);
        linkNodeAtEnd(p);
        return p;
    }

    TreeNode<K,V> replacementTreeNode(Node<K,V> p, Node<K,V> next) {
        LinkedHashMap.Entry<K,V> q = (LinkedHashMap.Entry<K,V>)p;
        TreeNode<K,V> t = new TreeNode<>(q.hash, q.key, q.value, next);
        transferLinks(q, t);
        return t;
    }

    void afterNodeRemoval(Node<K,V> e) { // unlink
        LinkedHashMap.Entry<K,V> p =
            (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
        p.before = p.after = null;
        if (b == null)
            head = a;
        else
            b.after = a;
        if (a == null)
            tail = b;
        else
            a.before = b;
    }

    void afterNodeInsertion(boolean evict) { // possibly remove eldest
        LinkedHashMap.Entry<K,V> first;
        if (evict && (first = head) != null && removeEldestEntry(first)) {
            K key = first.key;
            removeNode(hash(key), key, null, false, true);
        }
    }

    static final int PUT_NORM = 0;
    static final int PUT_FIRST = 1;
    static final int PUT_LAST = 2;
    transient int putMode = PUT_NORM;

    // Called after update, but not after insertion
    void afterNodeAccess(Node<K,V> e) {
        LinkedHashMap.Entry<K,V> last;
        LinkedHashMap.Entry<K,V> first;
        if ((putMode == PUT_LAST || (putMode == PUT_NORM && accessOrder)) && (last = tail) != e) {
            // move node to last
            LinkedHashMap.Entry<K,V> p =
                (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
            p.after = null;
            if (b == null)
                head = a;
            else
                b.after = a;
            if (a != null)
                a.before = b;
            else
                last = b;
            if (last == null)
                head = p;
            else {
                p.before = last;
                last.after = p;
            }
            tail = p;
            ++modCount;
        } else if (putMode == PUT_FIRST && (first = head) != e) {
            // move node to first
            LinkedHashMap.Entry<K,V> p =
                (LinkedHashMap.Entry<K,V>)e, b = p.before, a = p.after;
            p.before = null;
            if (a == null)
                tail = b;
            else
                a.before = b;
            if (b != null)
                b.after = a;
            else
                first = a;
            if (first == null)
                tail = p;
            else {
                p.after = first;
                first.before = p;
            }
            head = p;
            ++modCount;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If this map already contains a mapping for this key, the mapping is relocated if necessary
     * so that it is first in encounter order.
     *
     * @since 21
     */
    public V putFirst(K k, V v) {
        try {
            putMode = PUT_FIRST;
            return this.put(k, v);
        } finally {
            putMode = PUT_NORM;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If this map already contains a mapping for this key, the mapping is relocated if necessary
     * so that it is last in encounter order.
     *
     * @since 21
     */
    public V putLast(K k, V v) {
        try {
            putMode = PUT_LAST;
            return this.put(k, v);
        } finally {
            putMode = PUT_NORM;
        }
    }

    void internalWriteEntries(java.io.ObjectOutputStream s) throws IOException {
        for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after) {
            s.writeObject(e.key);
            s.writeObject(e.value);
        }
    }

    /**
     * Constructs an empty insertion-ordered {@code LinkedHashMap} instance
     * with the specified initial capacity and load factor.
     *
     * @apiNote
     * To create a {@code LinkedHashMap} with an initial capacity that accommodates
     * an expected number of mappings, use {@link #newLinkedHashMap(int) newLinkedHashMap}.
     *
     * @param  initialCapacity the initial capacity
     * @param  loadFactor      the load factor
     * @throws IllegalArgumentException if the initial capacity is negative
     *         or the load factor is nonpositive
     */
    public LinkedHashMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        accessOrder = false;
    }

    /**
     * Constructs an empty insertion-ordered {@code LinkedHashMap} instance
     * with the specified initial capacity and a default load factor (0.75).
     *
     * @apiNote
     * To create a {@code LinkedHashMap} with an initial capacity that accommodates
     * an expected number of mappings, use {@link #newLinkedHashMap(int) newLinkedHashMap}.
     *
     * @param  initialCapacity the initial capacity
     * @throws IllegalArgumentException if the initial capacity is negative
     */
    public LinkedHashMap(int initialCapacity) {
        super(initialCapacity);
        accessOrder = false;
    }

    /**
     * Constructs an empty insertion-ordered {@code LinkedHashMap} instance
     * with the default initial capacity (16) and load factor (0.75).
     */
    public LinkedHashMap() {
        super();
        accessOrder = false;
    }

    /**
     * Constructs an insertion-ordered {@code LinkedHashMap} instance with
     * the same mappings as the specified map.  The {@code LinkedHashMap}
     * instance is created with a default load factor (0.75) and an initial
     * capacity sufficient to hold the mappings in the specified map.
     *
     * @param  m the map whose mappings are to be placed in this map
     * @throws NullPointerException if the specified map is null
     */
    @SuppressWarnings("this-escape")
    public LinkedHashMap(Map<? extends K, ? extends V> m) {
        super();
        accessOrder = false;
        putMapEntries(m, false);
    }

    /**
     * Constructs an empty {@code LinkedHashMap} instance with the
     * specified initial capacity, load factor and ordering mode.
     *
     * @param  initialCapacity the initial capacity
     * @param  loadFactor      the load factor
     * @param  accessOrder     the ordering mode - {@code true} for
     *         access-order, {@code false} for insertion-order
     * @throws IllegalArgumentException if the initial capacity is negative
     *         or the load factor is nonpositive
     */
    public LinkedHashMap(int initialCapacity,
                         float loadFactor,
                         boolean accessOrder) {
        super(initialCapacity, loadFactor);
        this.accessOrder = accessOrder;
    }


    /**
     * Returns {@code true} if this map maps one or more keys to the
     * specified value.
     *
     * @param value value whose presence in this map is to be tested
     * @return {@code true} if this map maps one or more keys to the
     *         specified value
     */
    public boolean containsValue(Object value) {
        for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after) {
            V v = e.value;
            if (v == value || (value != null && value.equals(v)))
                return true;
        }
        return false;
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code (key==null ? k==null :
     * key.equals(k))}, then this method returns {@code v}; otherwise
     * it returns {@code null}.  (There can be at most one such mapping.)
     *
     * <p>A return value of {@code null} does not <i>necessarily</i>
     * indicate that the map contains no mapping for the key; it's also
     * possible that the map explicitly maps the key to {@code null}.
     * The {@link #containsKey containsKey} operation may be used to
     * distinguish these two cases.
     */
    public V get(Object key) {
        Node<K,V> e;
        if ((e = getNode(key)) == null)
            return null;
        if (accessOrder)
            afterNodeAccess(e);
        return e.value;
    }

    /**
     * {@inheritDoc}
     */
    public V getOrDefault(Object key, V defaultValue) {
       Node<K,V> e;
       if ((e = getNode(key)) == null)
           return defaultValue;
       if (accessOrder)
           afterNodeAccess(e);
       return e.value;
   }

    /**
     * {@inheritDoc}
     */
    public void clear() {
        super.clear();
        head = tail = null;
    }

    /**
     * Returns {@code true} if this map should remove its eldest entry.
     * This method is invoked by {@code put} and {@code putAll} after
     * inserting a new entry into the map.  It provides the implementor
     * with the opportunity to remove the eldest entry each time a new one
     * is added.  This is useful if the map represents a cache: it allows
     * the map to reduce memory consumption by deleting stale entries.
     *
     * <p>Sample use: this override will allow the map to grow up to 100
     * entries and then delete the eldest entry each time a new entry is
     * added, maintaining a steady state of 100 entries.
     * <pre>
     *     private static final int MAX_ENTRIES = 100;
     *
     *     protected boolean removeEldestEntry(Map.Entry eldest) {
     *        return size() &gt; MAX_ENTRIES;
     *     }
     * </pre>
     *
     * <p>This method typically does not modify the map in any way,
     * instead allowing the map to modify itself as directed by its
     * return value.  It <i>is</i> permitted for this method to modify
     * the map directly, but if it does so, it <i>must</i> return
     * {@code false} (indicating that the map should not attempt any
     * further modification).  The effects of returning {@code true}
     * after modifying the map from within this method are unspecified.
     *
     * <p>This implementation merely returns {@code false} (so that this
     * map acts like a normal map - the eldest element is never removed).
     *
     * @param    eldest The least recently inserted entry in the map, or if
     *           this is an access-ordered map, the least recently accessed
     *           entry.  This is the entry that will be removed if this
     *           method returns {@code true}.  If the map was empty prior
     *           to the {@code put} or {@code putAll} invocation resulting
     *           in this invocation, this will be the entry that was just
     *           inserted; in other words, if the map contains a single
     *           entry, the eldest entry is also the newest.
     * @return   {@code true} if the eldest entry should be removed
     *           from the map; {@code false} if it should be retained.
     */
    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
        return false;
    }

    /**
     * Returns a {@link Set} view of the keys contained in this map. The encounter
     * order of the keys in the view matches the encounter order of mappings of
     * this map. The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own {@code remove} operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * {@code Iterator.remove}, {@code Set.remove},
     * {@code removeAll}, {@code retainAll}, and {@code clear}
     * operations.  It does not support the {@code add} or {@code addAll}
     * operations.
     * Its {@link Spliterator} typically provides faster sequential
     * performance but much poorer parallel performance than that of
     * {@code HashMap}.
     *
     * @return a set view of the keys contained in this map
     */
    public Set<K> keySet() {
        return sequencedKeySet();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned view has the same characteristics as specified for the view
     * returned by the {@link #keySet keySet} method.
     *
     * @return {@inheritDoc}
     * @since 21
     */
    public SequencedSet<K> sequencedKeySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            SequencedSet<K> sks = new LinkedKeySet(false);
            keySet = sks;
            return sks;
        } else {
            // The cast should never fail, since the only assignment of non-null to keySet is
            // above, and assignments in AbstractMap and HashMap are in overridden methods.
            return (SequencedSet<K>) ks;
        }
    }

    static <K1,V1> Node<K1,V1> nsee(Node<K1,V1> node) {
        if (node == null)
            throw new NoSuchElementException();
        else
            return node;
    }

    final <T> T[] keysToArray(T[] a) {
        return keysToArray(a, false);
    }

    final <T> T[] keysToArray(T[] a, boolean reversed) {
        Object[] r = a;
        int idx = 0;
        if (reversed) {
            for (LinkedHashMap.Entry<K,V> e = tail; e != null; e = e.before) {
                r[idx++] = e.key;
            }
        } else {
            for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after) {
                r[idx++] = e.key;
            }
        }
        return a;
    }

    final <T> T[] valuesToArray(T[] a, boolean reversed) {
        Object[] r = a;
        int idx = 0;
        if (reversed) {
            for (LinkedHashMap.Entry<K,V> e = tail; e != null; e = e.before) {
                r[idx++] = e.value;
            }
        } else {
            for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after) {
                r[idx++] = e.value;
            }
        }
        return a;
    }

    final class LinkedKeySet extends AbstractSet<K> implements SequencedSet<K> {
        final boolean reversed;
        LinkedKeySet(boolean reversed)          { this.reversed = reversed; }
        public final int size()                 { return size; }
        public final void clear()               { LinkedHashMap.this.clear(); }
        public final Iterator<K> iterator() {
            return new LinkedKeyIterator(reversed);
        }
        public final boolean contains(Object o) { return containsKey(o); }
        public final boolean remove(Object key) {
            return removeNode(hash(key), key, null, false, true) != null;
        }
        public final Spliterator<K> spliterator()  {
            return Spliterators.spliterator(this, Spliterator.SIZED |
                                            Spliterator.ORDERED |
                                            Spliterator.DISTINCT);
        }

        public Object[] toArray() {
            return keysToArray(new Object[size], reversed);
        }

        public <T> T[] toArray(T[] a) {
            return keysToArray(prepareArray(a), reversed);
        }

        public final void forEach(Consumer<? super K> action) {
            if (action == null)
                throw new NullPointerException();
            int mc = modCount;
            if (reversed) {
                for (LinkedHashMap.Entry<K,V> e = tail; e != null; e = e.before)
                    action.accept(e.key);
            } else {
                for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after)
                    action.accept(e.key);
            }
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
        public final void addFirst(K k) { throw new UnsupportedOperationException(); }
        public final void addLast(K k) { throw new UnsupportedOperationException(); }
        public final K getFirst() { return nsee(reversed ? tail : head).key; }
        public final K getLast() { return nsee(reversed ? head : tail).key; }
        public final K removeFirst() {
            var node = nsee(reversed ? tail : head);
            removeNode(node.hash, node.key, null, false, false);
            return node.key;
        }
        public final K removeLast() {
            var node = nsee(reversed ? head : tail);
            removeNode(node.hash, node.key, null, false, false);
            return node.key;
        }
        public SequencedSet<K> reversed() {
            if (reversed) {
                return LinkedHashMap.this.sequencedKeySet();
            } else {
                return new LinkedKeySet(true);
            }
        }
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map. The
     * encounter order of values in the view matches the encounter order of entries in
     * this map. The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  If the map is
     * modified while an iteration over the collection is in progress
     * (except through the iterator's own {@code remove} operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * mapping from the map, via the {@code Iterator.remove},
     * {@code Collection.remove}, {@code removeAll},
     * {@code retainAll} and {@code clear} operations.  It does not
     * support the {@code add} or {@code addAll} operations.
     * Its {@link Spliterator} typically provides faster sequential
     * performance but much poorer parallel performance than that of
     * {@code HashMap}.
     *
     * @return a view of the values contained in this map
     */
    public Collection<V> values() {
        return sequencedValues();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned view has the same characteristics as specified for the view
     * returned by the {@link #values values} method.
     *
     * @return {@inheritDoc}
     * @since 21
     */
    public SequencedCollection<V> sequencedValues() {
        Collection<V> vs = values;
        if (vs == null) {
            SequencedCollection<V> svs = new LinkedValues(false);
            values = svs;
            return svs;
        } else {
            // The cast should never fail, since the only assignment of non-null to values is
            // above, and assignments in AbstractMap and HashMap are in overridden methods.
            return (SequencedCollection<V>) vs;
        }
    }

    final class LinkedValues extends AbstractCollection<V> implements SequencedCollection<V> {
        final boolean reversed;
        LinkedValues(boolean reversed)          { this.reversed = reversed; }
        public final int size()                 { return size; }
        public final void clear()               { LinkedHashMap.this.clear(); }
        public final Iterator<V> iterator() {
            return new LinkedValueIterator(reversed);
        }
        public final boolean contains(Object o) { return containsValue(o); }
        public final Spliterator<V> spliterator() {
            return Spliterators.spliterator(this, Spliterator.SIZED |
                                            Spliterator.ORDERED);
        }

        public Object[] toArray() {
            return valuesToArray(new Object[size], reversed);
        }

        public <T> T[] toArray(T[] a) {
            return valuesToArray(prepareArray(a), reversed);
        }

        public final void forEach(Consumer<? super V> action) {
            if (action == null)
                throw new NullPointerException();
            int mc = modCount;
            if (reversed) {
                for (LinkedHashMap.Entry<K,V> e = tail; e != null; e = e.before)
                    action.accept(e.value);
            } else {
                for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after)
                    action.accept(e.value);
            }
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
        public final void addFirst(V v) { throw new UnsupportedOperationException(); }
        public final void addLast(V v) { throw new UnsupportedOperationException(); }
        public final V getFirst() { return nsee(reversed ? tail : head).value; }
        public final V getLast() { return nsee(reversed ? head : tail).value; }
        public final V removeFirst() {
            var node = nsee(reversed ? tail : head);
            removeNode(node.hash, node.key, null, false, false);
            return node.value;
        }
        public final V removeLast() {
            var node = nsee(reversed ? head : tail);
            removeNode(node.hash, node.key, null, false, false);
            return node.value;
        }
        public SequencedCollection<V> reversed() {
            if (reversed) {
                return LinkedHashMap.this.sequencedValues();
            } else {
                return new LinkedValues(true);
            }
        }
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map. The encounter
     * order of the view matches the encounter order of entries of this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own {@code remove} operation, or through the
     * {@code setValue} operation on a map entry returned by the
     * iterator) the results of the iteration are undefined.  The set
     * supports element removal, which removes the corresponding
     * mapping from the map, via the {@code Iterator.remove},
     * {@code Set.remove}, {@code removeAll}, {@code retainAll} and
     * {@code clear} operations.  It does not support the
     * {@code add} or {@code addAll} operations.
     * Its {@link Spliterator} typically provides faster sequential
     * performance but much poorer parallel performance than that of
     * {@code HashMap}.
     *
     * @return a set view of the mappings contained in this map
     */
    public Set<Map.Entry<K,V>> entrySet() {
        return sequencedEntrySet();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The returned view has the same characteristics as specified for the view
     * returned by the {@link #entrySet entrySet} method.
     *
     * @return {@inheritDoc}
     * @since 21
     */
    public SequencedSet<Map.Entry<K, V>> sequencedEntrySet() {
        Set<Map.Entry<K, V>> es = entrySet;
        if (es == null) {
            SequencedSet<Map.Entry<K, V>> ses = new LinkedEntrySet(false);
            entrySet = ses;
            return ses;
        } else {
            // The cast should never fail, since the only assignment of non-null to entrySet is
            // above, and assignments in HashMap are in overridden methods.
            return (SequencedSet<Map.Entry<K, V>>) es;
        }
    }

    final class LinkedEntrySet extends AbstractSet<Map.Entry<K,V>>
        implements SequencedSet<Map.Entry<K,V>> {
        final boolean reversed;
        LinkedEntrySet(boolean reversed)        { this.reversed = reversed; }
        public final int size()                 { return size; }
        public final void clear()               { LinkedHashMap.this.clear(); }
        public final Iterator<Map.Entry<K,V>> iterator() {
            return new LinkedEntryIterator(reversed);
        }
        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry<?, ?> e))
                return false;
            Object key = e.getKey();
            Node<K,V> candidate = getNode(key);
            return candidate != null && candidate.equals(e);
        }
        public final boolean remove(Object o) {
            if (o instanceof Map.Entry<?, ?> e) {
                Object key = e.getKey();
                Object value = e.getValue();
                return removeNode(hash(key), key, value, true, true) != null;
            }
            return false;
        }
        public final Spliterator<Map.Entry<K,V>> spliterator() {
            return Spliterators.spliterator(this, Spliterator.SIZED |
                                            Spliterator.ORDERED |
                                            Spliterator.DISTINCT);
        }
        public final void forEach(Consumer<? super Map.Entry<K,V>> action) {
            if (action == null)
                throw new NullPointerException();
            int mc = modCount;
            if (reversed) {
                for (LinkedHashMap.Entry<K,V> e = tail; e != null; e = e.before)
                    action.accept(e);
            } else {
                for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after)
                    action.accept(e);
            }
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
        final Node<K,V> nsee(Node<K,V> e) {
            if (e == null)
                throw new NoSuchElementException();
            else
                return e;
        }
        public final void addFirst(Map.Entry<K,V> e) { throw new UnsupportedOperationException(); }
        public final void addLast(Map.Entry<K,V> e) { throw new UnsupportedOperationException(); }
        public final Map.Entry<K,V> getFirst() { return nsee(reversed ? tail : head); }
        public final Map.Entry<K,V> getLast() { return nsee(reversed ? head : tail); }
        public final Map.Entry<K,V> removeFirst() {
            var node = nsee(reversed ? tail : head);
            removeNode(node.hash, node.key, null, false, false);
            return node;
        }
        public final Map.Entry<K,V> removeLast() {
            var node = nsee(reversed ? head : tail);
            removeNode(node.hash, node.key, null, false, false);
            return node;
        }
        public SequencedSet<Map.Entry<K,V>> reversed() {
            if (reversed) {
                return LinkedHashMap.this.sequencedEntrySet();
            } else {
                return new LinkedEntrySet(true);
            }
        }
    }

    // Map overrides

    public void forEach(BiConsumer<? super K, ? super V> action) {
        if (action == null)
            throw new NullPointerException();
        int mc = modCount;
        for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after)
            action.accept(e.key, e.value);
        if (modCount != mc)
            throw new ConcurrentModificationException();
    }

    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        if (function == null)
            throw new NullPointerException();
        int mc = modCount;
        for (LinkedHashMap.Entry<K,V> e = head; e != null; e = e.after)
            e.value = function.apply(e.key, e.value);
        if (modCount != mc)
            throw new ConcurrentModificationException();
    }

    // Iterators

    abstract class LinkedHashIterator {
        LinkedHashMap.Entry<K,V> next;
        LinkedHashMap.Entry<K,V> current;
        int expectedModCount;
        boolean reversed;

        LinkedHashIterator(boolean reversed) {
            this.reversed = reversed;
            next = reversed ? tail : head;
            expectedModCount = modCount;
            current = null;
        }

        public final boolean hasNext() {
            return next != null;
        }

        final LinkedHashMap.Entry<K,V> nextNode() {
            LinkedHashMap.Entry<K,V> e = next;
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            if (e == null)
                throw new NoSuchElementException();
            current = e;
            next = reversed ? e.before : e.after;
            return e;
        }

        public final void remove() {
            Node<K,V> p = current;
            if (p == null)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            current = null;
            removeNode(p.hash, p.key, null, false, false);
            expectedModCount = modCount;
        }
    }

    final class LinkedKeyIterator extends LinkedHashIterator
        implements Iterator<K> {
        LinkedKeyIterator(boolean reversed) { super(reversed); }
        public final K next() { return nextNode().getKey(); }
    }

    final class LinkedValueIterator extends LinkedHashIterator
        implements Iterator<V> {
        LinkedValueIterator(boolean reversed) { super(reversed); }
        public final V next() { return nextNode().value; }
    }

    final class LinkedEntryIterator extends LinkedHashIterator
        implements Iterator<Map.Entry<K,V>> {
        LinkedEntryIterator(boolean reversed) { super(reversed); }
        public final Map.Entry<K,V> next() { return nextNode(); }
    }

    /**
     * Creates a new, empty, insertion-ordered LinkedHashMap suitable for the expected number of mappings.
     * The returned map uses the default load factor of 0.75, and its initial capacity is
     * generally large enough so that the expected number of mappings can be added
     * without resizing the map.
     *
     * @param numMappings the expected number of mappings
     * @param <K>         the type of keys maintained by the new map
     * @param <V>         the type of mapped values
     * @return the newly created map
     * @throws IllegalArgumentException if numMappings is negative
     * @since 19
     */
    public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(int numMappings) {
        if (numMappings < 0) {
            throw new IllegalArgumentException("Negative number of mappings: " + numMappings);
        }
        return new LinkedHashMap<>(HashMap.calculateHashMapCapacity(numMappings));
    }

    // Reversed View

    /**
     * {@inheritDoc}
     * <p>
     * Modifications to the reversed view and its map views are permitted and will be
     * propagated to this map. In addition, modifications to this map will be visible
     * in the reversed view and its map views.
     *
     * @return {@inheritDoc}
     * @since 21
     */
    public SequencedMap<K, V> reversed() {
        return new ReversedLinkedHashMapView<>(this);
    }

    static class ReversedLinkedHashMapView<K, V> extends AbstractMap<K, V>
                                                 implements SequencedMap<K, V> {
        final LinkedHashMap<K, V> base;

        ReversedLinkedHashMapView(LinkedHashMap<K, V> lhm) {
            base = lhm;
        }

        // Object
        // inherit toString() from AbstractMap; it depends on entrySet()

        public boolean equals(Object o) {
            return base.equals(o);
        }

        public int hashCode() {
            return base.hashCode();
        }

        // Map

        public int size() {
            return base.size();
        }

        public boolean isEmpty() {
            return base.isEmpty();
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

        public V put(K key, V value) {
            return base.put(key, value);
        }

        public V remove(Object key) {
            return base.remove(key);
        }

        public void putAll(Map<? extends K, ? extends V> m) {
            base.putAll(m);
        }

        public void clear() {
            base.clear();
        }

        public Set<K> keySet() {
            return base.sequencedKeySet().reversed();
        }

        public Collection<V> values() {
            return base.sequencedValues().reversed();
        }

        public Set<Entry<K, V>> entrySet() {
            return base.sequencedEntrySet().reversed();
        }

        public V getOrDefault(Object key, V defaultValue) {
            return base.getOrDefault(key, defaultValue);
        }

        public void forEach(BiConsumer<? super K, ? super V> action) {
            if (action == null)
                throw new NullPointerException();
            int mc = base.modCount;
            for (LinkedHashMap.Entry<K,V> e = base.tail; e != null; e = e.before)
                action.accept(e.key, e.value);
            if (base.modCount != mc)
                throw new ConcurrentModificationException();
        }

        public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
            if (function == null)
                throw new NullPointerException();
            int mc = base.modCount;
            for (LinkedHashMap.Entry<K,V> e = base.tail; e != null; e = e.before)
                e.value = function.apply(e.key, e.value);
            if (base.modCount != mc)
                throw new ConcurrentModificationException();
        }

        public V putIfAbsent(K key, V value) {
            return base.putIfAbsent(key, value);
        }

        public boolean remove(Object key, Object value) {
            return base.remove(key, value);
        }

        public boolean replace(K key, V oldValue, V newValue) {
            return base.replace(key, oldValue, newValue);
        }

        public V replace(K key, V value) {
            return base.replace(key, value);
        }

        public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
            return base.computeIfAbsent(key, mappingFunction);
        }

        public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
            return base.computeIfPresent(key, remappingFunction);
        }

        public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
            return base.compute(key, remappingFunction);
        }

        public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
            return base.merge(key, value, remappingFunction);
        }

        // SequencedMap

        public SequencedMap<K, V> reversed() {
            return base;
        }

        public Entry<K, V> firstEntry() {
            return base.lastEntry();
        }

        public Entry<K, V> lastEntry() {
            return base.firstEntry();
        }

        public Entry<K, V> pollFirstEntry() {
            return base.pollLastEntry();
        }

        public Entry<K, V> pollLastEntry() {
            return base.pollFirstEntry();
        }

        public V putFirst(K k, V v) {
            return base.putLast(k, v);
        }

        public V putLast(K k, V v) {
            return base.putFirst(k, v);
        }
    }
}
