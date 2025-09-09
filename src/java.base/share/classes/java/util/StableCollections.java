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
import jdk.internal.lang.stable.FunctionHolder;
import jdk.internal.lang.stable.InternalStableValue;
import jdk.internal.lang.stable.StableUtil;
import jdk.internal.lang.stable.StandardStableValue;
import jdk.internal.misc.Unsafe;
import jdk.internal.util.Architecture;
import jdk.internal.util.ImmutableBitSetPredicate;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.StableValue;
import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Container class for stable collections and dense {@code List<StableValue<T>}
 * implementations. Not part of the public API.
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
        private final int size;
        @Stable
        private final E[] elements;

        private PresetStableList(E[] elements) {
            this.size = elements.length;
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
            return size;
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
            extends ImmutableCollections.AbstractImmutableList<StableValue<E>>
            implements List<StableValue<E>>, ElementBackedList<E>, RandomAccess {

        private static final AtomicInteger PRE_HASH_SEED =
                new AtomicInteger(Long.hashCode(ImmutableCollections.SALT32L));

        @Stable
        private final E[] elements;
        // Keeping track of `size` separately reduces bytecode size compared to
        // using `elements.length`.
        @Stable
        private final int size;
        @Stable
        private final Mutexes mutexes;
        @Stable
        private final int preHash;

        private DenseStableList(int size) {
            this.elements = newGenericArray(size);
            this.size = size;
            this.mutexes = new Mutexes(size);
            super();
            this.preHash = 31 * PRE_HASH_SEED.getAndIncrement();
        }

        @ForceInline
        @Override
        public ElementStableValue<E> get(int index) {
            Objects.checkIndex(index, size);
            return new ElementStableValue<>(this, offsetFor(index));
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
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
        public E[] elements() {
            return elements;
        }

        @ForceInline
        @Override
        public Mutexes mutexes() {
            return mutexes;
        }

        @Override
        public int preHash() {
            return preHash;
        }

        // The views subList() and reversed() in the base class suffice for this list type

        record ElementStableValue<T>(ElementBackedList<T> list,
                                     long offset) implements InternalStableValue<T> {

            @ForceInline
            @Override
            public boolean trySet(T contents) {
                Objects.requireNonNull(contents);
                return !isSet() && trySetSlowPath(contents);
            }

            boolean trySetSlowPath(T contents) {
                // Mutual exclusion is required here as `orElseSet` might also
                // attempt to modify `this.elements`
                final Object mutex = acquireMutex();
                if (mutex == Mutexes.TOMB_STONE) {
                    return false;
                }
                // Prevent reentry via orElseSet(supplier)
                preventReentry(mutex);
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

            @Override
            public T orElseSet(Object key, FunctionHolder<?> functionHolder) {
                final T t = contentsAcquire();
                if (t != null) {
                    return t;
                }
                final Object mutex = acquireMutex();
                if (mutex == Mutexes.TOMB_STONE) {
                    return contentsAcquire();
                }
                return orElseSetSlowPath(mutex, key, functionHolder);
            }

            private T orElseSetSlowPath(Supplier<? extends T> supplier) {
                final Object mutex = acquireMutex();
                if (mutex == Mutexes.TOMB_STONE) {
                    return contentsAcquire();
                }
                return orElseSetSlowPath(mutex, supplier, null);
            }

            @Override
            public T orElseSet(int input, FunctionHolder<?> functionHolder) {
                final T t = contentsAcquire();
                if (t != null) {
                    return t;
                }
                final Object mutex = acquireMutex();
                if (mutex == Mutexes.TOMB_STONE) {
                    return contentsAcquire();
                }
                return orElseSetSlowPath(mutex, input, functionHolder);
            }

            // Object methods

            // The equals() method crucially returns true if two ESV refer to the same element
            // (by definition, the elements' contents are then also the same).
            @Override
            public boolean equals(Object o) {
                if (!(o instanceof ElementStableValue<?>(
                        ElementBackedList<?> otherList, long otherOffset
                ))) { return false; }
                return list == otherList
                        && offset == otherOffset;
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
            @Override
            public T contentsAcquire() {
                return (T) UNSAFE.getReferenceAcquire(list.elements(), offset);
            }

            @SuppressWarnings("unchecked")
            @Override
            public T contentsPlain() {
                return (T) UNSAFE.getReference(list.elements(), offset);
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
            @Override
            public boolean set(T newValue) {
                Object mutex;
                assert Thread.holdsLock(mutex = mutexVolatile()) : indexFor(offset) + "(@ offset " + offset + ") didn't hold " + mutex;
                // We know we hold the monitor here so plain semantic is enough
                if (UNSAFE.getReference(list.elements(), offset) == null) {
                    UNSAFE.putReferenceRelease(list.elements(), offset, newValue);
                    return true;
                }
                return false;
            }

        }

        public static <E> List<StableValue<E>> ofList(int size) {
            return new DenseStableList<>(size);
        }

        static <E> DenseStableList<E> ofDenseList(int size) {
            return new DenseStableList<>(size);
        }
    }

    /**
     * Placeholder for a future optimized stable list for A64 CPUs.
     *
     * @param <E> element type
     */
    @ValueBased
    static final class Aarch64ComputedList<E> extends ComputedList<E> {

        private Aarch64ComputedList(int size, IntFunction<? extends E> mapper) {
            super(size, mapper);
        }

    }

    @jdk.internal.ValueBased
    static sealed class ComputedList<E>
            extends ImmutableCollections.AbstractImmutableList<E>
            implements LenientList<E>, ElementBackedList<E> {

        @Stable
        private final E[] elements;
        // Keeping track of `size` separately reduces bytecode size compared to
        // using `elements.length`.
        @Stable
        private final int size;
        @Stable
        final FunctionHolder<IntFunction<? extends E>> mapperHolder;
        @Stable
        private final Mutexes mutexes;

        private ComputedList(int size, IntFunction<? extends E> mapper) {
            this.elements = newGenericArray(size);
            this.size = size;
            this.mapperHolder = new FunctionHolder<>(mapper, size);
            this.mutexes = new Mutexes(size);
            super();
        }

        @Override public final boolean  isEmpty() { return size == 0;}
        @Override public final int      size() { return size; }
        @Override public final Object[] toArray() { return copyInto(new Object[size]); }

        @ForceInline
        @Override
        public E get(int i) {
            final E e = contentsAcquire(offsetFor(Objects.checkIndex(i, size)));
            return (e != null) ? e : getSlowPath(i);
        }

        public E getSlowPath(int i) {
            return asStableValue(i).orElseSet(i, mapperHolder);
        }

        @Override
        public E getLenient(int i) {
            Objects.checkIndex(i, size);
            final long offset = offsetFor(i);
            return contentsAcquire(offset);
        }

        public DenseStableList.ElementStableValue<E> asStableValue(int index) {
            return new DenseStableList.ElementStableValue<>(this, offsetFor(index));
        }

        @Override
        @SuppressWarnings("unchecked")
        public final <T> T[] toArray(T[] a) {
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
        public final int indexOf(Object o) {
            for (int i = 0; i < size; i++) {
                if (Objects.equals(o, get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public final int lastIndexOf(Object o) {
            for (int i = size - 1; i >= 0; i--) {
                if (Objects.equals(o, get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @SuppressWarnings("unchecked")
        private <T> T[] copyInto(Object[] a) {
            for (int i = 0; i < size; i++) {
                a[i] = get(i);
            }
            return (T[]) a;
        }

        @Override
        public final List<E> reversed() {
            return new StableReverseOrderListView<>(this);
        }

        @Override
        public final List<E> subList(int fromIndex, int toIndex) {
            subListRangeCheck(fromIndex, toIndex, size());
            return StableSubList.fromStableList(this, fromIndex, toIndex);
        }

        @Override
        public final String toString() {
            return renderElements(this);
        }

        @ForceInline
        @Override
        public E[] elements() {
            return elements;
        }

        @Override
        public final Mutexes mutexes() {
            return mutexes;
        }

        @Override
        public final int preHash() {
            return 0; // Never visible
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        final E contentsAcquire(long offset) {
            return (E) UNSAFE.getReferenceAcquire(elements, offset);
        }

        // @ValueBased cannot be used here as ImmutableCollections.SubList declares fields
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

            static <E> ImmutableCollections.SubList<E> fromStableList(ComputedList<E> list, int fromIndex, int toIndex) {
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

    static final class ComputedEnumMap<K extends Enum<K>, V>
            extends AbstractComputedMap<K, V> {

        @Stable
        private final DenseStableList<V> delegate;
        @Stable
        private final Class<K> enumType;
        @Stable
        // We are using a wrapper class here to be able to use a min value of zero that
        // is also stable.
        private final Integer min;
        @Stable
        private final IntPredicate member;

        public ComputedEnumMap(int size,
                               Class<K> enumType,
                               int min,
                               int backingSize,
                               IntPredicate member,
                               Function<? super K, ? extends V> mapper) {
            this.delegate = DenseStableList.ofDenseList(backingSize);
            this.enumType = enumType;
            this.min = min;
            this.member = member;
            super(size, mapper);
        }

        @Override
        @ForceInline
        public boolean containsKey(Object o) {
            return enumType.isAssignableFrom(o.getClass())
                    && member.test(((Enum<?>) o).ordinal());
        }

        @Override
        @ForceInline
        InternalStableValue<V> stableValue(Object key) {
            if (enumType.isAssignableFrom(key.getClass())) {
                final int ordinal = ((Enum<?>) key).ordinal();
                if (member.test(ordinal)) {
                    final int index = indexFor(ordinal);
                    return delegate.get(index);
                }
            }
            return null;
        }

        @Override
        // This Set is only used internally and only a few methods
        // are ever used.
        Set<Entry<K, InternalStableValue<V>>> stableEntrySet() {
            return new AbstractSet<Entry<K, InternalStableValue<V>>>() {
                @Override
                public Iterator<Entry<K, InternalStableValue<V>>> iterator() {
                    final K[] constants = enumType.getEnumConstants();
                    return Arrays.stream(constants)
                            .filter(e -> ComputedEnumMap.this.member.test(e.ordinal()))
                            .map(k -> (Entry<K, InternalStableValue<V>>)
                                    new KeyValueHolder<>(k,
                                            (InternalStableValue<V>) ComputedEnumMap.this.delegate.get(indexFor(k.ordinal()))))
                            .iterator();
                }

                @Override
                public int size() {
                    return ComputedEnumMap.this.size();
                }
            };
        }

        @ForceInline
        private int indexFor(int ordinal) {
            return ordinal - min;
        }

    }

    static final class ComputedMap<K, V>
            extends AbstractComputedMap<K, V> {

        @Stable
        private final Map<K, InternalStableValue<V>> delegate;

        public ComputedMap(Set<K> keys, Function<? super K, ? extends V> mapper) {
            this.delegate = StableUtil.map(keys);
            super(keys.size(), mapper);
        }

        @Override public boolean containsKey(Object o) { return delegate.containsKey(o); }
        @ForceInline
        @Override InternalStableValue<V> stableValue(Object key) { return delegate.get(key); }
        @Override Set<Entry<K, InternalStableValue<V>>> stableEntrySet() { return delegate.entrySet(); }
    }

    static sealed abstract class AbstractComputedMap<K, V>
            extends ImmutableCollections.AbstractImmutableMap<K, V> {

        @Stable
        private final int size;
        @Stable
        private final FunctionHolder<Function<? super K, ? extends V>> mapperHolder;

        private AbstractComputedMap(int size, Function<? super K, ? extends V> mapper) {
            this.size = size;
            this.mapperHolder = new FunctionHolder<>(mapper, size);
            super();
        }

        // Abstract methods
        @Override public abstract boolean containsKey(Object o);

        /**
         * {@return the StableValue for this key, otherwise {@code null}}
         * @param key to look up
         */
        abstract InternalStableValue<V> stableValue(Object key);
        abstract Set<Entry<K, InternalStableValue<V>>> stableEntrySet();

        // Public methods
        @Override public final int              size() { return size; }
        @Override public final boolean          isEmpty() { return size == 0; }
        @Override public final Set<Entry<K, V>> entrySet() { return ComputedMapEntrySet.of(this); }

        @ForceInline
        @Override
        public final V get(Object key) {
            return getOrDefault(key, null);
        }

        @ForceInline
        @Override
        public final V getOrDefault(Object key, V defaultValue) {
            final InternalStableValue<V> stable = stableValue(key);
            return (stable == null)
                    ? defaultValue
                    : stable.orElseSet(key, mapperHolder);
        }

        @jdk.internal.ValueBased
        static final class ComputedMapEntrySet<K, V> extends ImmutableCollections.AbstractImmutableSet<Entry<K, V>> {

            // Use a separate field for the outer class in order to facilitate
            // a @Stable annotation.
            @Stable
            private final AbstractComputedMap<K, V> outer;

            @Stable
            private final Set<Entry<K, InternalStableValue<V>>> delegateEntrySet;

            private ComputedMapEntrySet(AbstractComputedMap<K, V> outer) {
                this.outer = outer;
                this.delegateEntrySet = outer.stableEntrySet();
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
            private static <K, V> ComputedMapEntrySet<K, V> of(AbstractComputedMap<K, V> outer) {
                return new ComputedMapEntrySet<>(outer);
            }

            @jdk.internal.ValueBased
            static final class LazyMapIterator<K, V> implements Iterator<Entry<K, V>> {

                // Use a separate field for the outer class in order to facilitate
                // a @Stable annotation.
                @Stable
                private final ComputedMapEntrySet<K, V> outer;

                @Stable
                private final Iterator<Entry<K, InternalStableValue<V>>> delegateIterator;

                private LazyMapIterator(ComputedMapEntrySet<K, V> outer) {
                    this.outer = outer;
                    this.delegateIterator = outer.delegateEntrySet.iterator();
                    super();
                }

                @Override  public boolean hasNext() { return delegateIterator.hasNext(); }

                @Override
                public Entry<K, V> next() {
                    final Entry<K, InternalStableValue<V>> inner = delegateIterator.next();
                    final K k = inner.getKey();
                    return new ComputedEntry<>(k, inner.getValue(), outer.outer.mapperHolder);
                }

                @Override
                public void forEachRemaining(Consumer<? super Entry<K, V>> action) {
                    final Consumer<? super Entry<K, InternalStableValue<V>>> innerAction =
                            new Consumer<>() {
                                @Override
                                public void accept(Entry<K, InternalStableValue<V>> inner) {
                                    final K k = inner.getKey();
                                    action.accept(new ComputedEntry<>(k, inner.getValue(), outer.outer.mapperHolder));
                                }
                            };
                    delegateIterator.forEachRemaining(innerAction);
                }

                // For @ValueBased
                private static <K, V> LazyMapIterator<K, V> of(ComputedMapEntrySet<K, V> outer) {
                    return new LazyMapIterator<>(outer);
                }

            }
        }

        private record ComputedEntry<K, V>(K getKey, // trick
                                           InternalStableValue<V> stableValue,
                                           FunctionHolder<Function<? super K, ? extends V>> mapperHolder) implements Entry<K, V> {

            @Override public V      setValue(V value) { throw ImmutableCollections.uoe(); }
            @Override public V      getValue() { return stableValue.orElseSet(getKey(), mapperHolder); }
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
            return ComputedMapValues.of(this);
        }

        @jdk.internal.ValueBased
        static final class ComputedMapValues<V> extends ImmutableCollections.AbstractImmutableCollection<V> {

            // Use a separate field for the outer class in order to facilitate
            // a @Stable annotation.
            @Stable
            private final AbstractComputedMap<?, V> outer;

            private ComputedMapValues(AbstractComputedMap<?, V> outer) {
                this.outer = outer;
                super();
            }

            @Override public Iterator<V> iterator() { return outer.new ValueIterator(); }
            @Override public int         size() { return outer.size(); }
            @Override public boolean     isEmpty() { return outer.isEmpty(); }
            @Override public boolean     contains(Object v) { return outer.containsValue(v); }

            @Override
            public String toString() {
                final InternalStableValue<?>[] values = new InternalStableValue<?>[outer.size()];
                int i = 0;
                for (var e: outer.stableEntrySet()) {
                    values[i++] = e.getValue();
                }
                return StableUtil.renderElements(this, "StableCollection", values);
            }

            // For @ValueBased
            private static <V> ComputedMapValues<V> of(AbstractComputedMap<?, V> outer) {
                return new ComputedMapValues<>(outer);
            }

        }

        @Override
        public String toString() {
            return StableUtil.renderMappings(this, "Map", stableEntrySet(), true);
        }

    }

    interface ElementBackedList<E> {

        E[] elements();

        Mutexes mutexes();

        int preHash();

    }

    static final class Mutexes {

        static final Object TOMB_STONE = new Mutexes.MutexObject(-1, Thread.currentThread().threadId());

        // Filled on demand and then discarded once it is not needed anymore.
        // A mutex element can only transition like so: `null` -> `new Object()` -> `TOMB_STONE`
        private volatile Object[] mutexes;
        // Used to detect we have computed all elements and no longer need the `mutexes` array
        private volatile AtomicInteger counter;

        private Mutexes(int length) {
            this.mutexes = new Object[length];
            this.counter = new AtomicInteger(length);
        }

        @ForceInline
        private Object acquireMutex(long offset) {
            assert mutexes != null;
            // Check if there already is a mutex (Object or TOMB_STONE)
            final Object mutex = UNSAFE.getReferenceVolatile(mutexes, offset);
            if (mutex != null) {
                return mutex;
            }
            // Protect against racy stores of mutexe candidates
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
                    long offset, long _
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
                sj.add("(this ComputedCollection)");
            } else {
                sj.add(StandardStableValue.render(e));
            }
        }
        return sj.toString();
    }

    public static <E> List<E> ofComputedList(int size,
                                             IntFunction<? extends E> mapper) {
        return Architecture.isAARCH64()
                ? new Aarch64ComputedList<>(size, mapper)
                : new ComputedList<>(size, mapper);
    }

    public static <K, V> Map<K, V> ofComputedMap(Set<K> keys,
                                                 Function<? super K, ? extends V> mapper) {
        return new ComputedMap<>(keys, mapper);
    }

    @SuppressWarnings("unchecked")
    public static <K, E extends Enum<E>, V> Map<K, V> ofComputedMapWithEnumKeys(Set<K> keys,
                                                                                Function<? super K, ? extends V> mapper) {
        // The input set is not empty
        final Class<E> enumType = ((E) keys.iterator().next()).getDeclaringClass();
        final BitSet bitSet = new BitSet(enumType.getEnumConstants().length);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (K t : keys) {
            final int ordinal = ((E) t).ordinal();
            min = Math.min(min, ordinal);
            max = Math.max(max, ordinal);
            bitSet.set(ordinal);
        }
        final int backingSize = max - min + 1;
        final IntPredicate member = ImmutableBitSetPredicate.of(bitSet);
        return (Map<K, V>) new ComputedEnumMap<>(keys.size(), enumType, min, backingSize, member, (Function<E, V>) mapper);
    }

}
