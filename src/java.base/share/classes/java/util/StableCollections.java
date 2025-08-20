/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.lang.stable.StableUtil;
import jdk.internal.lang.stable.StandardStableValue;
import jdk.internal.util.ArraysSupport;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.reflect.Array;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Container class for immutable collections. Not part of the public API.
 * Mainly for namespace management and shared infrastructure.
 *
 * Serial warnings are suppressed throughout because all implementation
 * classes use a serial proxy and thus have no need to declare serialVersionUID.
 */

class StableCollections {
    /** No instances. */
    private StableCollections() { }


    @FunctionalInterface
    interface HasStableDelegates<E> {
        StandardStableValue<E>[] delegates();
    }

    @jdk.internal.ValueBased
    static final class StableList<E>
            extends ImmutableCollections.AbstractImmutableList<E>
            implements HasStableDelegates<E> {

        @Stable
        private final IntFunction<? extends E> mapper;
        @Stable
        final StandardStableValue<E>[] delegates;

        StableList(int size, IntFunction<? extends E> mapper) {
            this.mapper = mapper;
            this.delegates = StableUtil.array(size);
            super();
        }

        @Override public boolean  isEmpty() { return delegates.length == 0;}
        @Override public int      size() { return delegates.length; }
        @Override public Object[] toArray() { return copyInto(new Object[size()]); }

        @ForceInline
        @Override
        public E get(int i) {
            final StandardStableValue<E> delegate;
            try {
                delegate = delegates[i];
            } catch (ArrayIndexOutOfBoundsException aioobe) {
                throw new IndexOutOfBoundsException(i);
            }
            return delegate.orElseSet(new Supplier<E>() {
                        @Override  public E get() { return mapper.apply(i); }});
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            final int size = delegates.length;
            if (a.length < size) {
                // Make a new array of a's runtime type, but my contents:
                T[] n = (T[])Array.newInstance(a.getClass().getComponentType(), size);
                return copyInto(n);
            }
            copyInto(a);
            if (a.length > size) {
                a[size] = null; // null-terminate
            }
            return a;
        }

        @Override
        public int indexOf(Object o) {
            final int size = size();
            for (int i = 0; i < size; i++) {
                if (Objects.equals(o, get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(Object o) {
            for (int i = size() - 1; i >= 0; i--) {
                if (Objects.equals(o, get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @SuppressWarnings("unchecked")
        private <T> T[] copyInto(Object[] a) {
            final int len = delegates.length;
            for (int i = 0; i < len; i++) {
                a[i] = get(i);
            }
            return (T[]) a;
        }

        @Override
        public List<E> reversed() {
            return new StableReverseOrderListView<>(this);
        }

        @Override
        public List<E> subList(int fromIndex, int toIndex) {
            subListRangeCheck(fromIndex, toIndex, size());
            return StableSubList.fromStableList(this, fromIndex, toIndex);
        }

        @Override
        public String toString() {
            return StableUtil.renderElements(this, "StableCollection", delegates);
        }

        @Override
        public StandardStableValue<E>[] delegates() {
            return delegates;
        }

        static final class StableSubList<E> extends ImmutableCollections.SubList<E>
                implements HasStableDelegates<E> {

            private StableSubList(ImmutableCollections.AbstractImmutableList<E> root, int offset, int size) {
                super(root, offset, size);
            }

            @Override
            public List<E> reversed() {
                return new StableReverseOrderListView<>(this);
            }

            @Override
            public List<E> subList(int fromIndex, int toIndex) {
                subListRangeCheck(fromIndex, toIndex, size());
                return StableSubList.fromStableSubList(this, fromIndex, toIndex);
            }

            @Override
            public String toString() {
                return StableUtil.renderElements(this, "StableCollection", delegates());
            }

            @Override
            boolean allowNulls() {
                return true;
            }

            @Override
            public StandardStableValue<E>[] delegates() {
                @SuppressWarnings("unchecked")
                final var rootDelegates = ((HasStableDelegates<E>) root).delegates();
                return Arrays.copyOfRange(rootDelegates, offset, offset + size);
            }

            static <E> ImmutableCollections.SubList<E> fromStableList(StableList<E> list, int fromIndex, int toIndex) {
                return new StableSubList<>(list, fromIndex, toIndex - fromIndex);
            }

            static <E> ImmutableCollections.SubList<E> fromStableSubList(StableSubList<E> parent, int fromIndex, int toIndex) {
                return new StableSubList<>(parent.root, parent.offset + fromIndex, toIndex - fromIndex);
            }

        }

        private static final class StableReverseOrderListView<E>
                extends ReverseOrderListView.Rand<E>
                implements HasStableDelegates<E> {

            private StableReverseOrderListView(List<E> base) {
                super(base, false);
            }

            // This method does not evaluate the elements
            @Override
            public String toString() {
                return StableUtil.renderElements(this, "StableCollection", delegates());
            }

            @Override
            public List<E> subList(int fromIndex, int toIndex) {
                final int size = base.size();
                subListRangeCheck(fromIndex, toIndex, size);
                return new StableReverseOrderListView<>(base.subList(size - toIndex, size - fromIndex));
            }

            @Override
            public StandardStableValue<E>[] delegates() {
                @SuppressWarnings("unchecked")
                final var baseDelegates = ((HasStableDelegates<E>) base).delegates();
                return ArraysSupport.reverse(
                        Arrays.copyOf(baseDelegates, baseDelegates.length));
            }
        }

    }

    static final class StableMap<K, V>
            extends ImmutableCollections.AbstractImmutableMap<K, V> {

        @Stable
        private final Function<? super K, ? extends V> mapper;
        @Stable
        private final Map<K, StandardStableValue<V>> delegate;

        StableMap(Set<K> keys, Function<? super K, ? extends V> mapper) {
            this.mapper = mapper;
            this.delegate = StableUtil.map(keys);
            super();
        }

        @Override public boolean              containsKey(Object o) { return delegate.containsKey(o); }
        @Override public int                  size() { return delegate.size(); }
        @Override public Set<Entry<K, V>> entrySet() { return StableMapEntrySet.of(this); }

        @ForceInline
        @Override
        public V get(Object key) {
            return getOrDefault(key, null);
        }

        @ForceInline
        @Override
        public V getOrDefault(Object key, V defaultValue) {
            final StandardStableValue<V> stable = delegate.get(key);
            if (stable == null) {
                return defaultValue;
            }
            @SuppressWarnings("unchecked")
            final K k = (K) key;
            return stable.orElseSet(new Supplier<V>() {
                @Override public V get() { return mapper.apply(k); }});
        }

        @jdk.internal.ValueBased
        static final class StableMapEntrySet<K, V> extends ImmutableCollections.AbstractImmutableSet<Entry<K, V>> {

            // Use a separate field for the outer class in order to facilitate
            // a @Stable annotation.
            @Stable
            private final StableMap<K, V> outer;

            @Stable
            private final Set<Entry<K, StandardStableValue<V>>> delegateEntrySet;

            private StableMapEntrySet(StableMap<K, V> outer) {
                this.outer = outer;
                this.delegateEntrySet = outer.delegate.entrySet();
                super();
            }

            @Override public Iterator<Entry<K, V>> iterator() { return LazyMapIterator.of(this); }
            @Override public int                       size() { return delegateEntrySet.size(); }
            @Override public int                       hashCode() { return outer.hashCode(); }

            @Override
            public String toString() {
                return StableUtil.renderMappings(this, "StableCollection", delegateEntrySet, false);
            }

            // For @ValueBased
            private static <K, V> StableMapEntrySet<K, V> of(StableMap<K, V> outer) {
                return new StableMapEntrySet<>(outer);
            }

            @jdk.internal.ValueBased
            static final class LazyMapIterator<K, V> implements Iterator<Entry<K, V>> {

                // Use a separate field for the outer class in order to facilitate
                // a @Stable annotation.
                @Stable
                private final StableMapEntrySet<K, V> outer;

                @Stable
                private final Iterator<Entry<K, StandardStableValue<V>>> delegateIterator;

                private LazyMapIterator(StableMapEntrySet<K, V> outer) {
                    this.outer = outer;
                    this.delegateIterator = outer.delegateEntrySet.iterator();
                    super();
                }

                @Override public boolean hasNext() { return delegateIterator.hasNext(); }

                @Override
                public Entry<K, V> next() {
                    final Entry<K, StandardStableValue<V>> inner = delegateIterator.next();
                    final K k = inner.getKey();
                    return new StableEntry<>(k, inner.getValue(), new Supplier<V>() {
                        @Override public V get() { return outer.outer.mapper.apply(k); }});
                }

                @Override
                public void forEachRemaining(Consumer<? super Entry<K, V>> action) {
                    final Consumer<? super Entry<K, StandardStableValue<V>>> innerAction =
                            new Consumer<>() {
                                @Override
                                public void accept(Entry<K, StandardStableValue<V>> inner) {
                                    final K k = inner.getKey();
                                    action.accept(new StableEntry<>(k, inner.getValue(), new Supplier<V>() {
                                        @Override public V get() { return outer.outer.mapper.apply(k); }}));
                                }
                            };
                    delegateIterator.forEachRemaining(innerAction);
                }

                // For @ValueBased
                private static  <K, V> LazyMapIterator<K, V> of(StableMapEntrySet<K, V> outer) {
                    return new LazyMapIterator<>(outer);
                }

            }
        }

        private record StableEntry<K, V>(K getKey, // trick
                                         StandardStableValue<V> stableValue,
                                         Supplier<? extends V> supplier) implements Entry<K, V> {

            @Override public V setValue(V value) { throw ImmutableCollections.uoe(); }
            @Override public V getValue() { return stableValue.orElseSet(supplier); }
            @Override public int hashCode() { return hash(getKey()) ^ hash(getValue()); }
            @Override public String toString() { return getKey() + "=" + stableValue.toString(); }
            @Override public boolean equals(Object o) {
                return o instanceof Map.Entry<?, ?> e
                        && Objects.equals(getKey(), e.getKey())
                        // Invoke `getValue()` as late as possible to avoid evaluation
                        && Objects.equals(getValue(), e.getValue());
            }

            private int hash(Object obj) { return (obj == null) ? 0 : obj.hashCode(); }
        }

        @Override
        public Collection<V> values() {
            return StableMapValues.of(this);
        }

        @jdk.internal.ValueBased
        static final class StableMapValues<V> extends ImmutableCollections.AbstractImmutableCollection<V> {

            // Use a separate field for the outer class in order to facilitate
            // a @Stable annotation.
            @Stable
            private final StableMap<?, V> outer;

            private StableMapValues(StableMap<?, V> outer) {
                this.outer = outer;
                super();
            }

            @Override public Iterator<V> iterator() { return outer.new ValueIterator(); }
            @Override public int size() { return outer.size(); }
            @Override public boolean isEmpty() { return outer.isEmpty();}
            @Override public boolean contains(Object v) { return outer.containsValue(v); }

            private static final IntFunction<StandardStableValue<?>[]> GENERATOR = new IntFunction<StandardStableValue<?>[]>() {
                @Override
                public StandardStableValue<?>[] apply(int len) {
                    return new StandardStableValue<?>[len];
                }
            };

            @Override
            public String toString() {
                final StandardStableValue<?>[] values = outer.delegate.values().toArray(GENERATOR);
                return StableUtil.renderElements(this, "StableCollection", values);
            }

            // For @ValueBased
            private static <V> StableMapValues<V> of(StableMap<?, V> outer) {
                return new StableMapValues<>(outer);
            }

        }

        @Override
        public String toString() {
            return StableUtil.renderMappings(this, "StableMap", delegate.entrySet(), true);
        }

    }

}
