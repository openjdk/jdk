/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * <p>Hash table and linked list implementation of the {@code Set} interface,
 * with well-defined encounter order.  This implementation differs from
 * {@code HashSet} in that it maintains a doubly-linked list running through
 * all of its entries.  This linked list defines the encounter order (iteration
 * order), which is the order in which elements were inserted into the set
 * (<i>insertion-order</i>). The least recently inserted element (the eldest) is
 * first, and the youngest element is last. Note that encounter order is <i>not</i> affected
 * if an element is <i>re-inserted</i> into the set with the {@code add} method.
 * (An element {@code e} is reinserted into a set {@code s} if {@code s.add(e)} is
 * invoked when {@code s.contains(e)} would return {@code true} immediately prior to
 * the invocation.) The reverse-ordered view of this set is in the opposite order, with
 * the youngest element appearing first and the eldest element appearing last. The encounter
 * order of elements already in the set can be changed by using the
 * {@link #addFirst addFirst} and {@link #addLast addLast} methods.
 *
 * <p>This implementation spares its clients from the unspecified, generally
 * chaotic ordering provided by {@link HashSet}, without incurring the
 * increased cost associated with {@link TreeSet}.  It can be used to
 * produce a copy of a set that has the same order as the original, regardless
 * of the original set's implementation:
 * <pre>{@code
 *     void foo(Set<String> s) {
 *         Set<String> copy = new LinkedHashSet<>(s);
 *         ...
 *     }
 * }</pre>
 * This technique is particularly useful if a module takes a set on input,
 * copies it, and later returns results whose order is determined by that of
 * the copy.  (Clients generally appreciate having things returned in the same
 * order they were presented.)
 *
 * <p>This class provides all of the optional {@link Set} and {@link SequencedSet}
 * operations, and it permits null elements. Like {@code HashSet}, it provides constant-time
 * performance for the basic operations ({@code add}, {@code contains} and
 * {@code remove}), assuming the hash function disperses elements
 * properly among the buckets.  Performance is likely to be just slightly
 * below that of {@code HashSet}, due to the added expense of maintaining the
 * linked list, with one exception: Iteration over a {@code LinkedHashSet}
 * requires time proportional to the <i>size</i> of the set, regardless of
 * its capacity.  Iteration over a {@code HashSet} is likely to be more
 * expensive, requiring time proportional to its <i>capacity</i>.
 *
 * <p>A linked hash set has two parameters that affect its performance:
 * <i>initial capacity</i> and <i>load factor</i>.  They are defined precisely
 * as for {@code HashSet}.  Note, however, that the penalty for choosing an
 * excessively high value for initial capacity is less severe for this class
 * than for {@code HashSet}, as iteration times for this class are unaffected
 * by capacity.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * If multiple threads access a linked hash set concurrently, and at least
 * one of the threads modifies the set, it <em>must</em> be synchronized
 * externally.  This is typically accomplished by synchronizing on some
 * object that naturally encapsulates the set.
 *
 * If no such object exists, the set should be "wrapped" using the
 * {@link Collections#synchronizedSet Collections.synchronizedSet}
 * method.  This is best done at creation time, to prevent accidental
 * unsynchronized access to the set: <pre>
 *   Set s = Collections.synchronizedSet(new LinkedHashSet(...));</pre>
 *
 * <p>The iterators returned by this class's {@code iterator} method are
 * <em>fail-fast</em>: if the set is modified at any time after the iterator
 * is created, in any way except through the iterator's own {@code remove}
 * method, the iterator will throw a {@link ConcurrentModificationException}.
 * Thus, in the face of concurrent modification, the iterator fails quickly
 * and cleanly, rather than risking arbitrary, non-deterministic behavior at
 * an undetermined time in the future.
 *
 * <p>Note that the fail-fast behavior of an iterator cannot be guaranteed
 * as it is, generally speaking, impossible to make any hard guarantees in the
 * presence of unsynchronized concurrent modification.  Fail-fast iterators
 * throw {@code ConcurrentModificationException} on a best-effort basis.
 * Therefore, it would be wrong to write a program that depended on this
 * exception for its correctness:   <i>the fail-fast behavior of iterators
 * should be used only to detect bugs.</i>
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework">
 * Java Collections Framework</a>.
 *
 * @param <E> the type of elements maintained by this set
 *
 * @author  Josh Bloch
 * @see     Object#hashCode()
 * @see     Collection
 * @see     Set
 * @see     HashSet
 * @see     TreeSet
 * @see     Hashtable
 * @since   1.4
 */

public class LinkedHashSet<E>
    extends HashSet<E>
    implements SequencedSet<E>, Cloneable, java.io.Serializable {

    @java.io.Serial
    private static final long serialVersionUID = -2851667679971038690L;

    /**
     * Constructs a new, empty linked hash set with the specified initial
     * capacity and load factor.
     *
     * @apiNote
     * To create a {@code LinkedHashSet} with an initial capacity that accommodates
     * an expected number of elements, use {@link #newLinkedHashSet(int) newLinkedHashSet}.
     *
     * @param      initialCapacity the initial capacity of the linked hash set
     * @param      loadFactor      the load factor of the linked hash set
     * @throws     IllegalArgumentException  if the initial capacity is less
     *               than zero, or if the load factor is nonpositive
     */
    public LinkedHashSet(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor, true);
    }

    /**
     * Constructs a new, empty linked hash set with the specified initial
     * capacity and the default load factor (0.75).
     *
     * @apiNote
     * To create a {@code LinkedHashSet} with an initial capacity that accommodates
     * an expected number of elements, use {@link #newLinkedHashSet(int) newLinkedHashSet}.
     *
     * @param   initialCapacity   the initial capacity of the LinkedHashSet
     * @throws  IllegalArgumentException if the initial capacity is less
     *              than zero
     */
    public LinkedHashSet(int initialCapacity) {
        super(initialCapacity, .75f, true);
    }

    /**
     * Constructs a new, empty linked hash set with the default initial
     * capacity (16) and load factor (0.75).
     */
    public LinkedHashSet() {
        super(16, .75f, true);
    }

    /**
     * Constructs a new linked hash set with the same elements as the
     * specified collection.  The linked hash set is created with an initial
     * capacity sufficient to hold the elements in the specified collection
     * and the default load factor (0.75).
     *
     * @param c  the collection whose elements are to be placed into
     *           this set
     * @throws NullPointerException if the specified collection is null
     */
    public LinkedHashSet(Collection<? extends E> c) {
        super(HashMap.calculateHashMapCapacity(Math.max(c.size(), 12)), .75f, true);
        addAll(c);
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@code Spliterator} over the elements in this set.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED},
     * {@link Spliterator#DISTINCT}, and {@code ORDERED}.  Implementations
     * should document the reporting of additional characteristic values.
     *
     * @implNote
     * The implementation creates a
     * <em><a href="Spliterator.html#binding">late-binding</a></em> spliterator
     * from the set's {@code Iterator}.  The spliterator inherits the
     * <em>fail-fast</em> properties of the set's iterator.
     * The created {@code Spliterator} additionally reports
     * {@link Spliterator#SUBSIZED}.
     *
     * @return a {@code Spliterator} over the elements in this set
     * @since 1.8
     */
    @Override
    public Spliterator<E> spliterator() {
        return Spliterators.spliterator(this, Spliterator.DISTINCT | Spliterator.ORDERED);
    }

    /**
     * Creates a new, empty LinkedHashSet suitable for the expected number of elements.
     * The returned set uses the default load factor of 0.75, and its initial capacity is
     * generally large enough so that the expected number of elements can be added
     * without resizing the set.
     *
     * @param numElements    the expected number of elements
     * @param <T>         the type of elements maintained by the new set
     * @return the newly created set
     * @throws IllegalArgumentException if numElements is negative
     * @since 19
     */
    public static <T> LinkedHashSet<T> newLinkedHashSet(int numElements) {
        if (numElements < 0) {
            throw new IllegalArgumentException("Negative number of elements: " + numElements);
        }
        return new LinkedHashSet<>(HashMap.calculateHashMapCapacity(numElements));
    }

    @SuppressWarnings("unchecked")
    LinkedHashMap<E, Object> map() {
        return (LinkedHashMap<E, Object>) map;
    }

    /**
     * {@inheritDoc}
     * <p>
     * If this set already contains the element, it is relocated if necessary so that it is
     * first in encounter order.
     *
     * @since 21
     */
    public void addFirst(E e) {
        map().putFirst(e, PRESENT);
    }

    /**
     * {@inheritDoc}
     * <p>
     * If this set already contains the element, it is relocated if necessary so that it is
     * last in encounter order.
     *
     * @since 21
     */
    public void addLast(E e) {
        map().putLast(e, PRESENT);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NoSuchElementException {@inheritDoc}
     * @since 21
     */
    public E getFirst() {
        return map().sequencedKeySet().getFirst();
    }

    /**
     * {@inheritDoc}
     *
     * @throws NoSuchElementException {@inheritDoc}
     * @since 21
     */
    public E getLast() {
        return map().sequencedKeySet().getLast();
    }

    /**
     * {@inheritDoc}
     *
     * @throws NoSuchElementException {@inheritDoc}
     * @since 21
     */
    public E removeFirst() {
        return map().sequencedKeySet().removeFirst();
    }

    /**
     * {@inheritDoc}
     *
     * @throws NoSuchElementException {@inheritDoc}
     * @since 21
     */
    public E removeLast() {
        return map().sequencedKeySet().removeLast();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Modifications to the reversed view are permitted and will be propagated to this set.
     * In addition, modifications to this set will be visible in the reversed view.
     *
     * @return {@inheritDoc}
     * @since 21
     */
    public SequencedSet<E> reversed() {
        class ReverseLinkedHashSetView extends AbstractSet<E> implements SequencedSet<E> {
            public int size()                  { return LinkedHashSet.this.size(); }
            public Iterator<E> iterator()      { return map().sequencedKeySet().reversed().iterator(); }
            public boolean add(E e)            { return LinkedHashSet.this.add(e); }
            public void addFirst(E e)          { LinkedHashSet.this.addLast(e); }
            public void addLast(E e)           { LinkedHashSet.this.addFirst(e); }
            public E getFirst()                { return LinkedHashSet.this.getLast(); }
            public E getLast()                 { return LinkedHashSet.this.getFirst(); }
            public E removeFirst()             { return LinkedHashSet.this.removeLast(); }
            public E removeLast()              { return LinkedHashSet.this.removeFirst(); }
            public SequencedSet<E> reversed()  { return LinkedHashSet.this; }
            public Object[] toArray() { return map().keysToArray(new Object[map.size()], true); }
            public <T> T[] toArray(T[] a) { return map().keysToArray(map.prepareArray(a), true); }
        }

        return new ReverseLinkedHashSetView();
    }
}
