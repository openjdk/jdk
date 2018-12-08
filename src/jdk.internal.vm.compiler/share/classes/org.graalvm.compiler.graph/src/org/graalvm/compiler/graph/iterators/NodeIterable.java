/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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


package org.graalvm.compiler.graph.iterators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.graalvm.compiler.graph.Node;

public interface NodeIterable<T extends Node> extends Iterable<T> {

    @SuppressWarnings("unchecked")
    default <F extends T> NodeIterable<F> filter(Class<F> clazz) {
        return (NodeIterable<F>) new FilteredNodeIterable<>(this).and(NodePredicates.isA(clazz));
    }

    default FilteredNodeIterable<T> filter(NodePredicate predicate) {
        return new FilteredNodeIterable<>(this).and(predicate);
    }

    default List<T> snapshot() {
        ArrayList<T> list = new ArrayList<>();
        snapshotTo(list);
        return list;
    }

    default void snapshotTo(Collection<? super T> to) {
        for (T n : this) {
            to.add(n);
        }
    }

    default T first() {
        Iterator<T> iterator = iterator();
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }

    default int count() {
        int count = 0;
        Iterator<T> iterator = iterator();
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        return count;
    }

    default boolean isEmpty() {
        return !iterator().hasNext();
    }

    default boolean isNotEmpty() {
        return iterator().hasNext();
    }

    default boolean contains(T node) {
        for (T next : this) {
            if (next == node) {
                return true;
            }
        }
        return false;
    }
}
