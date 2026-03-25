/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.valhalla.sandbox.corelibs.corelibs.mapprotos;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.Boolean;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * HashMap using hashing and "open addressing".
 * Hash entries are inline class instances.
 * As described in <em>Introduction to Algorithms, 3rd Edition (The MIT Press)</em>,
 * Section 11 Hash tables and Section 11.4 Open addressing.
 *
 * Open addressing is used to locate other entries for keys with the same hash.
 * If multiple keys have the same hashcode, a rehashing mechanism
 * is used to place the 2nd and subsequent
 * key/value pairs at a non-optimal index in the table. Therefore,
 * finding the entry for a desired key must rehash and examine subsequent
 * entries until the value is value or it encounters an empty entry.
 * When an entry is removed, the entry is marked as deleted, (not empty)
 * to allow the search algorithm to keep looking; otherwise it would terminate
 * the scan on the deleted entry, when it might be the case for some (other) key
 * would have that same entry as part of its chain of possible locations for its hash.
 * The default load factor (.75) should be re-evaluated in light of the open addressing
 * computations.  A higher number would reduce unused (wasted) space at the cost of
 * increased search times, a lower number would increase unused (wasted) space but
 * improve search times (assuming even hashcode distributions).
 * Badly distributed hash values will result in incremental table growth and
 * linear search performance.
 * <p>
 * During insertion the Robin Hood hash algorithm does a small optimization
 * to reduce worst case rehash lengths.
 * Removal of entries, does a compaction of the following entries to fill
 * in free entries and reduce entry rehashling lengths based on
 * "On Deletions in Open Addressing Hashing", by Rosa M. Jimenez and Conrado Martinz.
 *
 * <p>
 * The only allocation that occurs during put operations is for the resizing of the entry table.
 *
 * <P>
 * Hash table based implementation of the {@code Map} interface.  This
 * implementation provides all of the optional map operations, and permits
 * {@code null} values and the {@code null} key.  (The {@code HashMap}
 * class is roughly equivalent to {@code Hashtable}, except that it is
 * unsynchronized and permits nulls.)  This class makes no guarantees as to
 * the order of the map; in particular, it does not guarantee that the order
 * will remain constant over time.
 *
 * <p>This implementation provides constant-time performance for the basic
 * operations ({@code get} and {@code put}), assuming the hash function
 * disperses the elements properly among the buckets.  Iteration over
 * collection views requires time proportional to the "capacity" of the
 * {@code HashMap} instance (the number of buckets) plus its size (the number
 * of key-value mappings).  Thus, it's very important not to set the initial
 * capacity too high (or the load factor too low) if iteration performance is
 * important.
 *
 * <p>An instance of {@code HashMap} has two parameters that affect its
 * performance: <i>initial capacity</i> and <i>load factor</i>.  The
 * <i>capacity</i> is the number of buckets in the hash table, and the initial
 * capacity is simply the capacity at the time the hash table is created.  The
 * <i>load factor</i> is a measure of how full the hash table is allowed to
 * get before its capacity is automatically increased.  When the number of
 * entries in the hash table exceeds the product of the load factor and the
 * current capacity, the hash table is <i>rehashed</i> (that is, internal data
 * structures are rebuilt) so that the hash table has approximately twice the
 * number of buckets.
 *
 * <p>As a general rule, the default load factor (.75) offers a good
 * tradeoff between time and space costs.  Higher values decrease the
 * space overhead but increase the lookup cost (reflected in most of
 * the operations of the {@code HashMap} class, including
 * {@code get} and {@code put}).  The expected number of entries in
 * the map and its load factor should be taken into account when
 * setting its initial capacity, so as to minimize the number of
 * rehash operations.  If the initial capacity is greater than the
 * maximum number of entries divided by the load factor, no rehash
 * operations will ever occur.
 *
 * <p>If many mappings are to be stored in a {@code HashMap}
 * instance, creating it with a sufficiently large capacity will allow
 * the mappings to be stored more efficiently than letting it perform
 * automatic rehashing as needed to grow the table.  Note that using
 * many keys with the same {@code hashCode()} is a sure way to slow
 * down performance of any hash table.
 * TBD: To ameliorate impact, when keys
 * are {@link Comparable}, this class may use comparison order among
 * keys to help break ties.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * If multiple threads access a hash map concurrently, and at least one of
 * the threads modifies the map structurally, it <i>must</i> be
 * synchronized externally.  (A structural modification is any operation
 * that adds or deletes one or more mappings; merely changing the value
 * associated with a key that an instance already contains is not a
 * structural modification.)  This is typically accomplished by
 * synchronizing on some object that naturally encapsulates the map.
 *
 * If no such object exists, the map should be "wrapped" using the
 * {@link Collections#synchronizedMap Collections.synchronizedMap}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the map:<pre>
 *   Map m = Collections.synchronizedMap(new HashMap(...));</pre>
 *
 * <p>The iterators returned by all of this class's "collection view methods"
 * are <i>fail-fast</i>: if the map is structurally modified at any time after
 * the iterator is created, in any way except through the iterator's own
 * {@code remove} method, the iterator will throw a
 * {@link ConcurrentModificationException}.  Thus, in the face of concurrent
 * modification, the iterator fails quickly and cleanly, rather than risking
 * arbitrary, non-deterministic behavior at an undetermined time in the
 * future.
 *
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw {@code ConcurrentModificationException} on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness: <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework">
 * Java Collections Framework</a>.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 *
 * @author  Doug Lea
 * @author  Josh Bloch
 * @author  Arthur van Hoff
 * @author  Neal Gafter
 * @see     Object#hashCode()
 * @see     Collection
 * @see     Map
 * @see     TreeMap
 * @see     Hashtable
 * @since   1.2
 */
public class HashMap<K,V> extends XAbstractMap<K,V>
        implements Map<K,V>, Cloneable, Serializable {

    private static final long serialVersionUID = 362498820763181265L;

    private static final boolean DEBUG = Boolean.getBoolean("DEBUG");
    private static final boolean VERIFYTABLEOK = Boolean.getBoolean("VERIFYTABLEOK");

    /*
     * Implementation notes.
     *
     * This map usually acts as a binned (bucketed) hash table.
     * The concurrent-programming-like SSA-based coding style helps
     * avoid aliasing errors amid all of the twisty pointer operations.
     */

    /**
     * The default initial capacity - MUST be a power of two.
     */
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16

    /**
     * The maximum capacity, used if a higher value is implicitly specified
     * by either of the constructors with arguments.
     * MUST be a power of two <= 1<<30.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * The load factor used when none specified in constructor.
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private final int REHASH_HASH = 2003; // Odd and small (a medium-small prime)

    /**
     * Basic hash bin node, used for most entries.
     */
    static value class YNode<K,V> implements Map.Entry<K,V> {
        final int hash;
        final short probes; // maybe only a byte
        final K key;
        final V value;

        YNode() {
            this.hash = 0;
            this.probes = 0;
            this.key = null;
            this.value = null;
        }

        YNode(int hash, K key, V value, int probes) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.probes = (short)probes;
        }

        boolean isEmpty() {
            return probes == 0;
        }

        boolean isValue() {
            return probes > 0;
        }

        public final K getKey()        { return key; }
        public final V getValue()      { return value; }
        public final String toString() { return key + "=" + value + ", probes: " + probes
                + ", hash: " + Integer.toString(hash, 16)
                + ", hashCode: " + hashCode(); }
        public final int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        public final V setValue(V newValue) {
            throw new IllegalStateException("YNode cannot set a value");
        }

        public final boolean equals(Object o) {
            if (o == this)
                return true;
            if (o instanceof Map.Entry) {
                Map.Entry<?,?> e = (Map.Entry<?,?>)o;
                if (Objects.equals(key, e.getKey()) &&
                        Objects.equals(value, e.getValue()))
                    return true;
            }
            return false;
        }
    }

    value class YNodeWrapper implements Map.Entry<K,V> {
        final int index;
        final YNode<K,V> entry;

        YNodeWrapper(int index) {
            this.index = index;
            this.entry = table[index];
        }

        public K getKey() {
            return entry.key;
        }

        public V getValue() {
            return entry.value;
        }

        public String toString() {
            return entry.toString();
        }

        public int hashCode() {
            return entry.hashCode();
        }

        public boolean equals(Object o) {
            return entry.equals(o);
        }
        /**
         * Replaces the value corresponding to this entry with the specified
         * value (optional operation).  (Writes through to the map.)  The
         * behavior of this call is undefined if the mapping has already been
         * removed from the map (by the iterator's {@code remove} operation).
         *
         * @param value new value to be stored in this entry
         * @return old value corresponding to the entry
         * @throws UnsupportedOperationException if the {@code put} operation
         *         is not supported by the backing map
         * @throws ClassCastException if the class of the specified value
         *         prevents it from being stored in the backing map
         * @throws NullPointerException if the backing map does not permit
         *         null values, and the specified value is null
         * @throws IllegalArgumentException if some property of this value
         *         prevents it from being stored in the backing map
         * @throws IllegalStateException implementations may, but are not
         *         required to, throw this exception if the entry has been
         *         removed from the backing map.
         */
        public V setValue(V value) {
            table[index] = new YNode<>(entry.hash, entry.key, value, entry.probes);
            return entry.value;
        }
    }
    /* ---------------- Static utilities -------------- */

    /**
     * Computes key.hashCode() and spreads (XORs) higher bits of hash
     * to lower.  Because the table uses power-of-two masking, sets of
     * hashes that vary only in bits above the current mask will
     * always collide. (Among known examples are sets of Float keys
     * holding consecutive whole numbers in small tables.)  So we
     * apply a transform that spreads the impact of higher bits
     * downward. There is a tradeoff between speed, utility, and
     * quality of bit-spreading. Because many common sets of hashes
     * are already reasonably distributed (so don't benefit from
     * spreading), and because we use trees to handle large sets of
     * collisions in bins, we just XOR some shifted bits in the
     * cheapest possible way to reduce systematic lossage, as well as
     * to incorporate impact of the highest bits that would otherwise
     * never be used in index calculations because of table bounds.
     */
    static final int hash(Object key) {
        int h;
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }

    /**
     * Returns a power of two size for the given target capacity.
     */
    static final int tableSizeFor(int cap) {
        int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    /* ---------------- Fields -------------- */

    /**
     * The table, initialized on first use, and resized as
     * necessary. When allocated, length is always a power of two.
     * (We also tolerate length zero in some operations to allow
     * bootstrapping mechanics that are currently not needed.)
     */
    transient YNode<K,V>[] table;

    /**
     * Holds cached entrySet(). Note that AbstractMap fields are used
     * for keySet() and values().
     */
    transient Set<Map.Entry<K,V>> entrySet;

    /**
     * The number of key-value mappings contained in this map.
     */
    transient int size;

    /**
     * The number of times this HashMap has been structurally modified
     * Structural modifications are those that change the number of mappings in
     * the HashMap or otherwise modify its internal structure (e.g.,
     * rehash).  This field is used to make iterators on Collection-views of
     * the HashMap fail-fast.  (See ConcurrentModificationException).
     */
    transient int modCount;

    /**
     * The next size value at which to resize (capacity * load factor).
     *
     * @serial
     */
    // (The javadoc description is true upon serialization.
    // Additionally, if the table array has not been allocated, this
    // field holds the initial array capacity, or zero signifying
    // DEFAULT_INITIAL_CAPACITY.)
    int threshold;

    /**
     * The load factor for the hash table.
     *
     * @serial
     */
    final float loadFactor;

    private transient int[] getProbes = new int[16];
    private transient int[] putProbes = new int[16];
    private transient int[] notFoundProbes = new int[16];


    /* ---------------- Public operations -------------- */

    /**
     * Constructs an empty {@code HashMap} with the specified initial
     * capacity and load factor.
     *
     * @param  initialCapacity the initial capacity
     * @param  loadFactor      the load factor
     * @throws IllegalArgumentException if the initial capacity is negative
     *         or the load factor is nonpositive
     */
    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                    initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " +
                    loadFactor);
        this.loadFactor = loadFactor;
        this.threshold = tableSizeFor(initialCapacity);
    }

    /**
     * Constructs an empty {@code HashMap} with the specified initial
     * capacity and the default load factor (0.75).
     *
     * @param  initialCapacity the initial capacity.
     * @throws IllegalArgumentException if the initial capacity is negative.
     */
    public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs an empty {@code HashMap} with the default initial capacity
     * (16) and the default load factor (0.75).
     */
    public HashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR; // all other fields defaulted
    }

    /**
     * Constructs a new {@code HashMap} with the same mappings as the
     * specified {@code Map}.  The {@code HashMap} is created with
     * default load factor (0.75) and an initial capacity sufficient to
     * hold the mappings in the specified {@code Map}.
     *
     * @param   m the map whose mappings are to be placed in this map
     * @throws  NullPointerException if the specified map is null
     */
    @SuppressWarnings("initialization")
    public HashMap(Map<? extends K, ? extends V> m) {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        putMapEntries(m, false);
    }

    /**
     * Implements Map.putAll and Map constructor.
     *
     * @param m the map
     * @param evict false when initially constructing this map, else true.
     */
    final void putMapEntries(Map<? extends K, ? extends V> m, boolean evict) {
        int s = m.size();
        if (s > 0) {
            if (table == null) { // pre-size
                float ft = ((float)s / loadFactor) + 1.0F;
                int t = ((ft < (float)MAXIMUM_CAPACITY) ?
                        (int)ft : MAXIMUM_CAPACITY);
                if (t > threshold)
                    threshold = tableSizeFor(t);
            } else {
                // Because of linked-list bucket constraints, we cannot
                // expand all at once, but can reduce total resize
                // effort by repeated doubling now vs later
                while (s > threshold && table.length < MAXIMUM_CAPACITY)
                    resize();
            }

            for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
                K key = e.getKey();
                V value = e.getValue();
                putVal(hash(key), key, value, false);
            }
        }
    }

    /**
     * Returns the number of key-value mappings in this map.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        return size;
    }

    /**
     * Returns {@code true} if this map contains no key-value mappings.
     *
     * @return {@code true} if this map contains no key-value mappings
     */
    public boolean isEmpty() {
        return size == 0;
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
     *
     * @see #put(Object, Object)
     */
    public V get(Object key) {
        final YNode<K, V>[] tab;
        final int mask;
//        int probes = 0;
        if ((tab = table) != null && (mask = tab.length - 1) >= 0) {
            final int hash = hash(key);
            int h = hash;
            YNode<K, V> entry;
            while ((entry = tab[(mask & h)]).isValue()) {
//                probes++;
                K k;
                if (entry.hash == hash &&
                        ((k = entry.key) == key || (key != null && key.equals(k)))) {
//                    getProbes = incProbeCount(getProbes, probes);
                    return entry.value;
                } else {
                    h += REHASH_HASH;
                }
            }
        }
//        notFoundProbes = incProbeCount(notFoundProbes, 0);
        return null;      // not found; empty table
    }

    /**
     * Same as Get caching the entry.
     * @param key the key
     * @return a value, or null
     */
    public V get1(Object key) {
        final int hash = hash(key);
        final YNode<K, V>[] tab;
        int n;
        if ((tab = table) != null && (n = tab.length) > 0) {
            int h = hash;
            int index = (n - 1) & h;
            YNode<K, V> entry = tab[index];
            for (; //int probes = 1
                 entry.isValue();
                 h += REHASH_HASH, index = (n - 1) & h, entry = tab[index]) {  //, probes++
                K k;
                if (entry.hash == hash &&
                        ((k = entry.key) == key || (key != null && key.equals(k)))) {
//                    getProbes = incProbeCount(getProbes, probes);
                    return entry.value;
                }
            }
        }
//        notFoundProbes = incProbeCount(notFoundProbes, 0);
        return null;      // not found; empty table
    }

    /**
     * Implements Map.get and related methods.
     *
     * @param hash hash for key
     * @param key the key
     * @return the index of a matching node or -1
     */
    private final int getNode(final int hash, Object key) {
        YNode<K, V>[] tab;
        int n;
        if ((tab = table) != null && (n = tab.length) > 0) {
            final YNode<K, V> first;
            final int i = (n - 1) & hash;
            K k;
            if ((first = tab[i]).isValue() &&
                    first.hash == hash &&
                    ((k = first.key) == key || (key != null && key.equals(k)))) {
//                getProbes = incProbeCount(getProbes, 1);
                return i;
            }
            // non-empty table and not first entry
            int h = hash;
            for (int probes = 1; probes <= tab.length; probes++, h += REHASH_HASH) {
                final int index = (n - 1) & h;
                final YNode<K, V> entry = tab[index];
                if (!entry.isValue()) {
//                    notFoundProbes = incProbeCount(notFoundProbes, probes);
                    return -1;  // search ended without finding the key
                } else if (entry.hash == hash &&
                        ((k = entry.key) == key || (key != null && key.equals(k)))) {
//                    getProbes = incProbeCount(getProbes, probes);
                    return index;
                }
            }
        }
//        notFoundProbes = incProbeCount(notFoundProbes, 0);
        return -1;      // not found; empty table
    }

    /**
     * Returns {@code true} if this map contains a mapping for the
     * specified key.
     *
     * @param   key   The key whose presence in this map is to be tested
     * @return {@code true} if this map contains a mapping for the specified
     * key.
     */
    public boolean containsKey(Object key) {
        return getNode(hash(key), key) >= 0;
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old
     * value is replaced.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}.
     *         (A {@code null} return can also indicate that the map
     *         previously associated {@code null} with {@code key}.)
     */
    public V put(K key, V value) {
        return putVal(hash(key), key, value, false);
    }

    /**
     * Implements Map.put and related methods.
     *
     * @param hash hash for key
     * @param key the key
     * @param value the value to put
     * @param onlyIfAbsent if true, don't change existing value
     * @return previous value, or null if none
     */
    private final V putVal(final int hash, final K key, final V value, boolean onlyIfAbsent) {
        YNode<K, V>[] tab;
        YNode<K, V> tp;
        int n, i;
        if ((tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;
        debug("  putval", -1, new YNode<K,V>(hash, key, value, -1));

        int h = hash;
        int insert = -1;    // insertion point if not already present
        int insertProbes = -1;
        for (int probes = 1; ; probes++, h += REHASH_HASH) {
            if (probes > tab.length)
                throw new IllegalStateException("No empty entries in table");
            final int index;
            final YNode<K, V> entry = tab[(index = ((n - 1) & h))];
//            debug("    looking at", index, entry);
            if (entry.isEmpty()) {
                // Absent; insert in the first place it could be added
                // TBD: should it check onlyIfAbsent?
                if (insert < 0) {
                    // no better place to insert than here
                    tab[index] = new YNode<>(hash, key, value, probes);
                    debug("    setting", index, tab[index]);
//                    putProbes = incProbeCount(putProbes, probes);
                } else {
                    // The new entry is more needy than the current one
                    final YNode<K,V> tmp = tab[insert];
                    tab[insert] = new YNode<>(hash, key, value, insertProbes);
                    debug("    robin-hood inserted", index, tab[index]);
//                    putProbes = incProbeCount(putProbes, insertProbes);
                    putReinsert(insert, tmp);
                }
                break;  // break to update modCount and size
            }

            if (entry.isValue() && entry.hash == hash &&
                    (entry.key == key || (key != null && key.equals(entry.key)))) {
                // TBD: consider if updated entry should be moved closer to the front
                if (!onlyIfAbsent || entry.value == null) {
                    tab[index] = new YNode<>(hash, key, value, entry.probes);
                }
                debug("    oldvalue", index, entry);
                debug("    updating", index, tab[index]);
//                putProbes = incProbeCount(putProbes, probes);
                return entry.value;
            }

            // Save first possible insert index
            if (insert < 0 && probes > entry.probes) {
                insert = index;
                insertProbes = probes;
            }
        }
        ++modCount;
        ++size;
        isTableOk(tab, "table not ok, putval");
        if (size >= threshold)
            resize();       // Ensure there is at least 1 empty available
        return null;
    }

    /**
     * Re-insert the entry in the table starting at the entry beyond the index.
     * Insert it at an empty slot.
     * Replace an entry with a lower probe count and repeat to reinsert that entry.
     * @param oldIndex the index just replaced
     * @param rEntry the entry to be replaced
     */
    private void putReinsert(final int oldIndex, YNode<K,V> rEntry) {
        final YNode<K, V>[] tab = table;
        final int n = tab.length;

        int h = oldIndex + REHASH_HASH;
        for (int probes = rEntry.probes + 1; probes <= n; probes++, h += REHASH_HASH) {
            isTableOk(tab, "bubble down loop");
            final int index = (n - 1) & h;
            final YNode<K,V> entry = tab[index];
            if (entry.isEmpty()) {
                // Insert in the empty slot
                tab[index] = new YNode<>(rEntry.hash, rEntry.key, rEntry.value, probes);
                debug("    reinserted", index, tab[index]);
                return;
            } else if (probes > entry.probes) {
                // Replace a less deserving entry
                tab[index] = new YNode<>(rEntry.hash, rEntry.key, rEntry.value, probes);
                debug("    robin-hood bubble down", index, tab[index]);
                rEntry = entry;
                probes = rEntry.probes;
                debug("    robin-hood moving", index, rEntry);
            } else {
                debug("    robin-hood skipping", index, entry);
            }
        }
        throw new RuntimeException("bubble down failed");
    }

    private void debug(String msg, int index, YNode<K,V> entry) {
        if (DEBUG && System.out != null) {
            System.out.println(System.identityHashCode(this) + ": " + msg + ": index: " + index + ", node: " + Objects.toString(entry));
        }
    }
   private void debug2(String msg, int index, YNode<K,V> entry) {
        if (System.out != null) {
            System.out.println(System.identityHashCode(this) + ": " + msg + ": index: " + index +
                    ", " + "node: " + entry);
        }
    }

    /**
     * Initializes or doubles table size.  If null, allocates in
     * accord with initial capacity target held in field threshold.
     * Otherwise, because we are using power-of-two expansion, the
     * elements from each bin must either stay at same index, or move
     * with a power of two offset in the new table.
     *
     * @return the table
     */
    final YNode<K,V>[] resize() {
        YNode<K,V>[] oldTab = table;
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        int oldThr = threshold;
        int newCap, newThr = 0;
        if (oldCap > 0) {
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                     oldCap >= DEFAULT_INITIAL_CAPACITY)
                newThr = oldThr << 1; // double threshold
        }
        else if (oldThr > 0) // initial capacity was placed in threshold
            newCap = oldThr;
        else {               // zero initial threshold signifies using defaults
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        if (newThr == 0) {
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                    (int)ft : Integer.MAX_VALUE);
        }
        isTableOk(oldTab, "oldTab bad before resize");
        if (getProbes != null)
            Arrays.fill(getProbes, 0);
        if (putProbes != null)
            Arrays.fill(putProbes, 0);
        if (notFoundProbes != null)
            Arrays.fill(notFoundProbes, 0);

        // There must always be an empty entry, resize when it gets to capacity.
        threshold = (newThr > newCap) ? newCap : newThr;
        @SuppressWarnings({"rawtypes","unchecked"})
        YNode<K,V>[] newTab = (YNode<K,V>[])new YNode[newCap];
        table = newTab;
        if (oldTab != null) {
            for (int i = 0; i < oldCap; ++i) {
                YNode<K,V> e;
                if ((e = oldTab[i]).isValue()) {
                    final int ii;
                    if (newTab[ii = (newCap - 1) & e.hash].isEmpty()) {
                        newTab[ii] = new YNode<>(e.hash, e.key, e.value, 1);
                        putProbes = incProbeCount(putProbes, 1);
                    } else {
                        int h = e.hash + REHASH_HASH;
                        for (int probes = 2; ; probes++, h += REHASH_HASH) {
                            final int index;
                            if (newTab[(index = ((newCap - 1) & h))].isEmpty()) {
                                newTab[index] = new YNode<>(e.hash, e.key, e.value, probes);
                                putProbes = incProbeCount(putProbes, probes);
                                break;
                            }
                            // TBD: Consider Robin-hood insert
                            if (probes > newTab.length)
                                throw new IllegalStateException("NYI resize: no support for overflow");
                        }
                    }
                }
            }
        }

        debug("resized", newTab.length, new YNode<K,V>());
        isTableOk(newTab, "newTab bad after resize");
        return newTab;
    }

    /**
     * Dump the hashtable.
     */
    public void dumpTable() {
        dumpTable(table, "dumpTable");
    }

    /**
     * Dump the hashtable
     * @param table the table
     * @param msg a message
     */
    private void dumpTable(YNode<K, V>[] table, String msg) {
        if (System.out == null || table == null)
            return;
        System.out.println(msg + ", size: " + size + ", len: " + table.length);
        for (int i = 0; i < table.length; ++i) {
            if (table[i].isValue())
                System.out.println("   [" + i + "] " + table[i]);
        }
    }

    /**
     * Copies all of the mappings from the specified map to this map.
     * These mappings will replace any mappings that this map had for
     * any of the keys currently in the specified map.
     *
     * @param m mappings to be stored in this map
     * @throws NullPointerException if the specified map is null
     */
    public void putAll(Map<? extends K, ? extends V> m) {
        putMapEntries(m, true);
    }

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param  key key whose mapping is to be removed from the map
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}.
     *         (A {@code null} return can also indicate that the map
     *         previously associated {@code null} with {@code key}.)
     */
    public V remove(Object key) {
        YNode<K,V> entry = removeNode(hash(key), key, null, false, true);
        return entry.isValue() ? entry.value : null;
    }

    /**
     * Implements Map.remove and related methods.
     *
     * @param hash hash for key
     * @param key the key
     * @param value the value to match if matchValue, else ignored
     * @param matchValue if true only remove if value is equal
     * @param movable if false do not move other nodes while removing
     * @return the node, or null if none
     */
    @SuppressWarnings("unchecked")
    private final YNode<K,V> removeNode(final int hash, final Object key, final Object value,
                                         boolean matchValue, boolean movable) {
        YNode<K, V>[] tab;
        YNode<K, V> entry;
        V v = null;
        int curr;
        int n;
        debug("  removeNode", -2, new YNode<K,V>(hash, (K)key, (V)value, -2));

        if ((tab = table) != null && (n = tab.length) > 0 &&
                (curr = getNode(hash, key)) >= 0 &&
                (entry = tab[curr]).isValue() &&
                ((!matchValue || (v = entry.value) == value ||
                        (value != null && value.equals(v))))) {
            // found entry; free and compress
            removeNode(curr);
            return entry;
        }
        return new YNode();
    }

    @SuppressWarnings("unchecked")
    private void removeNode(final int curr) {
        final YNode<K, V>[] tab = table;;
        final int n = tab.length;

        ++modCount;
        --size;
        int free = curr;        // The entry to be cleared, if not replaced
        int h = curr;
        int probes = 0;
        do {
            probes++;
            h += REHASH_HASH;
            final int index = (n - 1) & h;
            final YNode<K, V> entry = tab[index];
            if (entry.probes == 0) {
                // Search ended at empty entry, clear the free entry
                debug("    clearing", index, entry);
                tab[free] = new YNode<>();
                return;
            }
            if (entry.probes > probes) {
                // move up the entry, skip if it is already in the best spot
                debug("    replacing", free, entry);
                tab[free] = new YNode<>(entry.hash, entry.key, entry.value, entry.probes - probes);
                debug("         with", free, tab[free]);
                free = index;
                probes = 0;
            }
        } while (((n - 1) & h) != curr);
        isTableOk(tab, "table not ok, not found");
        throw new RuntimeException("removeNode too many probes");
    }

    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     */
    @SuppressWarnings({"rawtypes","unchecked"})
    public void clear() {
        YNode<K,V>[] tab;
        modCount++;
        if ((tab = table) != null && size > 0) {
            size = 0;
            for (int i = 0; i < tab.length; i++)
                tab[i] = new YNode();
        }
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
        YNode<K,V>[] tab; V v;
        if ((tab = table) != null && size > 0) {
            for (YNode<K,V> te : tab) {
                if (te.isValue()) {
                    if ((v = te.value) == value ||
                            (value != null && value.equals(v)))
                        return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own {@code remove} operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * {@code Iterator.remove}, {@code Set.remove},
     * {@code removeAll}, {@code retainAll}, and {@code clear}
     * operations.  It does not support the {@code add} or {@code addAll}
     * operations.
     *
     * @return a set view of the keys contained in this map
     */
    public Set<K> keySet() {
        Set<K> ks = keySet;
        if (ks == null) {
            ks = new KeySet();
            keySet = ks;
        }
        return ks;
    }

    /**
     * Prepares the array for {@link Collection#toArray(Object[])} implementation.
     * If supplied array is smaller than this map size, a new array is allocated.
     * If supplied array is bigger than this map size, a null is written at size index.
     *
     * @param a an original array passed to {@code toArray()} method
     * @param <T> type of array elements
     * @return an array ready to be filled and returned from {@code toArray()} method.
     */
    @SuppressWarnings("unchecked")
    final <T> T[] prepareArray(T[] a) {
        int size = this.size;
        if (a.length < size) {
            return (T[]) java.lang.reflect.Array
                    .newInstance(a.getClass().getComponentType(), size);
        }
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }

    /**
     * Fills an array with this map keys and returns it. This method assumes
     * that input array is big enough to fit all the keys. Use
     * {@link #prepareArray(Object[])} to ensure this.
     *
     * @param a an array to fill
     * @param <T> type of array elements
     * @return supplied array
     */
    <T> T[] keysToArray(T[] a) {
        Object[] r = a;
        YNode<K,V>[] tab;
        int idx = 0;
        if (size > 0 && (tab = table) != null) {
            for (YNode<K,V> te : tab) {
                if (te.isValue()) {
                    r[idx++] = te.key;
                }
            }
        }
        return a;
    }

    /**
     * Fills an array with this map values and returns it. This method assumes
     * that input array is big enough to fit all the values. Use
     * {@link #prepareArray(Object[])} to ensure this.
     *
     * @param a an array to fill
     * @param <T> type of array elements
     * @return supplied array
     */
    <T> T[] valuesToArray(T[] a) {
        Object[] r = a;
        YNode<K,V>[] tab;
        int idx = 0;
        if (size > 0 && (tab = table) != null) {
            for (YNode<K,V> te : tab) {
                if (te.isValue()) {
                    r[idx++] = te.value;
                }
            }
        }
        return a;
    }

    final class KeySet extends AbstractSet<K> {
        public final int size()                 { return size; }
        public final void clear()               { HashMap.this.clear(); }
        public final Iterator<K> iterator()     { return new KeyIterator(); }
        public final boolean contains(Object o) { return containsKey(o); }
        public final boolean remove(Object key) {
            return removeNode(hash(key), key, null, false, true).isValue();
        }

        public Object[] toArray() {
            return keysToArray(new Object[size]);
        }

        public <T> T[] toArray(T[] a) {
            return keysToArray(prepareArray(a));
        }

        public final void forEach(Consumer<? super K> action) {
            YNode<K,V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (YNode<K,V> te : tab) {
                    if (te.isValue()) {
                        action.accept(te.key);
                    }
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  If the map is
     * modified while an iteration over the collection is in progress
     * (except through the iterator's own {@code remove} operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * mapping from the map, via the {@code Iterator.remove},
     * {@code Collection.remove}, {@code removeAll},
     * {@code retainAll} and {@code clear} operations.  It does not
     * support the {@code add} or {@code addAll} operations.
     *
     * @return a view of the values contained in this map
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        if (vs == null) {
            vs = new Values();
            values = vs;
        }
        return vs;
    }

    final class Values extends AbstractCollection<V> {
        public final int size()                 { return size; }
        public final void clear()               { HashMap.this.clear(); }
        public final Iterator<V> iterator()     { return new ValueIterator(); }
        public final boolean contains(Object o) { return containsValue(o); }

        public Object[] toArray() {
            return valuesToArray(new Object[size]);
        }

        public <T> T[] toArray(T[] a) {
            return valuesToArray(prepareArray(a));
        }

        public final void forEach(Consumer<? super V> action) {
            YNode<K,V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (YNode<K,V> te : tab) {
                    if (te.isValue()) {
                        action.accept(te.value);
                    }
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
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
     *
     * @return a set view of the mappings contained in this map
     */
    public Set<Map.Entry<K,V>> entrySet() {
        Set<Map.Entry<K,V>> es;
        return (es = entrySet) == null ? (entrySet = new EntrySet()) : es;
    }

    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public final int size()                 { return size; }
        public final void clear()               { HashMap.this.clear(); }
        public final Iterator<Map.Entry<K,V>> iterator() {
            return new EntryIterator();
        }
        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>) o;
            Object key = e.getKey();
            int index = getNode(hash(key), key);
            return index >= 0 && table[index].equals(e);
        }
        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry<?,?> e = (Map.Entry<?,?>) o;
                Object key = e.getKey();
                Object value = e.getValue();
                return removeNode(hash(key), key, value, true, true).isValue();
            }
            return false;
        }

        public final void forEach(Consumer<? super Map.Entry<K,V>> action) {
            YNode<K,V>[] tab;
            if (action == null)
                throw new NullPointerException();
            if (size > 0 && (tab = table) != null) {
                int mc = modCount;
                for (YNode<K,V> te : tab) {
                    if (te.isValue()) {
                        action.accept(new YNodeWrapper(te.hash & (tab.length - 1)));
                    }
                }
                if (modCount != mc)
                    throw new ConcurrentModificationException();
            }
        }
    }

    // Overrides of JDK8 Map extension methods

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        final int index;
        return (index = getNode(hash(key), key)) < 0 ? defaultValue : table[index].value;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        return putVal(hash(key), key, value, true);
    }

    @Override
    public boolean remove(Object key, Object value) {
        return removeNode(hash(key), key, value, true, true).isValue();
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        int hash, index;
        V v;
        if ((index = getNode((hash = hash(key)), key)) >= 0 &&
                ((v = table[index].value) == oldValue || (v != null && v.equals(oldValue)))) {
            table[index] = new YNode<>(hash, key, newValue, table[index].probes);
            return true;
        }
        return false;
    }

    @Override
    public V replace(K key, V value) {
        int hash, index;
        V v;
        if ((index = getNode((hash = hash(key)), key)) >= 0) {
            V oldValue = table[index].value;
            table[index] = new YNode<>(hash, key, value, table[index].probes);
            return oldValue;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method will, on a best-effort basis, throw a
     * {@link ConcurrentModificationException} if it is detected that the
     * mapping function modifies this map during computation.
     *
     * @throws ConcurrentModificationException if it is detected that the
     * mapping function modified this map
     */
    @Override
    public V computeIfAbsent(K key,
                             Function<? super K, ? extends V> mappingFunction) {
        if (mappingFunction == null)
            throw new NullPointerException();
        int hash = hash(key);
        YNode<K,V>[] tab = table;
        YNode<K,V> entry;
        int index;

        index = getNode(hash, key);
        if (index >= 0 && (entry = tab[index]).value != null) {
            return entry.value;
        }
        int mc = modCount;
        V v = mappingFunction.apply(key);
        if (mc != modCount) { throw new ConcurrentModificationException(); }
        if (v == null) {
            return null;
        }
        putVal(hash, key, v, false);
        return v;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method will, on a best-effort basis, throw a
     * {@link ConcurrentModificationException} if it is detected that the
     * remapping function modifies this map during computation.
     *
     * @throws ConcurrentModificationException if it is detected that the
     * remapping function modified this map
     */
    @Override
    public V computeIfPresent(K key,
                              BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null)
            throw new NullPointerException();
        YNode<K,V> oldValue;
        int hash = hash(key);
        int index = getNode(hash, key);
        if (index >= 0 && (oldValue = table[index]).value != null) {
            int mc = modCount;
            V v = remappingFunction.apply(key, oldValue.value);
            if (mc != modCount) { throw new ConcurrentModificationException(); }
            if (v != null) {
                table[index] = new YNode<>(hash, key, v, oldValue.probes);
                return v;
            } else
                removeNode(hash, key, null, false, true);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method will, on a best-effort basis, throw a
     * {@link ConcurrentModificationException} if it is detected that the
     * remapping function modifies this map during computation.
     *
     * @throws ConcurrentModificationException if it is detected that the
     * remapping function modified this map
     */
    @Override
    @SuppressWarnings("unchecked")
    public V compute(K key,
                     BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null)
            throw new NullPointerException();

        int hash = hash(key);
        int index = getNode(hash, key);
        YNode<K,V> oldValue = (index >= 0) ? table[index] : new YNode();

        int mc = modCount;
        V v = remappingFunction.apply(key, oldValue.value);
        if (mc != modCount) { throw new ConcurrentModificationException(); }
        if (v != null) {
            if (index >= 0) {
                table[index] = new YNode<>(hash, key, v, oldValue.probes);
//                modCount++;
            } else
                putVal(hash, key, v, false);
        } else
            // TBD: 2nd lookup to remove even though we have index
            removeNode(hash, key, null, false, true);
        return v;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method will, on a best-effort basis, throw a
     * {@link ConcurrentModificationException} if it is detected that the
     * remapping function modifies this map during computation.
     *
     * @throws ConcurrentModificationException if it is detected that the
     * remapping function modified this map
     */
    @Override
    public V merge(K key, V value,
                   BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        if (remappingFunction == null)
            throw new NullPointerException();

        final int hash = hash(key);
        final int index = getNode(hash, key);
        if (index >= 0) {
            int mc = modCount;
            V v = remappingFunction.apply(table[index].value, value);
            if (mc != modCount) { throw new ConcurrentModificationException(); }
            if (v != null) {
                if (index >= 0) {
                    table[index] = new YNode<>(hash, key, v, table[index].probes);
//                    modCount++;
                } else
                    putVal(hash, key, v, false);
            } else {
                // TBD: 2nd lookup to remove even though we have index
                removeNode(hash, key, null, false, true);
            }
            return v;
        } else {
            // put new key/value (even if null)
            putVal(hash, key, value, false);
        }
        return value;
    }

    @Override
    public void forEach(BiConsumer<? super K, ? super V> action) {
        YNode<K,V>[] tab;
        if (action == null)
            throw new NullPointerException();
        if (size > 0 && (tab = table) != null) {
            int mc = modCount;
            for (YNode<K,V> te : tab) {
                if (te.isValue()) {
                    action.accept(te.key, te.value);
                }
            }
            if (modCount != mc)
                throw new ConcurrentModificationException();
        }
    }

    @Override
    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
        super.replaceAll(function);
    }

    /* ------------------------------------------------------------ */
    // Cloning and serialization

    /**
     * Returns a shallow copy of this {@code HashMap} instance: the keys and
     * values themselves are not cloned.
     *
     * @return a shallow copy of this map
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object clone() {
        HashMap<K,V> result;
        try {
            result = (HashMap<K,V>)super.clone();
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
        result.reinitialize();
        result.putMapEntries(this, false);
        return result;
    }

    // These methods are also used when serializing HashSets
    final float loadFactor() { return loadFactor; }
    final int capacity() {
        return (table != null) ? table.length :
                (threshold > 0) ? threshold :
                DEFAULT_INITIAL_CAPACITY;
    }

    /**
     * Saves this map to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws IOException if an I/O error occurs
     * @serialData The <i>capacity</i> of the HashMap (the length of the
     *             bucket array) is emitted (int), followed by the
     *             <i>size</i> (an int, the number of key-value
     *             mappings), followed by the key (Object) and value (Object)
     *             for each key-value mapping.  The key-value mappings are
     *             emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws IOException {
        int buckets = capacity();
        // Write out the threshold, loadfactor, and any hidden stuff
        s.defaultWriteObject();
        s.writeInt(buckets);
        s.writeInt(size);
        internalWriteEntries(s);
    }

    /**
     * Reconstitutes this map from a stream (that is, deserializes it).
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     * @throws IOException if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
        throws IOException, ClassNotFoundException {
        // Read in the threshold (ignored), loadfactor, and any hidden stuff
        s.defaultReadObject();
        reinitialize();
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new InvalidObjectException("Illegal load factor: " +
                    loadFactor);
        s.readInt();                // Read and ignore number of buckets
        int mappings = s.readInt(); // Read number of mappings (size)
        if (mappings < 0)
            throw new InvalidObjectException("Illegal mappings count: " +
                    mappings);
        else if (mappings > 0) { // (if zero, use defaults)
            // Size the table using given load factor only if within
            // range of 0.25...4.0
            float lf = Math.min(Math.max(0.25f, loadFactor), 4.0f);
            float fc = (float)mappings / lf + 1.0f;
            int cap = ((fc < DEFAULT_INITIAL_CAPACITY) ?
                    DEFAULT_INITIAL_CAPACITY :
                    (fc >= MAXIMUM_CAPACITY) ?
                            MAXIMUM_CAPACITY :
                            tableSizeFor((int)fc));
            float ft = (float)cap * lf;
            threshold = ((cap < MAXIMUM_CAPACITY && ft < MAXIMUM_CAPACITY) ?
                    (int)ft : Integer.MAX_VALUE);

            // Check Map.Entry[].class since it's the nearest public type to
            // what we're actually creating.
//            SharedSecrets.getJavaObjectInputStreamAccess().checkArray(s, Map.Entry[].class, cap);
            @SuppressWarnings({"rawtypes","unchecked"})
            YNode<K,V>[] tab = (YNode<K,V>[])new YNode[cap];
            table = tab;

            // Read the keys and values, and put the mappings in the HashMap
            for (int i = 0; i < mappings; i++) {
                @SuppressWarnings("unchecked")
                K key = (K) s.readObject();
                @SuppressWarnings("unchecked")
                V value = (V) s.readObject();
                putVal(hash(key), key, value, false);
            }
        }
    }

    /* ------------------------------------------------------------ */
    // iterators

    abstract class HashIterator {
        int next;        // next entry to return
        int current;     // current entry
        int expectedModCount;  // for fast-fail
        int count;

        @SuppressWarnings("initialization")
        HashIterator() {
            expectedModCount = modCount;
            current = -1;
            next = (size > 0 && table != null) ? findNext(0) : -1;
            count = 0;
        }

        public final boolean hasNext() {
            return next >= 0;
        }

        final Entry<K,V> nextNode() {
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException("ex: " + expectedModCount + " != " + modCount);
            if (!hasNext())
                throw new NoSuchElementException();
            current = next;
            assert current >= 0;

            next = (current + REHASH_HASH) & (table.length - 1);
            next = (next == 0) ? -1 : findNext(next);
            return new YNodeWrapper(current);
        }

        public final void remove() {
            if (current < 0 || current > table.length)
                throw new IllegalStateException();
            if (modCount != expectedModCount)
                throw new ConcurrentModificationException();
            YNode<K, V> p = table[current];
            removeNode(p.hash, p.key, null, false, false);
            if (table[current].isValue()) {
                // a node was moved into current
                next = current;
            }
            current = -1;
            expectedModCount = modCount;
        }

        /**
         * Find the next value entry in the rehash sequence.
         */
        private final int findNext(int index) {
            final YNode<K,V>[] t = table;
            final int lowbitmask = table.length - 1;
            index = index & lowbitmask;
            int count = 0;
            while (!t[index].isValue()) {
                count ++;
                index = (index + REHASH_HASH) & lowbitmask;
		if (index == 0)
		    return -1;
            }
            return index;
        }
    }

    final class KeyIterator extends HashIterator
            implements Iterator<K> {
        public final K next() { return nextNode().getKey(); }
    }

    final class ValueIterator extends HashIterator
            implements Iterator<V> {
        public final V next() { return nextNode().getValue(); }
    }

    final class EntryIterator extends HashIterator
            implements Iterator<Map.Entry<K,V>> {
        public final Map.Entry<K,V> next() { return nextNode(); }
    }

    /**
     * Reset to initial default state.  Called by clone and readObject.
     */
    void reinitialize() {
        table = null;
        entrySet = null;
        keySet = null;
        values = null;
        modCount = 0;
        threshold = 0;
        size = 0;
    }

    // Called only from writeObject, to ensure compatible ordering.
    void internalWriteEntries(java.io.ObjectOutputStream s) throws IOException {
        YNode<K,V>[] tab;
        if (size > 0 && (tab = table) != null) {
            for (YNode<K,V> te : tab) {
                if (te.isValue()) {
                    s.writeObject(te.key);
                    s.writeObject(te.value);
                }
            }
        }
    }


    /**
     * Check each entry in the table.
     *  - FindNode will find it from the key.
     *  - the probes value is correct.
     */
    boolean isTableOk(final YNode<K,V>[] tab, String msg) {
        int n;
        if (!VERIFYTABLEOK || tab == null || (n = tab.length) == 0)
            return true;
        boolean ok = true;
        int valueEntries = 0;
        for (int index = 0; index < tab.length; index++) {
            final YNode<K,V> te = tab[index];
            if (te.isValue()) {
                valueEntries++;
                if (te.key == this || te.value == this)
                    continue;   // skip self referential entries
                int hash = hash(te.key);
                int th = hash(te.key);
                if (th != te.hash) {
                    ok = false;
                    debug2("ERROR: computed hash not equal stored hash: th: " + th, index, te);
                }

                int findex = getNode(hash, te.key);
                if (findex < index) {
                    ok = false;
                    debug2("ERROR: getNode entry not found/found at wrong index: " + findex,
                            index , te);
                }
                if (findex >= 0) {
                    int h = hash;
                    for (int probes = 1; probes <= tab.length; probes++, h += REHASH_HASH) {
                        int i = (n - 1) & h;
                        if (i == findex) {
                            if (probes != te.probes) {
                                ok = false;
                                debug2("ERROR: computed probes not equal recorded " +
                                        "probes: " + probes, findex, te);
                            }
                            break;
                        }
                        if (probes == 500) {
                            debug2("probes > 500: " + probes, findex, te);
                        }
                    }
                }
                // Check for duplicate entry
                for (int j = index + 1; j < tab.length; j++) {
                    if (te.hash == tab[j].hash &&
                            te.key.equals(tab[j].key)) {
                        debug2("ERROR: YNode at index ", index, te);
                        debug2("ERROR: duplicate YNode", j, tab[j]);
                    }
                }
            }
        }
        if (valueEntries != size()) {
            debug2("ERROR: size wrong: " + valueEntries, size(), new YNode<K,V>());
            ok = false;
        }
        if (!ok) {
            if (System.out != null) {
                Thread.dumpStack();
                dumpTable(table, msg);
            }
        }
        return ok;
    }

    /**
     * Print stats of the table to the a stream.
     * @param out a stream
     */
    public void dumpStats(PrintStream out) {
        out.printf("%s instance: size: %d%n", this.getClass().getName(), this.size());
        long size = heapSize();
        long bytesPer = (this.size != 0) ? size / this.size() : 0;
        out.printf("    heap size: %d(bytes), avg bytes per entry: %d, table len: %d%n",
                size, bytesPer, (table != null) ? table.length : 0);
        long[] types = entryTypes();
        out.printf("    values: %d, empty: %d%n",
                types[0], types[1]);
        printStats(out, "hash collisions", entryRehashes());
        printStats(out, "getProbes      ", minCounts(getProbes));
        printStats(out, "putProbes      ", minCounts(putProbes));
        printStats(out, "notFoundProbes ", minCounts(notFoundProbes));

        isTableOk(table, "dumpStats");
    }

    private void printStats(PrintStream out, String label, int[] hist) {
        if (hist.length > 1) {
            out.printf("    %s: max: %d, mean: %3.2f, stddev: %3.2f, %s%n",
                    label, hist.length - 1,
                    computeMean(hist), computeStdDev(hist),
                    Arrays.toString(hist));
        } else if (hist.length > 0) {
            out.printf("    %s: max: %d, %s%n",
                    label, hist.length - 1,
                    Arrays.toString(hist));
        } else {
            out.printf("    %s: n/a%n", label);
        }
    }

    private double computeStdDev(int[] hist) {
        double mean = computeMean(hist);
        double sum = 0.0f;
        long count = 0L;
        for (int i = 1; i < hist.length; i++) {
            count += hist[i];
            sum += (i - mean) * (i - mean) * hist[i];
        }
        return Math.sqrt(sum / (count - 1));
    }

    private double computeMean(int[] hist) {
        long sum = 0L;
        long count = 0;
        for (int i = 1; i < hist.length; i++) {
            count += hist[i];
            sum += i * hist[i];
        }
        return (double)sum / (double)count;
    }

    private long[] entryTypes() {
        long[] counts = new long[2];
        if (table == null)
            return counts;
        for (YNode<K,V> te : table) {
            if (te.isEmpty())
                counts[1]++;
            else
                counts[0]++;
        }
        return counts;
    }

    private int[] incProbeCount(int[] counters, int probes) {
        if (counters == null)
            counters = new int[Math.max(probes + 1, 16)];
        else if (probes >= counters.length)
            counters = Arrays.copyOf(counters, Math.max(probes + 1, counters.length * 2));
        counters[probes]++;
        return counters;
    }


    // Returns a histogram array of the number of rehashs needed to find each key.
    private int[] entryRehashes() {
        if (table == null)
            return new int[0];
        int[] counts = new int[table.length + 1];
        YNode<K,V>[] tab = table;
        int n = tab.length;
        for (YNode<K,V> te : tab) {

            if (!te.isValue())
                continue;
            int h = te.hash;
            int count;
            final K key = te.key;
            K k;
            for (count = 1; count < tab.length; count++, h += REHASH_HASH) {
                final YNode<K, V> entry;
                if ((entry = tab[(n - 1) & h]).isValue() &&
                        entry.hash == te.hash &&
                        ((k = entry.key) == key || (k != null && k.equals(key)))) {
                    break;
                }
            }

            counts[count]++;
        }

        int i;
        for (i = counts.length - 1; i >= 0 && counts[i] == 0; i--) {
        }
        counts = Arrays.copyOf(counts, i + 1);
        return counts;
    }

    private int[] minCounts(int[] counts) {
        int i;
        for (i = counts.length - 1; i >= 0 && counts[i] == 0; i--) {
        }
        counts = Arrays.copyOf(counts, i + 1);
        return counts;
    }

    private long heapSize() {
        long acc = objectSizeMaybe(this);
        return acc + objectSizeMaybe(table);
    }

    private long objectSizeMaybe(Object o) {
        try {
            return (mObjectSize != null)
                    ? (long)mObjectSize.invoke(null, o)
                    : 0L;
        } catch (IllegalAccessException | InvocationTargetException e) {
            return 0L;
        }
    }

    private static boolean hasObjectSize = false;
    private static Method mObjectSize = getObjectSizeMethod();

    private static Method getObjectSizeMethod() {
        try {
            Method m = Objects.class.getDeclaredMethod("getObjectSize", Object.class);
            hasObjectSize = true;
            return m;
        } catch (NoSuchMethodException nsme) {
            return null;
        }
    }

}
