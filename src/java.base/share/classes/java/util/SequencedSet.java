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
 * A collection that is both a {@link SequencedCollection} and a {@link Set}. As such,
 * it can be thought of either as a {@code Set} that also has a well-defined
 * <a href="SequencedCollection.html#encounter">encounter order</a>, or as a
 * {@code SequencedCollection} that also has unique elements.
 * <p>
 * This interface has the same requirements on the {@code equals} and {@code hashCode}
 * methods as defined by {@link Set#equals Set.equals} and {@link Set#hashCode Set.hashCode}.
 * Thus, a {@code Set} and a {@code SequencedSet} will compare equals if and only
 * if they have equal elements, irrespective of ordering.
 * <p>
 * {@code SequencedSet} defines the {@link #reversed} method, which provides a
 * reverse-ordered <a href="Collection.html#view">view</a> of this set. The only difference
 * from the {@link SequencedCollection#reversed SequencedCollection.reversed} method is
 * that the return type of {@code SequencedSet.reversed} is {@code SequencedSet}.
 *
 * <h2><a id="unmodifiable">Unmodifiable Sets with Well-defined Encounter Order</a></h2>
 * <p>The {@link #of(Object...) SequencedSet.of} and {@link #copyOf
 * SequencedSet.copyOf} static factory methods provide a convenient way to
 * create unmodifiable sets with well-defined encounter order. The {@code
 * SequencedSet} instances created by these methods have the following characteristics:
 *
 * <ul>
 * <li>They are <a href="Collection.html#unmodifiable"><i>unmodifiable</i></a>. Elements cannot
 * be added or removed. Calling any mutator method on the collection
 * will always cause {@code UnsupportedOperationException} to be thrown.
 * However, if the contained elements are themselves mutable, this may cause the
 * Set to behave inconsistently or its contents to appear to change.
 * <li>They disallow {@code null} elements. Attempts to create them with
 * {@code null} elements result in {@code NullPointerException}.
 * <li>They are serializable if all elements are serializable, but their
 * reverse-ordered views are never serializable.
 * <li>They reject duplicate elements at creation time. Duplicate elements
 * passed to a static factory method result in {@code IllegalArgumentException}.
 * <li>The iteration order of set elements is explicitly specified at creation.
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
 * @param <E> the type of elements in this sequenced set
 * @since 21
 */
public interface SequencedSet<E> extends SequencedCollection<E>, Set<E> {
    /**
     * {@inheritDoc}
     *
     * @return a reverse-ordered view of this collection, as a {@code SequencedSet}
     */
    SequencedSet<E> reversed();

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} set containing zero
     * elements in an explicit encounter order.
     *
     * @param <E> the {@code SequencedSet}'s element type
     * @return an empty {@code SequencedSet}
     *
     * @since 26
     */
    static <E> SequencedSet<E> of() {
        return ImmutableCollections.sequencedSet(List.of());
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} set containing one
     * element in an explicit encounter order.
     *
     * @param <E> the {@code SequencedSet}'s element type
     * @param e1 the single element
     * @return a {@code SequencedSet} containing the specified element
     * @throws NullPointerException if the element is {@code null}
     *
     * @since 26
     */
    static <E> SequencedSet<E> of(E e1) {
        return ImmutableCollections.sequencedSet(List.of(e1));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} set containing two
     * elements in the given order.
     *
     * @param <E> the {@code SequencedSet}'s element type
     * @param e1 the first element
     * @param e2 the second element
     * @return a {@code SequencedSet} containing the specified elements in the specified order
     * @throws IllegalArgumentException if the elements are duplicates
     * @throws NullPointerException if an element is {@code null}
     *
     * @since 26
     */
    static <E> SequencedSet<E> of(E e1, E e2) {
        return ImmutableCollections.sequencedSet(List.of(e1, e2));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} set containing three
     * elements in the given order.
     *
     * @param <E> the {@code SequencedSet}'s element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @return a {@code SequencedSet} containing the specified elements in the specified order
     * @throws IllegalArgumentException if there are any duplicate elements
     * @throws NullPointerException if an element is {@code null}
     *
     * @since 26
     */
    static <E> SequencedSet<E> of(E e1, E e2, E e3) {
        return ImmutableCollections.sequencedSet(List.of(e1, e2, e3));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} set containing four
     * elements in the given order.
     *
     * @param <E> the {@code SequencedSet}'s element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @return a {@code SequencedSet} containing the specified elements in the specified order
     * @throws IllegalArgumentException if there are any duplicate elements
     * @throws NullPointerException if an element is {@code null}
     *
     * @since 26
     */
    static <E> SequencedSet<E> of(E e1, E e2, E e3, E e4) {
        return ImmutableCollections.sequencedSet(List.of(e1, e2, e3, e4));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} set containing five
     * elements in the given order.
     *
     * @param <E> the {@code SequencedSet}'s element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @param e5 the fifth element
     * @return a {@code SequencedSet} containing the specified elements in the specified order
     * @throws IllegalArgumentException if there are any duplicate elements
     * @throws NullPointerException if an element is {@code null}
     *
     * @since 26
     */
    static <E> SequencedSet<E> of(E e1, E e2, E e3, E e4, E e5) {
        return ImmutableCollections.sequencedSet(List.of(e1, e2, e3, e4, e5));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} set containing six
     * elements in the given order.
     *
     * @param <E> the {@code SequencedSet}'s element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @param e5 the fifth element
     * @param e6 the sixth element
     * @return a {@code SequencedSet} containing the specified elements in the specified order
     * @throws IllegalArgumentException if there are any duplicate elements
     * @throws NullPointerException if an element is {@code null}
     *
     * @since 26
     */
    static <E> SequencedSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6) {
        return ImmutableCollections.sequencedSet(List.of(e1, e2, e3, e4, e5,
                e6));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} set containing seven
     * elements in the given order.
     *
     * @param <E> the {@code SequencedSet}'s element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @param e5 the fifth element
     * @param e6 the sixth element
     * @param e7 the seventh element
     * @return a {@code SequencedSet} containing the specified elements in the specified order
     * @throws IllegalArgumentException if there are any duplicate elements
     * @throws NullPointerException if an element is {@code null}
     *
     * @since 26
     */
    static <E> SequencedSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
        return ImmutableCollections.sequencedSet(List.of(e1, e2, e3, e4, e5,
                e6, e7));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} set containing eight
     * elements in the given order.
     *
     * @param <E> the {@code SequencedSet}'s element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @param e5 the fifth element
     * @param e6 the sixth element
     * @param e7 the seventh element
     * @param e8 the eighth element
     * @return a {@code SequencedSet} containing the specified elements in the specified order
     * @throws IllegalArgumentException if there are any duplicate elements
     * @throws NullPointerException if an element is {@code null}
     *
     * @since 26
     */
    static <E> SequencedSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
        return ImmutableCollections.sequencedSet(List.of(e1, e2, e3, e4, e5,
                e6, e7, e8));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} set containing nine
     * elements in the given order.
     *
     * @param <E> the {@code SequencedSet}'s element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @param e5 the fifth element
     * @param e6 the sixth element
     * @param e7 the seventh element
     * @param e8 the eighth element
     * @param e9 the ninth element
     * @return a {@code SequencedSet} containing the specified elements in the specified order
     * @throws IllegalArgumentException if there are any duplicate elements
     * @throws NullPointerException if an element is {@code null}
     *
     * @since 26
     */
    static <E> SequencedSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {
        return ImmutableCollections.sequencedSet(List.of(e1, e2, e3, e4, e5,
                e6, e7, e8, e9));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} set containing ten
     * elements in the given order.
     *
     * @param <E> the {@code SequencedSet}'s element type
     * @param e1 the first element
     * @param e2 the second element
     * @param e3 the third element
     * @param e4 the fourth element
     * @param e5 the fifth element
     * @param e6 the sixth element
     * @param e7 the seventh element
     * @param e8 the eighth element
     * @param e9 the ninth element
     * @param e10 the tenth element
     * @return a {@code SequencedSet} containing the specified elements in the specified order
     * @throws IllegalArgumentException if there are any duplicate elements
     * @throws NullPointerException if an element is {@code null}
     *
     * @since 26
     */
    static <E> SequencedSet<E> of(E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) {
        return ImmutableCollections.sequencedSet(List.of(e1, e2, e3, e4, e5,
                e6, e7, e8, e9, e10));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} set containing an
     * arbitrary number of elements in the given order.
     *
     * @apiNote
     * This method also accepts a single array as an argument. The element type of
     * the resulting set will be the component type of the array, and the size of
     * the set will be equal to the length of the array. To create a set with
     * a single element that is an array, do the following:
     *
     * <pre>{@code
     *     String[] array = ... ;
     *     SequencedSet<String[]> list = SequencedSet.<String[]>of(array);
     * }</pre>
     *
     * This will cause the {@link SequencedSet#of(Object) SequencedSet.of(E)} method
     * to be invoked instead.
     *
     * @param <E> the {@code SequencedSet}'s element type
     * @param elements the elements to be contained in the set
     * @return a {@code SequencedSet} containing the specified elements in the specified order
     * @throws IllegalArgumentException if there are any duplicate elements
     * @throws NullPointerException if an element is {@code null} or if the array is {@code null}
     *
     * @since 26
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    static <E> SequencedSet<E> of(E... elements) {
        return ImmutableCollections.sequencedSet(List.of(elements));
    }

    /**
     * Returns an {@linkplain ##unmodifiable unmodifiable} set containing the
     * elements of the given Collection in the same encounter order. The given
     * Collection must not be null, and it must not contain any null elements.
     * If the given Collection contains duplicate elements, the first
     * encountered element of the duplicates is preserved. If the given
     * Collection is subsequently modified, the returned Set will not reflect
     * such modifications.
     *
     * @implNote
     * If the given Collection is an {@linkplain ##unmodifiable unmodifiable}
     * set with a well-defined encounter order, calling copyOf will generally
     * not create a copy.
     *
     * @param <E> the {@code SequencedSet}'s element type
     * @param coll a {@code Collection} from which elements are drawn, must be non-null
     * @return a {@code SequencedSet} containing the elements of the given {@code Collection}
     * @throws NullPointerException if coll is null, or if it contains any nulls
     * @since 26
     */
    @SuppressWarnings("unchecked")
    static <E> SequencedSet<E> copyOf(Collection<? extends E> coll) {
        if (coll instanceof ImmutableCollections.WrapperSequencedSet)
            return (SequencedSet<E>) coll;
        return ImmutableCollections.sequencedSetDedup(List.copyOf(coll));
    }
}
