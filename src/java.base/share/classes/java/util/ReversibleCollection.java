/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * A linear collection that has a well-defined order and that is reversible.
 * Provides methods for a reverse-ordered view as well as convenient access
 * to elements at both ends.
 *
 * @param <E> the type of elements in this collection
 * @since XXX
 */
public interface ReversibleCollection<E> extends Collection<E> {
    /**
     * Returns a reversed-order view of this collection. If the implementation
     * permits modifications to this view, the modifications "write through"
     * to the underlying collection. Depending upon the implementation's
     * concurrent modification policy, changes to the underlying collection
     * may be visible in this reversed view.
     * @return a reversed-order view of this collection
     */
    ReversibleCollection<E> reversedCollection();

    /**
     * Adds an element at the front of this collection (optional operation).
     * @param e the element to be added
     * @throws NullPointerException if the specified element is null and this
     *         collection does not permit null elements
     */
    void addFirst(E e);

    /**
     * Adds an element at the end of this collection (optional operation).
     * Synonymous with add(E).
     * @param e the element to be added.
     * @throws NullPointerException if the specified element is null and this
     *         collection does not permit null elements
     */
    void addLast(E e);

    /**
     * Gets the element at the front of this collection.
     * @return the retrieved element
     * @throws NoSuchElementException if this collection is empty
     */
    E getFirst();

    /**
     * Gets the element at the end of this collection.
     * @return the retrieved element
     * @throws NoSuchElementException if this collection is empty
     */
    E getLast();

    /**
     * Removes and returns the first element of this collection (optional operation).
     * @return the removed element
     * @throws NoSuchElementException if this collection is empty
     */
    E removeFirst();

    /**
     * Removes and returns the last element of this collection (optional operation).
     * @return the removed element
     * @throws NoSuchElementException if this collection is empty
     */
    E removeLast();
}
