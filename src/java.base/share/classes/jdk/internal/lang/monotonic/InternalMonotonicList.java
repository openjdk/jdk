/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import static jdk.internal.lang.monotonic.MonotonicUtil.*;

public sealed interface InternalMonotonicList<E>
        extends Monotonic.List<E> {

    final class ReferenceList<E>
            extends AbstractMonotonicList<E>
            implements InternalMonotonicList<E> {

        @Stable
        private final E[] elements;

        @SuppressWarnings("unchecked")
        public ReferenceList(int size) {
            super(size);
            this.elements = (E[]) new Object[size];
        }

        @Override
        public E get(int index) {
            Objects.checkIndex(index, size);
            // Try normal memory semantics first
            E e = elements[index];
            if (e != null) {
                return e;
            }
            // Now, fall back to volatile semantics
            return elementVolatile(index);
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

/*        @SuppressWarnings("unchecked")
        @Override
        public E computeIfAbsent(int index, MethodHandle mapper) {
            return computeIfAbsent0(index, mapper, m -> (E) (Object) m.invokeExact(index));
        }*/

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
                    return e;
                }
                try {
                    E newValue = mapper.apply(supplier);
                    if (newValue == null) {
                        // Do not record a value
                        return null;
                    }
                    e = newValue;
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

/*        @Override
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
        }*/

        // Accessors

        @SuppressWarnings("unchecked")
        private E elementVolatile(int index) {
            return (E) UNSAFE.getReferenceVolatile(elements, MonotonicUtil.objectOffset(index));
        }

        @SuppressWarnings("unchecked")
        private E caeElement(int index, E created) {
            // Make sure no reordering of store operations
            freeze();
            E e = (E) UNSAFE.compareAndExchangeReference(elements, MonotonicUtil.objectOffset(index), null, created);
            return e;
        }

    }

    final class IntList
            extends AbstractPrimitiveMonotonicList<Integer>
            implements InternalMonotonicList<Integer> {

        @Stable
        private final int[] elements;

        public IntList(int size) {
            super(size);
            this.elements = new int[size];
        }

        @Override
        public Integer get(int index) {
            Objects.checkIndex(index, size);
            // Try normal memory semantics first
            int e = elements[index];
            if (e != 0) {
                return e;
            }
            if (isBound(index)) {
                return 0;
            }
            // Now, fall back to volatile semantics
            e = elementVolatile(index);
            if (e != 0) {
                return e;
            }
            if (isBoundVolatile(index)) {
                return 0;
            }
            return null;
        }

        @Override
        public boolean isPresent(int index) {
            return elements[index] != 0 || isBound(index) ||
                    elementVolatile(index) != 0 || isBoundVolatile(index);
        }

        @Override
        public Integer set(int index, Integer element) {
            Objects.requireNonNull(element);
            int witness;
            if ((witness = caeElement(index, element)) != 0 || !casBound(index)) {
                throw valueAlreadyBound(index, get(index));
            }
            return witness;
        }

        @Override
        public Integer putIfAbsent(int index, Integer element) {
            Objects.requireNonNull(element);
            int witness = caeElement(index, element);
            if (witness == 0) {
                casBound(index);
                return element;
            } else {
                return witness;
            }
        }

        @Override
        public Integer computeIfAbsent(int index, IntFunction<? extends Integer> mapper) {
            return computeIfAbsent0(index, mapper, m -> m.apply(index));
        }

/*        @Override
        public Integer computeIfAbsent(int index, MethodHandle mapper) {
            return computeIfAbsent0(index, mapper, m -> (Integer) (Object) m.invokeExact(index));
        }*/

        private <T> Integer computeIfAbsent0(int index,
                                       T supplier,
                                       MonotonicUtil.ThrowingFunction<T, Integer> mapper) {
            Objects.requireNonNull(supplier);
            // Try normal memory semantics first
            int e = elements[index];
            if (e != 0) {
                return e;
            }
            if (isBound(index)) {
                return 0;
            }
            // Now, fall back to volatile semantics
            e = elementVolatile(index);
            if (e != 0) {
                return e;
            }
            if (isBound(index)) {
                return 0;
            }
            int witness;
            // Make sure the supplier is only invoked at most once by this method
            synchronized (supplier) {
                // Re-check
                e = elementVolatile(index);
                if (e != 0) {
                    return e;
                }
                if (isBoundVolatile(index)) {
                    return 0;
                }

                try {
                    Integer newValue = mapper.apply(supplier);
                    if (newValue == null) {
                        // Do not record a value
                        return null;
                    }
                    e = newValue;
                } catch (Throwable t) {
                    if (t instanceof Error error) {
                        throw error;
                    }
                    throw new NoSuchElementException(t);
                }
                witness = caeElement(index, e);
            }
            if (witness == 0) {
                casBound(index);
                return e;
            } else {
                return witness;
            }
        }

/*        @Override
        public MethodHandle getter() {
            final class Holder {
                static final MethodHandle HANDLE;

                static {
                    try {
                        HANDLE = MethodHandles.lookup()
                                .findVirtual(IntList.class, "get", MethodType.methodType(Object.class, int.class))
                                .asType(MethodType.methodType(Object.class, Monotonic.class));
                    } catch (ReflectiveOperationException e) {
                        throw new ExceptionInInitializerError(e);
                    }
                }
            }
            return Holder.HANDLE;
        }*/

        // Accessors

        private int elementVolatile(int index) {
            return UNSAFE.getIntVolatile(elements, MonotonicUtil.intOffset(index));
        }

        private int caeElement(int index, int created) {
            // reordering is not an issue for primitive values so no freeze() is needed here
            return UNSAFE.compareAndExchangeInt(elements, MonotonicUtil.intOffset(index), 0, created);
        }

    }

    abstract class AbstractPrimitiveMonotonicList<E> extends AbstractMonotonicList<E> {

        @Stable
        private final byte[] bound;

        AbstractPrimitiveMonotonicList(int size) {
            super(size);
            this.bound = new byte[size];
        }

        protected final boolean isBound(int index) {
            return bound[index] != 0;
        }

        protected final boolean isBoundVolatile(int index) {
            return UNSAFE.getByteVolatile(bound, MonotonicUtil.byteOffset(index)) != 0;
        }

        protected final boolean casBound(int index) {
            return UNSAFE.compareAndSetByte(bound, MonotonicUtil.byteOffset(index), (byte) 0, (byte) 1);
        }

    }

    abstract class AbstractMonotonicList<E> extends AbstractList<E> {

        @Stable
        protected final int size;

        public AbstractMonotonicList(int size) {
            this.size = size;
        }

        @Override
        public final int size() {
            return size;
        }


        /*        @Override
        public Iterator<E> iterator() {
            return new Itr();
        }

        @Override
        public ListIterator<E> listIterator() {
            return new ListItr(0);
        }*/

        // all mutating methods throw UnsupportedOperationException
        @Override public final boolean add(E v) {throw uoe();}
        @Override public final boolean addAll(int index, Collection<? extends E> c) {throw uoe();}
        @Override public final void clear() {throw uoe();}
        @Override public final boolean remove(Object o) {throw uoe();}
        @Override public final boolean removeAll(Collection<?> c) {throw uoe();}
        @Override public final boolean removeIf(Predicate<? super E> filter) {throw uoe();}
        @Override public final boolean retainAll(Collection<?> c) {throw uoe();}

  /*      private class Itr implements Iterator<E> {
            *//**
             * Index of element to be returned by subsequent call to next.
             *//*
            int cursor = 0;

            *//**
             * Index of element returned by most recent call to next or
             * previous.
             *//*
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
                for (; cursor < size(); cursor++) {
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
                 return i >= 0 && i < size();
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
                    AbstractMonotonicList.this.set(lastRet, e);
                } catch (IndexOutOfBoundsException ex) {
                    throw new ConcurrentModificationException();
                }
            }

            public void add(E e) {
                throw uoe();
            }
        }*/
    }

    static IllegalStateException valueAlreadyBound(int index,
                                                   Object value) {
        return new IllegalStateException("An element value is already bound at index " + index + ": " + value);
    }
}
