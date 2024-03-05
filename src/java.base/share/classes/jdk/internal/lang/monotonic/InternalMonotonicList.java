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
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import static jdk.internal.lang.monotonic.MonotonicUtil.UNSAFE;
import static jdk.internal.lang.monotonic.InternalMonotonic.freeze;
import static jdk.internal.lang.monotonic.MonotonicUtil.uoe;
import static jdk.internal.misc.Unsafe.ARRAY_OBJECT_BASE_OFFSET;
import static jdk.internal.misc.Unsafe.ARRAY_OBJECT_INDEX_SCALE;

public sealed interface InternalMonotonicList<E>
        extends Monotonic.List<E> {

    final class ReferenceList<E>
            extends AbstractList<E>
            implements InternalMonotonicList<E> {

        private static final long SIZE_OFFSET =
                UNSAFE.objectFieldOffset(ReferenceList.class, "size");

        private int size;

        @Stable
        private final E[] elements;

        @SuppressWarnings("unchecked")
        public ReferenceList(int size) {
            this.elements = (E[]) new Object[size];
        }

        @Override
        public int size() {
            return UNSAFE.getIntVolatile(this, SIZE_OFFSET);
        }

        @Override
        public E get(int index) {
            // Try normal memory semantics first
            E e = elements[index];
            if (e != null) {
                return e;
            }
            // Now, fall back to volatile semantics
            e = elementVolatile(index);
            if (e != null) {
                return e;
            }
            return null;
            //throw new NoSuchElementException(Integer.toString(index));
        }

        @Override
        public boolean isPresent(int index) {
            return elements[index] != null || elementVolatile(index) != null;
        }

        @Override
        public E set(int index, E element) {
            Objects.requireNonNull(element);
            E previous = caeElement(index, element);
            if (previous == null) {
                return element;
            }
            throw new IllegalStateException("Value already bound at index " + index + ": " + previous);
        }

        @Override
        public E putIfAbsent(int index, E element) {
            Objects.requireNonNull(element);
            E previous = caeElement(index, element);
            return previous == null ? element : previous;
        }

        @Override
        public E computeIfAbsent(int index, IntFunction<? extends E> mapper) {
            return computeIfAbsent0(index, mapper, m -> m.apply(index));
        }

        @SuppressWarnings("unchecked")
        @Override
        public E computeIfAbsent(int index, MethodHandle mapper) {
            return computeIfAbsent0(index, mapper, m -> (E) (Object) m.invokeExact(index));
        }

        private <T> E computeIfAbsent0(int index,
                                       T supplier,
                                       MonotonicUtil.ThrowingFunction<T, E> mapper) {
            Objects.requireNonNull(supplier);
            // Try normal memory semantics first
            E e = elements[index];
            if (e != null) {
                return e;
            }
            // Now, fall back to volatile semantics
            e = elementVolatile(index);
            if (e != null) {
                return e;
            }
            E witness;
            // Make sure the supplier is only invoked at most once by this method
            synchronized (supplier) {
                // Re-check
                e = elementVolatile(index);
                if (e != null) {
                    return get(index);
                }
                try {
                    e = mapper.apply(supplier);
                } catch (Throwable t) {
                    if (t instanceof Error error) {
                        throw error;
                    }
                    throw new NoSuchElementException(t);
                }
                Objects.requireNonNull(e);
                witness = caeElement(index, e);
            }
            return witness == null ? e : witness;
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

        @Override
        public Iterator<E> iterator() {
            return new Itr();
        }

        @Override
        public ListIterator<E> listIterator() {
            return new ListItr(0);
        }

        // all mutating methods throw UnsupportedOperationException
        @Override
        public boolean add(E v) {
            throw MonotonicUtil.uoe();
        }

        @Override
        public boolean addAll(int index, Collection<? extends E> c) {
            throw MonotonicUtil.uoe();
        }

        @Override
        public void clear() {
            throw MonotonicUtil.uoe();
        }

        @Override
        public boolean remove(Object o) {
            throw MonotonicUtil.uoe();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw MonotonicUtil.uoe();
        }

        @Override
        public boolean removeIf(Predicate<? super E> filter) {
            throw MonotonicUtil.uoe();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw MonotonicUtil.uoe();
        }

        private class Itr implements Iterator<E> {
            /**
             * Index of element to be returned by subsequent call to next.
             */
            int cursor = 0;

            /**
             * Index of element returned by most recent call to next or
             * previous.
             */
            int lastRet = -1;

            E next;

            @Override
            public boolean hasNext() {
                if (next != null) {
                    return true;
                }
                while ((next = get(cursor++)) == null && isInRange(cursor)) {
                }
                return next != null;
            }

            @Override
            public E next() {
                E candidate = next;
                if (candidate != null) {
                    // Mark as consumed
                    next = null;
                    markLastRet();
                    return candidate;
                }
                if (hasNext()) {
                    markLastRet();
                    return next;
                }
                throw new NoSuchElementException();
            }

            @Override
            public void forEachRemaining(Consumer<? super E> action) {
                Objects.requireNonNull(action);
                E candidate = next;
                if (candidate != null) {
                    // Mark as consumed
                    next = null;
                    markLastRet();
                    action.accept(candidate);
                }
                for (; cursor < elements.length; cursor++) {
                    E e = get(cursor);
                    if (e != null) {
                        action.accept(e);
                    }
                }
                markLastRet();
            }

            @Override public void remove() {
                throw MonotonicUtil.uoe();
            }

            boolean isInRange(int i) {
                return i >= 0 && i < elements.length;
            }

            void markLastRet() {
                lastRet = cursor;
            }

        }

        private class ListItr extends Itr implements ListIterator<E> {

            private E previous;

            ListItr(int index) {
                cursor = index;
            }

            public boolean hasPrevious() {
                int i = cursor -1 ;
                while ((previous = get(i)) == null && isInRange(cursor)) {
                }
                return previous != null;
            }

            public E previous() {
                E candidate = previous;
                if (candidate != null) {
                    // Mark as consumed
                    previous = null;
                    markLastRet();
                    return candidate;
                }
                if (hasPrevious()) {
                    markLastRet();
                    return previous;
                }
                throw new NoSuchElementException();
            }

            public int nextIndex() {
                return cursor;
            }

            public int previousIndex() {
                return cursor-1;
            }

            public void set(E e) {
                if (lastRet < 0)
                    throw new IllegalStateException();
                try {
                    ReferenceList.this.set(lastRet, e);
                } catch (IndexOutOfBoundsException ex) {
                    throw new ConcurrentModificationException();
                }
            }

            public void add(E e) {
                throw uoe();
            }
        }

        // Todo: Make sure sub-lists, iterators etc. upholds the monotonic invariants
        // Todo: Investigate if we can reuse the ImmutableCollections types...

        // Accessors

        @SuppressWarnings("unchecked")
        private E elementVolatile(int index) {
            return (E) UNSAFE.getReferenceVolatile(elements, offset(index));
        }

        @SuppressWarnings("unchecked")
        private E caeElement(int index, E created) {
            // Make sure no reordering of store operations
            freeze();
            E e = (E) UNSAFE.compareAndExchangeReference(elements, offset(index), null, created);
            if (e == null) {
                // We have added another element
                UNSAFE.getAndAddInt(this, SIZE_OFFSET, 1);
            }
            return e;
        }

        private static long offset(int index) {
            return ARRAY_OBJECT_BASE_OFFSET + (long) index * ARRAY_OBJECT_INDEX_SCALE;
        }

    }
}
