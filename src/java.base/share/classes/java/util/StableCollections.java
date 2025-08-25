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

import jdk.internal.ValueBased;
import jdk.internal.invoke.stable.InternalStableValue;
import jdk.internal.invoke.stable.StableUtil;
import jdk.internal.invoke.stable.StandardStableValue;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.invoke.StableValue;
import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Container class for stable collections. Not part of the public API.
 */
final class StableCollections {

    /**
     * No instances.
     */
    private StableCollections() { }

    // Unsafe allows StableValue to be used early in the boot sequence
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    @ValueBased
    static public class PresetStableList<E>
            extends ImmutableCollections.AbstractImmutableList<StableValue<E>>
            implements List<StableValue<E>>, RandomAccess {

        @Stable
        private final E[] elements;

        private PresetStableList(E[] elements) {
            this.elements = elements;
            super();
        }

        @ForceInline
        @Override
        public StableValue<E> get(int index) {
            final E element = elements[index];
            return StandardStableValue.ofPreset(element);
        }

        @Override
        public int size() {
            return elements.length;
        }

        @Override
        public int indexOf(Object o) {
            return StableCollections.indexOf(this, o);
        }

        @Override
        public int lastIndexOf(Object o) {
            return StableCollections.lastIndexOf(this, o);
        }

        @SafeVarargs
        @SuppressWarnings("varargs")
        public static <E> List<StableValue<E>> ofList(E... elements) {
            return new PresetStableList<>(elements);
        }

    }

    @ValueBased
    static final class DenseStableList<E>
            extends AbstractImmutableStableElementList<StableValue<E>>
            implements List<StableValue<E>>, RandomAccess {

        @Stable
        private final E[] elements;
        @Stable
        private final Mutexes mutexes;
        @Stable
        private final int preHash;

        private DenseStableList(int size) {
            this.elements = newGenericArray(size);
            this.mutexes = new Mutexes(size);
            super();
            int h = 1;
            h = 31 * h + System.identityHashCode(this);
            h = 31 * h;
            this.preHash = h;
        }

        @ForceInline
        @Override
        public ElementStableValue<E> get(int index) {
            Objects.checkIndex(index, elements.length);
            return new ElementStableValue<>(elements, this, offsetFor(index));
        }

        @Override
        public int size() {
            return elements.length;
        }

        @Override
        public boolean isEmpty() {
            return elements.length == 0;
        }

        @Override
        public int indexOf(Object o) {
            return StableCollections.indexOf(this, o);
        }

        @Override
        public int lastIndexOf(Object o) {
            return StableCollections.lastIndexOf(this, o);
        }

        @ForceInline
        @Override
        Mutexes mutexes() {
            return mutexes;
        }

        @Override
        int preHash() {
            return preHash;
        }

        // The views subList() and reversed() in the base class suffice for this list type

        public record ElementStableValue<T>(@Stable T[] elements, // fast track this one
                                            AbstractImmutableStableElementList<?> list,
                                            long offset) implements InternalStableValue<T> {

            @ForceInline
            @Override
            public boolean trySet(T contents) {
                Objects.requireNonNull(contents);
                return !isSet() && trySetSlowPath(contents);
            }

            boolean trySetSlowPath(T contents) {
                // Prevent reentry via orElseSet(supplier)
                preventReentry();
                // Mutual exclusion is required here as `orElseSet` might also
                // attempt to modify `this.elements`
                final Object mutex = acquireMutex();
                if (mutex == Mutexes.TOMB_STONE) {
                    return false;
                }
                synchronized (mutex) {
                    // Maybe we were not the winner?
                    if (acquireMutex() == Mutexes.TOMB_STONE) {
                        return false;
                    }
                    final boolean outcome = set(contents);
                    disposeOfMutex();
                    return outcome;
                }
            }

            @ForceInline
            @Override
            public T orElse(T other) {
                final T t = contentsAcquire();
                return t == null ? other : t;
            }

            @ForceInline
            @Override
            public T get() {
                final T t = contentsAcquire();
                if (t == null) {
                    throw new NoSuchElementException("No contents set");
                }
                return t;
            }

            @ForceInline
            @Override
            public boolean isSet() {
                return contentsAcquire() != null;
            }

            @ForceInline
            @Override
            public T orElseSet(Supplier<? extends T> supplier) {
                Objects.requireNonNull(supplier);
                final T t = contentsAcquire();
                return (t == null) ? orElseSetSlowPath(supplier) : t;
            }

            private T orElseSetSlowPath(Supplier<? extends T> supplier) {
                preventReentry();
                final Object mutex = acquireMutex();
                if (mutex == Mutexes.TOMB_STONE) {
                    return contentsAcquire();
                }
                synchronized (mutex) {
                    // If there was another winner that succeeded,
                    // the contents is guaranteed to be set
                    final T t = contentsPlain();  // Plain semantics suffice here
                    if (t == null) {
                        final T newValue = supplier.get();
                        Objects.requireNonNull(newValue);
                        // The mutex is not reentrant so we know newValue should be returned
                        set(newValue);
                        return newValue;
                    }
                    return t;
                }
            }

            // Object methods

            // The equals() method crucially returns true if two ESV refer to the same element
            // (by definition, the elements' contents are then also the same).
            @Override
            public boolean equals(Object o) {
                if (!(o instanceof ElementStableValue<?> that)) return false;
                return list == that.list
                        && offset == that.offset;
            }

            // Similar arguments are true for hashCode() where it must depend on the referring
            // element.
            @Override
            public int hashCode() {
                return list.preHash() + Long.hashCode(offset);
            }

            @Override
            public String toString() {
                final T t = contentsAcquire();
                return t == this
                        ? "(this StableValue)"
                        : StandardStableValue.render(t);
            }

            @SuppressWarnings("unchecked")
            @ForceInline
            private T contentsAcquire() {
                return (T) UNSAFE.getReferenceAcquire(elements, offset);
            }

            @SuppressWarnings("unchecked")
            private T contentsPlain() {
                return (T) UNSAFE.getReference(elements, offset);
            }

            @ForceInline
            private Object acquireMutex() {
                return list.mutexes().acquireMutex(offset);
            }

            @ForceInline
            private void disposeOfMutex() {
                list.mutexes().disposeOfMutex(offset);
            }

            @ForceInline
            private Object mutexVolatile() {
                return list.mutexes().mutexVolatile(offset);
            }

            private void preventReentry() {
                final Object mutex = mutexVolatile();
                if (mutex == null || mutex == Mutexes.TOMB_STONE || !Thread.holdsLock(mutex)) {
                    // No reentry attempted
                    return;
                }
                throw new IllegalStateException("Recursive initialization of a stable value is illegal. Index: " + indexFor(offset));
            }

            /**
             * Tries to set the contents at the provided {@code index} to
             * {@code newValue}.
             * <p>
             * This method ensures the {@link Stable} element is written to at most once.
             *
             * @param newValue to set
             * @return if the contents was set
             */
            @ForceInline
            private boolean set(T newValue) {
                Object mutex;
                assert Thread.holdsLock(mutex = mutexVolatile()) : indexFor(offset) + "(@ offset " + offset + ") didn't hold " + mutex;
                // We know we hold the monitor here so plain semantic is enough
                if (UNSAFE.getReference(elements, offset) == null) {
                    UNSAFE.putReferenceRelease(elements, offset, newValue);
                    return true;
                }
                return false;
            }

        }

        public static <E> List<StableValue<E>> ofList(int size) {
            return new DenseStableList<>(size);
        }
    }

    @jdk.internal.ValueBased
    static final class StableList<E>
            extends AbstractImmutableStableElementList<E>
            implements LenientList<E> {

        @Stable
        private final IntFunction<? extends E> mapper;
        @Stable
        private final E[] elements;
        @Stable
        private final Mutexes mutexes;

        private StableList(int size, IntFunction<? extends E> mapper) {
            this.mapper = mapper;
            this.elements = newGenericArray(size);
            this.mutexes = new Mutexes(size);
            super();
        }

        @Override public boolean  isEmpty() { return elements.length == 0;}
        @Override public int      size() { return elements.length; }
        @Override public Object[] toArray() { return copyInto(new Object[size()]); }

        @ForceInline
        @Override
        public E get(int i) {
            Objects.checkIndex(i, elements.length);
            final long offset = offsetFor(i);
            final E e = contentsAcquire(offset);
            if (e != null) {
                return e;
            }
            return asStableValue(i).orElseSet(new Supplier<E>() {
                @Override  public E get() { return mapper.apply(i); }});
        }

        @Override
        public E getLenient(int i) {
            Objects.checkIndex(i, elements.length);
            final long offset = offsetFor(i);
            return contentsAcquire(offset);
        }

        public DenseStableList.ElementStableValue<E> asStableValue(int index) {
            return new DenseStableList.ElementStableValue<>(elements, this, offsetFor(index));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            final int size = elements.length;
            if (a.length < size) {
                // Make a new array of a's runtime type, but my contents:
                T[] n = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
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
            final int len = elements.length;
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
            return renderElements(this);
        }

        @Override
        Mutexes mutexes() {
            return mutexes;
        }

        @Override
        int preHash() {
            return 0; // Never visible
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        private E contentsAcquire(long offset) {
            return (E) UNSAFE.getReferenceAcquire(elements, offset);
        }

        public static <E> List<E> of(int size, IntFunction<? extends E> mapper) {
            return new StableList<>(size, mapper);
        }

        static final class StableSubList<E> extends ImmutableCollections.SubList<E>
                implements LenientList<E> {

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
                return renderElements(this);
            }

            @Override
            boolean allowNulls() {
                return false;
            }

            @SuppressWarnings("unchecked")
            @Override
            public E getLenient(int index) {
                Objects.checkIndex(index, size);
                return ((LenientList<E>) root).getLenient(offset + index);
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
                implements LenientList<E> {

            private StableReverseOrderListView(List<E> base) {
                super(base, false);
            }

            // This method does not evaluate the elements
            @Override
            public String toString() {
                return renderElements(this);
            }

            @Override
            public List<E> subList(int fromIndex, int toIndex) {
                final int size = base.size();
                subListRangeCheck(fromIndex, toIndex, size);
                return new StableReverseOrderListView<>(base.subList(size - toIndex, size - fromIndex));
            }

            @Override
            public E getLenient(int i) {
                final int size = base.size();
                Objects.checkIndex(i, size);
                return ((LenientList<E>) base).getLenient(size - i - 1);
            }
        }

    }

    interface LenientList<E> extends List<E> {
        /**
         * {@return the element at index {@code i} without evaluating it}
         * @param i index
         */
        E getLenient(int i);
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

        @Override public boolean          containsKey(Object o) { return delegate.containsKey(o); }
        @Override public int              size() { return delegate.size(); }
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
            @SuppressWarnings("unchecked") final K k = (K) key;
            return stable.orElseSet(new Supplier<V>() {
                @Override
                public V get() {
                    return mapper.apply(k);
                }
            });
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
            @Override public int                   size() { return delegateEntrySet.size(); }
            @Override public int                   hashCode() { return outer.hashCode(); }

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

                @Override  public boolean hasNext() { return delegateIterator.hasNext(); }

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
                private static <K, V> LazyMapIterator<K, V> of(StableMapEntrySet<K, V> outer) {
                    return new LazyMapIterator<>(outer);
                }

            }
        }

        private record StableEntry<K, V>(K getKey, // trick
                                         StandardStableValue<V> stableValue,
                                         Supplier<? extends V> supplier) implements Entry<K, V> {

            @Override public V      setValue(V value) { throw ImmutableCollections.uoe(); }
            @Override public V      getValue() { return stableValue.orElseSet(supplier); }
            @Override public int    hashCode() { return hash(getKey()) ^ hash(getValue()); }
            @Override public String toString() { return getKey() + "=" + stableValue.toString(); }

            @Override
            public boolean equals(Object o) {
                return o instanceof Map.Entry<?, ?> e
                        && Objects.equals(getKey(), e.getKey())
                        // Invoke `getValue()` as late as possible to avoid evaluation
                        && Objects.equals(getValue(), e.getValue());
            }

            private int hash(Object obj) {
                return (obj == null) ? 0 : obj.hashCode();
            }
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
            @Override public int         size() { return outer.size(); }
            @Override public boolean     isEmpty() { return outer.isEmpty(); }
            @Override public boolean     contains(Object v) { return outer.containsValue(v); }

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

    abstract static class AbstractImmutableStableElementList<E>
            extends ImmutableCollections.AbstractImmutableList<E> {

        abstract Mutexes mutexes();

        abstract int preHash();

    }

    private static final class Mutexes {

        static final Object TOMB_STONE = new Mutexes.MutexObject(-1, Thread.currentThread().threadId());

        // Inflated on demand
        private volatile Object[] mutexes;
        // Used to detect we have computed all elements and no longer need the `mutexes` array
        private volatile AtomicInteger counter;

        private Mutexes(int length) {
            this.mutexes = new Object[length];
            this.counter = new AtomicInteger(length);
        }

        @ForceInline
        private Object acquireMutex(long offset) {
            final Object candidate = new Mutexes.MutexObject(offset, Thread.currentThread().threadId());
            final Object witness = UNSAFE.compareAndExchangeReference(mutexes, offset, null, candidate);
            check(witness, offset);
            return witness == null ? candidate : witness;
        }

        @ForceInline
        private void disposeOfMutex(long offset) {
            UNSAFE.putReferenceVolatile(mutexes, offset, TOMB_STONE);
            // Todo: the null check is redundant as this method is invoked at most
            //       `size()` times.
            if (counter != null && counter.decrementAndGet() == 0) {
                // We don't need these anymore
                counter = null;
                mutexes = null;
            }
        }

        @ForceInline
        private Object mutexVolatile(long offset) {
            // Can be plain semantics?
            return check(UNSAFE.getReferenceVolatile(mutexes, offset), offset);
        }

        // Todo: remove this after stabilization
        private Object check(Object mutex, long realOffset) {
            if (mutex == null || mutex == TOMB_STONE) {
                return mutex;
            }
            assert (mutex instanceof Mutexes.MutexObject(
                    long offset, long tid
            )) && offset == realOffset :
                    mutex +
                            ", realOffset = " + realOffset +
                            ", realThread = " + Thread.currentThread().threadId();
            return mutex;
        }

        // Todo: remove this after stabilization
        record MutexObject(long offset, long tid) { }

    }

    public static <E> int indexOf(List<StableValue<E>> list, Object o) {
        Objects.requireNonNull(o);
        if (o instanceof StableValue<?> s) {
            final int size = list.size();
            for (int i = 0; i < size; i++) {
                if (Objects.equals(s, list.get(i))) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static <E> int lastIndexOf(List<StableValue<E>> list, Object o) {
        Objects.requireNonNull(o);
        if (o instanceof StableValue<?> s) {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (Objects.equals(s, list.get(i))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static long indexFor(long offset) {
        return (offset - Unsafe.ARRAY_OBJECT_BASE_OFFSET) / Unsafe.ARRAY_OBJECT_INDEX_SCALE;
    }

    @ForceInline
    private static long offsetFor(long index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index;
    }

    @SuppressWarnings("unchecked")
    private static <E> E[] newGenericArray(int length) {
        return (E[]) new Object[length];
    }

    public static String renderElements(LenientList<?> self) {
        final StringJoiner sj = new StringJoiner(", ", "[", "]");
        for (int i = 0; i < self.size(); i++) {
            final Object e = self.getLenient(i);
            if (e == self) {
                sj.add("(this StableCollection)");
            } else {
                sj.add(StandardStableValue.render(e));
            }
        }
        return sj.toString();
    }

}
