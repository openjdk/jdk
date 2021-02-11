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
 * A set that has a well-defined order. Provides methods for
 * convenient access to elements at both ends, as well as a reversed-order view.
 *
 * @param <E> the type of elements in this collection
 * @since XXX
 */
public interface OrderedSet<E> extends Set<E>, OrderedCollection<E> {
    /**
     * Throws UnsupportedOperationException.
     * @param e the element to be added
     * @throws UnsupportedOperationException always
     */
    default void addFirst(E e) { throw new UnsupportedOperationException(); }

    /**
     * Throws UnsupportedOperationException.
     * @param e the element to be added
     * @throws UnsupportedOperationException always
     */
    default void addLast(E e) { throw new UnsupportedOperationException(); }

    /**
     * Returns a reversed-order view of this set. If the implementation
     * permits modifications to this view, the modifications "write through"
     * to the underlying collection. Depending upon the implementation's
     * concurrent modification policy, changes to the underlying collection
     * may be visible in this reversed view.
     * @return a reversed-order view
     */
    OrderedSet<E> reversed();
}
