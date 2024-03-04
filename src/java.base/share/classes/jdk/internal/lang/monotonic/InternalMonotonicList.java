/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.lang.monotonic;

import jdk.internal.vm.annotation.Stable;

import java.util.AbstractList;
import java.util.Collection;
import java.util.function.Predicate;

import static jdk.internal.lang.monotonic.InternalMonotonic.UNSAFE;
import static jdk.internal.lang.monotonic.InternalMonotonic.freeze;
import static jdk.internal.misc.Unsafe.ARRAY_OBJECT_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_OBJECT_INDEX_SCALE;

public sealed interface InternalMonotonicList<V>
        extends Monotonic.MonotonicList<V> {

    final class MonotonicListImpl<V>
            extends AbstractList<Monotonic<V>>
            implements InternalMonotonicList<V> {

        @Stable
        private final Class<V> backingElementType;

        @Stable
        private final Monotonic<V>[] elements;

        @SuppressWarnings({"unchecked", "rawtypes"})
        public MonotonicListImpl(Class<V> backingElementType,
                                 int size) {
            this.backingElementType = backingElementType;
            this.elements = (Monotonic<V>[]) new Monotonic[size];
        }

        @Override
        public int size() {
            return elements.length;
        }

        @Override
        public Monotonic<V> get(int index) {
            // Try normal memory semantics first
            Monotonic<V> e = elements[index];
            if (e != null) {
                return e;
            }
            return slowPath(index);
        }

        private Monotonic<V> slowPath(int index) {
            // Another thread might have created the element
            Monotonic<V> e = elementVolatile(index);
            if (e != null) {
                return e;
            }
            Monotonic<V> newElement = Monotonic.of(backingElementType);
            return caeElement(index, newElement);
        }

        // all mutating methods throw UnsupportedOperationException
        @Override public boolean add(Monotonic<V> vMonotonic) { throw uoe(); }
        @Override public boolean addAll(int index, Collection<? extends Monotonic<V>> c) { throw uoe(); }
        @Override public void    clear() { throw uoe(); }
        @Override public boolean remove(Object o) { throw uoe(); }
        @Override public boolean removeAll(Collection<?> c) { throw uoe(); }
        @Override public boolean removeIf(Predicate<? super Monotonic<V>> filter) { throw uoe(); }
        @Override public boolean retainAll(Collection<?> c) { throw uoe(); }

        // Todo: Make sure sub-lists, iterators etc. upholds the monotonic invariants
        // Todo: Investigate if we can reuse the ImmutableCollections types...

        // Accessors

        @SuppressWarnings("unchecked")
        private Monotonic<V> elementVolatile(int index) {
            return (Monotonic<V>) UNSAFE.getReferenceVolatile(elements, offset(index));
        }

        private Monotonic<V> caeElement(int index, Monotonic<V> created) {
            // try to store our newly-created Monotonic
            @SuppressWarnings("unchecked")
            var witness = (Monotonic<V>) UNSAFE.compareAndExchangeReference(elements, offset(index), null, created);
            // will use the witness Monotonic someone else created if it exists
            if (witness == null) {
                freeze();
                return created;
            } else {
                return witness;
            }
        }

        private static long offset(int index) {
            return ARRAY_OBJECT_BASE_OFFSET + (long) index * ARRAY_OBJECT_INDEX_SCALE;
        }
    }

    static UnsupportedOperationException uoe() { return new UnsupportedOperationException(); }

}
