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
 * A collection that has a well-defined encounter order, that supports operations at both ends,
 * and that is reversible. The elements of a sequenced collection have an <a id="encounter">
 * <i>encounter order</i></a>, where conceptually the elements have a linear arrangement
 * from the first element to the last element. Given any two elements, one element is
 * either before (closer to the first element) or after (closer to the last element)
 * the other element.
 * <p>
 * (Note that this definition does not imply anything about physical positioning
 * of elements, such as their locations in a computer's memory.)
 * <p>
 * Several methods inherited from the {@link Collection} interface are required to operate
 * on elements according to this collection's encounter order. For instance, the
 * {@link Collection#iterator iterator} method provides elements starting from the first element,
 * proceeding through successive elements, until the last element. Other methods that are
 * required to operate on elements in encounter order include the following:
 * {@link Iterable#forEach forEach}, {@link Collection#parallelStream parallelStream},
 * {@link Collection#spliterator spliterator}, {@link Collection#stream stream},
 * and all overloads of the {@link Collection#toArray toArray} method.
 * <p>
 * This interface provides methods to add, retrieve, and remove elements at either end
 * of the collection.
 * <p>
 * This interface also defines the {@link #reversed reversed} method, which provides
 * a reverse-ordered <a href="Collection.html#view">view</a> of this collection.
 * In the reverse-ordered view, the concepts of first and last are inverted, as are
 * the concepts of successor and predecessor. The first element of this collection is
 * the last element of the reverse-ordered view, and vice-versa. The successor of some
 * element in this collection is its predecessor in the reversed view, and vice-versa. All
 * methods that respect the encounter order of the collection operate as if the encounter order
 * is inverted. For instance, the {@link #iterator} method of the reversed view reports the
 * elements in order from the last element of this collection to the first. The availability of
 * the {@code reversed} method, and its impact on the ordering semantics of all applicable
 * methods, allow convenient iteration, searching, copying, and streaming of the elements of
 * this collection in either forward order or reverse order.
 * <p>
 * This class is a member of the
 * <a href="{@docRoot}/java.base/java/util/package-summary.html#CollectionsFramework">
 * Java Collections Framework</a>.
 *
 * @apiNote
 * This interface does not impose any requirements on the {@code equals} and {@code hashCode}
 * methods, because requirements imposed by sub-interfaces {@link List} and {@link SequencedSet}
 * (which inherits requirements from {@link Set}) would be in conflict. See the specifications for
 * {@link Collection#equals Collection.equals} and {@link Collection#hashCode Collection.hashCode}
 * for further information.
 *
 * @param <E> the type of elements in this collection
 * @since 21
 */
public interface SequencedCollection<E> extends Collection<E> {
    /**
     * Returns a reverse-ordered <a href="Collection.html#view">view</a> of this collection.
     * The encounter order of elements in the returned view is the inverse of the encounter
     * order of elements in this collection. The reverse ordering affects all order-sensitive
     * operations, including those on the view collections of the returned view. If the collection
     * implementation permits modifications to this view, the modifications "write through" to the
     * underlying collection. Changes to the underlying collection might or might not be visible
     * in this reversed view, depending upon the implementation.
     *
     * @return a reverse-ordered view of this collection
     */
    SequencedCollection<E> reversed();

    /**
     * Adds an element as the first element of this collection (optional operation).
     * After this operation completes normally, the given element will be a member of
     * this collection, and it will be the first element in encounter order.
     *
     * @implSpec
     * The implementation in this interface always throws {@code UnsupportedOperationException}.
     *
     * @param e the element to be added
     * @throws NullPointerException if the specified element is null and this
     *         collection does not permit null elements
     * @throws UnsupportedOperationException if this collection implementation
     *         does not support this operation
     */
    default void addFirst(E e) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds an element as the last element of this collection (optional operation).
     * After this operation completes normally, the given element will be a member of
     * this collection, and it will be the last element in encounter order.
     *
     * @implSpec
     * The implementation in this interface always throws {@code UnsupportedOperationException}.
     *
     * @param e the element to be added.
     * @throws NullPointerException if the specified element is null and this
     *         collection does not permit null elements
     * @throws UnsupportedOperationException if this collection implementation
     *         does not support this operation
     */
    default void addLast(E e) {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the first element of this collection.
     *
     * @implSpec
     * The implementation in this interface obtains an iterator of this collection, and
     * then it obtains an element by calling the iterator's {@code next} method. Any
     * {@code NoSuchElementException} thrown is propagated. Otherwise, it returns
     * the element.
     *
     * @return the retrieved element
     * @throws NoSuchElementException if this collection is empty
     */
    default E getFirst() {
        return this.iterator().next();
    }

    /**
     * Gets the last element of this collection.
     *
     * @implSpec
     * The implementation in this interface obtains an iterator of the reversed view
     * of this collection, and then it obtains an element by calling the iterator's
     * {@code next} method. Any {@code NoSuchElementException} thrown is propagated.
     * Otherwise, it returns the element.
     *
     * @return the retrieved element
     * @throws NoSuchElementException if this collection is empty
     */
    default E getLast() {
        return this.reversed().iterator().next();
    }

    /**
     * Removes and returns the first element of this collection (optional operation).
     *
     * @implSpec
     * The implementation in this interface obtains an iterator of this collection, and then
     * it obtains an element by calling the iterator's {@code next} method. Any
     * {@code NoSuchElementException} thrown is propagated. It then calls the iterator's
     * {@code remove} method. Any {@code UnsupportedOperationException} thrown is propagated.
     * Then, it returns the element.
     *
     * @return the removed element
     * @throws NoSuchElementException if this collection is empty
     * @throws UnsupportedOperationException if this collection implementation
     *         does not support this operation
     */
    default E removeFirst() {
        var it = this.iterator();
        E e = it.next();
        it.remove();
        return e;
    }

    /**
     * Removes and returns the last element of this collection (optional operation).
     *
     * @implSpec
     * The implementation in this interface obtains an iterator of the reversed view of this
     * collection, and then it obtains an element by calling the iterator's {@code next} method.
     * Any {@code NoSuchElementException} thrown is propagated. It then calls the iterator's
     * {@code remove} method. Any {@code UnsupportedOperationException} thrown is propagated.
     * Then, it returns the element.
     *
     * @return the removed element
     * @throws NoSuchElementException if this collection is empty
     * @throws UnsupportedOperationException if this collection implementation
     *         does not support this operation
     */
    default E removeLast() {
        var it = this.reversed().iterator();
        E e = it.next();
        it.remove();
        return e;
    }
}
