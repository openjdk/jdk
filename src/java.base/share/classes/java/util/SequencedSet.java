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
}
