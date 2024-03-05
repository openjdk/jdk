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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import static jdk.internal.lang.monotonic.InternalMonotonic.UNSAFE;
import static jdk.internal.lang.monotonic.InternalMonotonic.freeze;
import static jdk.internal.misc.Unsafe.ARRAY_OBJECT_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_OBJECT_INDEX_SCALE;

public sealed interface InternalMonotonicList<V>
        extends Monotonic.List<V> {

    final class ReferenceList<V>
            extends AbstractList<V>
            implements InternalMonotonicList<V> {

        @Stable
        private final V[] elements;

        //@SuppressWarnings({"unchecked", "rawtypes"})
        @SuppressWarnings("unchecked")
        public ReferenceList(Class<V> backingElementType,
                                 int size) {
            this.elements = (V[]) new Object[size];
        }

        @Override
        public int size() {
            return (int) Arrays.stream(elements)
                    .filter(Objects::nonNull)
                    .count();
        }

        @Override
        public V get(int index) {
            // Try normal memory semantics first
            V v = elements[index];
            if (v != null) {
                return v;
            }
            // Now, fall back to volatile semantics
            v = elementVolatile(index);
            if (v != null) {
                return v;
            }
            throw new NoSuchElementException(Integer.toString(index));
        }

        @Override
        public boolean isPresent(int index) {
            return elements[index] != null || elementVolatile(index) != null;
        }

        @Override
        public V set(int index, V element) {
            Objects.requireNonNull(element);
            freeze();
            V previous = caeElement(index, element);
            if (previous == null) {
                return element;
            }
            throw new IllegalStateException("Value already bound at index " + index + ": " + previous);
        }

        @Override
        public V putIfAbsent(int index, V element) {
            Objects.requireNonNull(element);
            freeze();
            V previous = caeElement(index, element);
            return previous == null ? element : previous;
        }

        @Override
        public V computeIfAbsent(int index, IntFunction<? extends V> mapper) {
            return computeIfAbsent0(index, mapper, m -> m.apply(index));
        }

        @SuppressWarnings("unchecked")
        @Override
        public V computeIfAbsent(int index, MethodHandle mapper) {
            return computeIfAbsent0(index, mapper, m -> (V) (Object) m.invokeExact(index));
        }

        private <T> V computeIfAbsent0(int index,
                                       T supplier,
                                       InternalMonotonic.ThrowingFunction<T, V> mapper) {
            Objects.requireNonNull(supplier);
            // Try normal memory semantics first
            V v = elements[index];
            if (v != null) {
                return v;
            }
            // Now, fall back to volatile semantics
            v = elementVolatile(index);
            if (v != null) {
                return v;
            }
            V witness;
            // Make sure the supplier is only invoked at most once by this method
            synchronized (supplier) {
                // Re-check
                v = elementVolatile(index);
                if (v != null) {
                    return get(index);
                }
                try {
                    v = mapper.apply(supplier);
                } catch (Throwable t) {
                    if (t instanceof Error e) {
                        throw e;
                    }
                    throw new NoSuchElementException(t);
                }
                Objects.requireNonNull(v);
                freeze();
                witness = caeElement(index, v);
            }
            return witness == null ? v : witness;
        }

        @Override
        public MethodHandle getter() {
            final class Holder {
                static final MethodHandle HANDLE;

                static {
                    try {
                        HANDLE = MethodHandles.lookup()
                                .findVirtual(ReferenceList.class, "get", MethodType.methodType(Object.class, int.class))
                                .asType(MethodType.methodType(Object.class, Monotonic.class));
                    } catch (ReflectiveOperationException e) {
                        throw new ExceptionInInitializerError(e);
                    }
                }
            }
            return Holder.HANDLE;
        }

        // all mutating methods throw UnsupportedOperationException
        @Override public boolean add(V v) { throw uoe(); }
        @Override public boolean addAll(int index, Collection<? extends V> c) { throw uoe(); }
        @Override public void    clear() { throw uoe(); }
        @Override public boolean remove(Object o) { throw uoe(); }
        @Override public boolean removeAll(Collection<?> c) { throw uoe(); }
        @Override public boolean removeIf(Predicate<? super V> filter) { throw uoe(); }
        @Override public boolean retainAll(Collection<?> c) { throw uoe(); }

        // Todo: Make sure sub-lists, iterators etc. upholds the monotonic invariants
        // Todo: Investigate if we can reuse the ImmutableCollections types...

        // Accessors

        @SuppressWarnings("unchecked")
        private V elementVolatile(int index) {
            return (V) UNSAFE.getReferenceVolatile(elements, offset(index));
        }

        @SuppressWarnings("unchecked")
        private V caeElement(int index, V created) {
            return (V) UNSAFE.compareAndExchangeReference(elements, offset(index), null, created);
        }

        private static long offset(int index) {
            return ARRAY_OBJECT_BASE_OFFSET + (long) index * ARRAY_OBJECT_INDEX_SCALE;
        }
    }

    static UnsupportedOperationException uoe() { return new UnsupportedOperationException(); }

}
