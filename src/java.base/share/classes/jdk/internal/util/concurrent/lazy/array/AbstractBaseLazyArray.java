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
package jdk.internal.util.concurrent.lazy.array;

import jdk.internal.misc.Unsafe;
import jdk.internal.util.concurrent.lazy.AbstractBaseLazyReference;
import jdk.internal.util.concurrent.lazy.StandardEmptyLazyReference;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.lazy.BaseLazyArray;
import java.util.concurrent.lazy.BaseLazyReference;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReference;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

abstract class AbstractBaseLazyArray<V, L extends AbstractBaseLazyReference<V>>
        implements BaseLazyArray<V> {

    private final LazyReference<ListView> listView = Lazy.of(ListView::new);

    final L[] lazyObjects;

    @SuppressWarnings("unchecked")
    AbstractBaseLazyArray(L[] lazyObjects) {
        this.lazyObjects = lazyObjects;
    }

    @Override
    public final int length() {
        return lazyObjects.length;
    }

    @Override
    public final Lazy.State state(int index) {
        return lazyObjects[index].state();
    }

    @Override
    public final Optional<Throwable> exception(int index) {
        return lazyObjects[index].exception();
    }

    @SuppressWarnings("unchecked")
    @Override
    public V getOr(int index, V defaultValue) {
        return lazyObjects[index].getOr(defaultValue);
    }

    @Override
    public List<V> asList() {
        return listView.get();
    }

    @Override
    public List<V> asList(V defaulValue) {
        return new ListView(defaulValue);
    }

    @Override
    public Stream<Optional<V>> stream() {
        return IntStream.range(0, length())
                .mapToObj(i -> Optional.ofNullable(getOr(i, null)));
    }

    @Override
    public Stream<V> stream(V defaultValue) {
        return IntStream.range(0, length())
                .mapToObj(i -> getOr(i, defaultValue));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + IntStream.range(0, length())
                .mapToObj(i -> switch (state(i)) {
                    case EMPTY -> "-";
                    case CONSTRUCTING -> "+";
                    case PRESENT -> Objects.toString(getOr(i, null));
                    case ERROR -> "!";
                })
                .collect(Collectors.joining(", ")) + "]";
    }

    final class ListView implements List<V> {

        private final V defaultValue;
        private final int begin;
        private final int end;

        ListView(int begin,
                 int end,
                 V defaultValue) {
            if (begin < 0) {
                throw new IndexOutOfBoundsException("begin: " + begin);
            }
            if (end > length()) {
                throw new IndexOutOfBoundsException("end: " + begin);
            }
            this.begin = begin;
            this.end = end;
            this.defaultValue = defaultValue;
        }

        public ListView() {
            this(null);
        }

        ListView(V defaultValue) {
            this(0, length(), defaultValue);
        }

        @Override
        public int size() {
            return end - begin;
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public boolean contains(Object o) {
            for (int i = begin; i < end; i++) {
                if (Objects.equals(0, getOr(i, defaultValue))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Iterator<V> iterator() {
            return new ListIteratorView(0, length(), null);
        }

        @Override
        public Object[] toArray() {
            return IntStream.range(0, size())
                    .mapToObj(i -> getOr(i, defaultValue))
                    .toArray();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T1> T1[] toArray(T1[] a) {
            if (a.length < size()) {
                return (T1[]) Arrays.copyOf(toArray(), size(), a.getClass());
            }
            System.arraycopy(toArray(), 0, a, 0, size());
            if (a.length > size())
                a[size()] = null;
            return a;
        }

        @Override
        public boolean add(V v) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean remove(Object o) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            for (Object e : c)
                if (!contains(e))
                    return false;
            return true;
        }

        @Override
        public boolean addAll(Collection<? extends V> c) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean addAll(int index, Collection<? extends V> c) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            throw newUnsupportedOperation();
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            throw newUnsupportedOperation();
        }

        @Override
        public void clear() {
            throw newUnsupportedOperation();
        }

        @Override
        public V get(int index) {
            return getOr(index, defaultValue);
        }

        @Override
        public V set(int index, V element) {
            throw newUnsupportedOperation();
        }

        @Override
        public void add(int index, V element) {
            throw newUnsupportedOperation();
        }

        @Override
        public V remove(int index) {
            throw newUnsupportedOperation();
        }

        @Override
        public int indexOf(Object o) {
            for (int i = 0; i < size(); i++) {
                if (Objects.equals(o, getOr(i, defaultValue))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(Object o) {
            for (int i = size() - 1; i >= 0; i--) {
                if (Objects.equals(o, getOr(i, defaultValue))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public ListIterator<V> listIterator() {
            return new ListIteratorView(defaultValue);
        }

        @Override
        public ListIterator<V> listIterator(int index) {
            return new ListIteratorView(index, length(), defaultValue);
        }

        @Override
        public List<V> subList(int fromIndex, int toIndex) {
            if (fromIndex < 0) {
                throw new IndexOutOfBoundsException("fromIndex: " + fromIndex);
            }
            if (toIndex > size()) {
                throw new IndexOutOfBoundsException("toIndex: " + toIndex);
            }
            if (fromIndex > toIndex) {
                throw new IndexOutOfBoundsException("fromIndex > toIndex: " + fromIndex + ", " + toIndex);
            }
            return new ListView(begin + fromIndex, begin + toIndex, defaultValue);
        }

        @Override
        public void sort(Comparator<? super V> c) {
            throw newUnsupportedOperation();
        }
    }

    final class ListIteratorView implements ListIterator<V> {

        private final V defaultValue;
        private final int begin;
        private final int end;
        private int cursor;

        private ListIteratorView(V defaultValue) {
            this(0, length(), defaultValue);
        }

        private ListIteratorView(int begin,
                                 int end,
                                 V defaultValue) {
            this.begin = begin;
            this.end = end;
            this.defaultValue = defaultValue;
            this.cursor = begin;
        }

        @Override
        public boolean hasNext() {
            return cursor < end;
        }

        @Override
        public boolean hasPrevious() {
            return cursor != begin;
        }

        @Override
        public V previous() {
            int i = cursor - 1;
            if (i < begin)
                throw new NoSuchElementException();
            cursor = i;
            return getOr(i, defaultValue);
        }

        @Override
        public int nextIndex() {
            return cursor;
        }

        @Override
        public int previousIndex() {
            return cursor - 1;
        }

        @Override
        public void set(V v) {
            throw newUnsupportedOperation();
        }

        @Override
        public void add(V v) {
            throw newUnsupportedOperation();
        }

        @Override
        public V next() {
            var i = cursor + 1;
            if (i >= end) {
                throw new NoSuchElementException();
            }
            cursor = i;
            return getOr(i, defaultValue);
        }

        @Override
        public void remove() {
            throw newUnsupportedOperation();
        }

        @Override
        public void forEachRemaining(Consumer<? super V> action) {
            for (; cursor < end; cursor++) {
                action.accept(getOr(cursor, defaultValue));
            }
        }
    }

    private UnsupportedOperationException newUnsupportedOperation() {
        return new UnsupportedOperationException("Not supported on an unmodifiable list.");
    }

}
