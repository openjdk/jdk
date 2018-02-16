/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.collections;

import java.util.Iterator;

/**
 * Memory efficient set data structure.
 *
 * @since 1.0
 */
public interface EconomicSet<E> extends UnmodifiableEconomicSet<E> {

    /**
     * Adds {@code element} to this set if it is not already present.
     *
     * @return {@code true} if this set did not already contain {@code element}.
     * @since 1.0
     */
    boolean add(E element);

    /**
     * Removes {@code element} from this set if it is present. This set will not contain
     * {@code element} once the call returns.
     *
     * @since 1.0
     */
    void remove(E element);

    /**
     * Removes all of the elements from this set. The set will be empty after this call returns.
     *
     * @since 1.0
     */
    void clear();

    /**
     * Adds all of the elements in {@code other} to this set if they're not already present.
     *
     * @since 1.0
     */
    default void addAll(EconomicSet<E> other) {
        addAll(other.iterator());
    }

    /**
     * Adds all of the elements in {@code values} to this set if they're not already present.
     *
     * @since 1.0
     */
    default void addAll(Iterable<E> values) {
        addAll(values.iterator());
    }

    /**
     * Adds all of the elements enumerated by {@code iterator} to this set if they're not already
     * present.
     *
     * @since 1.0
     */
    default void addAll(Iterator<E> iterator) {
        while (iterator.hasNext()) {
            add(iterator.next());
        }
    }

    /**
     * Removes from this set all of its elements that are contained in {@code other}.
     *
     * @since 1.0
     */
    default void removeAll(EconomicSet<E> other) {
        removeAll(other.iterator());
    }

    /**
     * Removes from this set all of its elements that are contained in {@code values}.
     *
     * @since 1.0
     */
    default void removeAll(Iterable<E> values) {
        removeAll(values.iterator());
    }

    /**
     * Removes from this set all of its elements that are enumerated by {@code iterator}.
     *
     * @since 1.0
     */
    default void removeAll(Iterator<E> iterator) {
        while (iterator.hasNext()) {
            remove(iterator.next());
        }
    }

    /**
     * Removes from this set all of its elements that are not contained in {@code other}.
     *
     * @since 1.0
     */
    default void retainAll(EconomicSet<E> other) {
        Iterator<E> iterator = iterator();
        while (iterator.hasNext()) {
            E key = iterator.next();
            if (!other.contains(key)) {
                iterator.remove();
            }
        }
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements with the
     * default {@link Equivalence#DEFAULT} comparison strategy.
     *
     * @since 1.0
     */
    static <E> EconomicSet<E> create() {
        return EconomicSet.create(Equivalence.DEFAULT);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements.
     *
     * @since 1.0
     */
    static <E> EconomicSet<E> create(Equivalence strategy) {
        return EconomicMapImpl.create(strategy, true);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements with the
     * default {@link Equivalence#DEFAULT} comparison strategy and inserts all elements of the
     * specified collection.
     *
     * @since 1.0
     */
    static <E> EconomicSet<E> create(int initialCapacity) {
        return EconomicSet.create(Equivalence.DEFAULT, initialCapacity);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements with the
     * default {@link Equivalence#DEFAULT} comparison strategy and inserts all elements of the
     * specified collection.
     *
     * @since 1.0
     */
    static <E> EconomicSet<E> create(UnmodifiableEconomicSet<E> c) {
        return EconomicSet.create(Equivalence.DEFAULT, c);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements and
     * initializes with the given capacity.
     *
     * @since 1.0
     */
    static <E> EconomicSet<E> create(Equivalence strategy, int initialCapacity) {
        return EconomicMapImpl.create(strategy, initialCapacity, true);
    }

    /**
     * Creates a new set guaranteeing insertion order when iterating over its elements and inserts
     * all elements of the specified collection.
     *
     * @since 1.0
     */
    static <E> EconomicSet<E> create(Equivalence strategy, UnmodifiableEconomicSet<E> c) {
        return EconomicMapImpl.create(strategy, c, true);
    }
}
