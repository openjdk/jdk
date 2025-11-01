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

import jdk.internal.misc.Unsafe;
import jdk.internal.util.ImmutableBitSetPredicate;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.LazyConstant;
import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Container class for lazy collections implementations. Not part of the public API.
 */
final class LazyCollections {

    /**
     * No instances.
     */
    private LazyCollections() { }

    // Unsafe allows LazyCollection classes to be used early in the boot sequence
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    @jdk.internal.ValueBased
    static final class LazyList<E>
            extends ImmutableCollections.AbstractImmutableList<E> {

        @Stable
        private final E[] elements;
        // Keeping track of `size` separately reduces bytecode size compared to
        // using `elements.length`.
        @Stable
        private final int size;
        @Stable
        final FunctionHolder<IntFunction<? extends E>> functionHolder;
        @Stable
        private final Mutexes mutexes;

        private LazyList(int size, IntFunction<? extends E> computingFunction) {
            this.elements = newGenericArray(size);
            this.size = size;
            this.functionHolder = new FunctionHolder<>(computingFunction, size);
            this.mutexes = new Mutexes(size);
            super();
        }

        @Override public boolean  isEmpty() { return size == 0; }
        @Override public int      size() { return size; }
        @Override public Object[] toArray() { return copyInto(new Object[size]); }

        @ForceInline
        @Override
        public E get(int i) {
            final E e = contentsAcquire(offsetFor(Objects.checkIndex(i, size)));
            return (e != null) ? e : getSlowPath(i);
        }

        private E getSlowPath(int i) {
            return orElseComputeSlowPath(elements, i, mutexes.acquireMutex(offsetFor(i)), i, functionHolder);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
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
            for (int i = 0; i < size; i++) {
                if (Objects.equals(o, get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(Object o) {
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
        public List<E> reversed() {
            return new ReverseOrderLazyListView<>(this);
        }

        @Override
        public List<E> subList(int fromIndex, int toIndex) {
            subListRangeCheck(fromIndex, toIndex, size());
            return LazySubList.fromLazyList(this, fromIndex, toIndex);
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        private E contentsAcquire(long offset) {
            return (E) UNSAFE.getReferenceAcquire(elements, offset);
        }

        // @ValueBased cannot be used here as ImmutableCollections.SubList declares fields
        static final class LazySubList<E> extends ImmutableCollections.SubList<E> {

            private LazySubList(ImmutableCollections.AbstractImmutableList<E> root, int offset, int size) {
                super(root, offset, size);
            }

            @Override
            public List<E> reversed() {
                return new ReverseOrderLazyListView<>(this);
            }

            @Override
            public List<E> subList(int fromIndex, int toIndex) {
                subListRangeCheck(fromIndex, toIndex, size());
                return LazySubList.fromLazySubList(this, fromIndex, toIndex);
            }

            @Override
            boolean allowNulls() {
                return false;
            }

            static <E> ImmutableCollections.SubList<E> fromLazyList(LazyList<E> list, int fromIndex, int toIndex) {
                return new LazySubList<>(list, fromIndex, toIndex - fromIndex);
            }

            static <E> ImmutableCollections.SubList<E> fromLazySubList(LazySubList<E> parent, int fromIndex, int toIndex) {
                return new LazySubList<>(parent.root, parent.offset + fromIndex, toIndex - fromIndex);
            }

        }

        private static final class ReverseOrderLazyListView<E>
                extends ReverseOrderListView.Rand<E> {

            private ReverseOrderLazyListView(List<E> base) {
                super(base, false);
            }

            @Override
            public List<E> subList(int fromIndex, int toIndex) {
                final int size = base.size();
                subListRangeCheck(fromIndex, toIndex, size);
                return new ReverseOrderLazyListView<>(base.subList(size - toIndex, size - fromIndex));
            }
        }
    }

    static final class LazyEnumMap<K extends Enum<K>, V>
            extends AbstractLazyMap<K, V> {

        @Stable
        private final Class<K> enumType;
        @Stable
        // We are using a wrapper class here to be able to use a min value of zero that
        // is also stable.
        private final Integer min;
        @Stable
        private final IntPredicate member;

        public LazyEnumMap(Set<K> set,
                           Class<K> enumType,
                           int min,
                           int backingSize,
                           IntPredicate member,
                           Function<? super K, ? extends V> computingFunction) {
            this.enumType = enumType;
            this.min = min;
            this.member = member;
            super(set, set.size(), backingSize, computingFunction);
        }

        @Override
        @ForceInline
        public boolean containsKey(Object o) {
            return enumType.isAssignableFrom(o.getClass())
                    && member.test(((Enum<?>) o).ordinal());
        }

        @ForceInline
        @Override
        public V getOrDefault(Object key, V defaultValue) {
            if (enumType.isAssignableFrom(key.getClass())) {
                final int ordinal = ((Enum<?>) key).ordinal();
                if (member.test(ordinal)) {
                    @SuppressWarnings("unchecked")
                    final K k = (K) key;
                    return orElseCompute(k, indexForAsInt(k));
                }
            }
            return defaultValue;
        }

        @Override
        Integer indexFor(K key) {
            return indexForAsInt(key);
        }

        private int indexForAsInt(K key) {
            return key.ordinal() - min;
        }

    }

    static final class LazyMap<K, V>
            extends AbstractLazyMap<K, V> {

        @Stable
        private final Map<K, Integer> indexMapper;

        public LazyMap(Set<K> keys, Function<? super K, ? extends V> computingFunction) {
            @SuppressWarnings("unchecked")
            final Entry<K, Integer>[] entries = (Entry<K, Integer>[]) new Entry<?, ?>[keys.size()];
            int i = 0;
            for (K k : keys) {
                entries[i] = Map.entry(k, i++);
            }
            this.indexMapper = Map.ofEntries(entries);
            super(keys, i, i, computingFunction);
        }

        @ForceInline
        @Override
        public V getOrDefault(Object key, V defaultValue) {
            final Integer index = indexMapper.get(key);
            if (index != null) {
                @SuppressWarnings("unchecked")
                final K k = (K) key;
                return orElseCompute(k, index);
            }
            return defaultValue;
        }

        @Override public boolean containsKey(Object o) { return indexMapper.containsKey(o); }

        @Override
        Integer indexFor(K key) {
            return indexMapper.get(key);
        }
    }

    static sealed abstract class AbstractLazyMap<K, V>
            extends ImmutableCollections.AbstractImmutableMap<K, V> {

        // This field shadows AbstractMap.keySet which is not @Stable.
        @Stable
        Set<K> keySet;
        // This field shadows AbstractMap.values which is of another type
        @Stable
        final V[] values;
        @Stable
        Mutexes mutexes;
        @Stable
        private final int size;
        @Stable
        final FunctionHolder<Function<? super K, ? extends V>> functionHolder;
        @Stable
        private final Set<Entry<K, V>> entrySet;

        private AbstractLazyMap(Set<K> keySet,
                                int size,
                                int backingSize,
                                Function<? super K, ? extends V> computingFunction) {
            this.size = size;
            this.functionHolder = new FunctionHolder<>(computingFunction, size);
            this.values = newGenericArray(backingSize);
            this.mutexes = new Mutexes(backingSize);
            super();
            this.keySet = keySet;
            this.entrySet = LazyMapEntrySet.of(this);
        }

        // Abstract methods
        @Override public abstract boolean containsKey(Object o);
        abstract Integer indexFor(K key);

        // Public methods
        @Override public final int              size() { return size; }
        @Override public final boolean          isEmpty() { return size == 0; }
        @Override public final Set<Entry<K, V>> entrySet() { return entrySet; }
        @Override public Set<K>                 keySet() { return keySet; }

        @ForceInline
        @Override
        public final V get(Object key) {
            return getOrDefault(key, null);
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        final V orElseCompute(K key, int index) {
            final long offset = offsetFor(index);
            final V v = (V) UNSAFE.getReferenceAcquire(values, offset);
            if (v != null) {
                return v;
            }
            final Object mutex = mutexes.acquireMutex(offset);
            return orElseComputeSlowPath(values, index, mutex, key, functionHolder);
        }

        @SuppressWarnings("unchecked")
        final V getAcquire(K key) {
            return (V) UNSAFE.getReferenceAcquire(values, offsetFor(indexFor(key)));
        }

        @jdk.internal.ValueBased
        static final class LazyMapEntrySet<K, V> extends ImmutableCollections.AbstractImmutableSet<Entry<K, V>> {

            // Use a separate field for the outer class in order to facilitate
            // a @Stable annotation.
            @Stable
            private final AbstractLazyMap<K, V> map;

            private LazyMapEntrySet(AbstractLazyMap<K, V> map) {
                this.map = map;
                super();
            }

            @Override public Iterator<Entry<K, V>> iterator() { return LazyMapIterator.of(map); }
            @Override public int                   size() { return map.size(); }
            @Override public int                   hashCode() { return map.hashCode(); }

            // For @ValueBased
            private static <K, V> LazyMapEntrySet<K, V> of(AbstractLazyMap<K, V> outer) {
                return new LazyMapEntrySet<>(outer);
            }

            @jdk.internal.ValueBased
            static final class LazyMapIterator<K, V> implements Iterator<Entry<K, V>> {

                // Use a separate field for the outer class in order to facilitate
                // a @Stable annotation.
                @Stable
                private final AbstractLazyMap<K, V> map;
                @Stable
                private final Iterator<K> keyIterator;

                private LazyMapIterator(AbstractLazyMap<K, V> map) {
                    this.map = map;
                    this.keyIterator = map.keySet.iterator();
                    super();
                }

                @Override  public boolean hasNext() { return keyIterator.hasNext(); }

                @Override
                public Entry<K, V> next() {
                    final K k = keyIterator.next();
                    return new LazyEntry<>(k, map, map.functionHolder);
                }

                @Override
                public void forEachRemaining(Consumer<? super Entry<K, V>> action) {
                    final Consumer<? super K> innerAction =
                            new Consumer<>() {
                                @Override
                                public void accept(K key) {
                                    action.accept(new LazyEntry<>(key, map, map.functionHolder));
                                }
                            };
                    keyIterator.forEachRemaining(innerAction);
                }

                // For @ValueBased
                private static <K, V> LazyMapIterator<K, V> of(AbstractLazyMap<K, V> map) {
                    return new LazyMapIterator<>(map);
                }

            }
        }

        private record LazyEntry<K, V>(K getKey, // trick
                                       AbstractLazyMap<K, V> map,
                                       FunctionHolder<Function<? super K, ? extends V>> functionHolder) implements Entry<K, V> {

            @Override public V      setValue(V value) { throw ImmutableCollections.uoe(); }
            @Override public V      getValue() {
                final int index = map.indexFor(getKey);
                final V v = map.getAcquire(getKey);
                return v != null
                        ? v
                        : orElseComputeSlowPath(map.values, index, map.mutexes.acquireMutex(offsetFor(index)), getKey, functionHolder);
            }
            @Override public int    hashCode() { return hash(getKey()) ^ hash(getValue()); }
            @Override public String toString() { return getKey() + "=" + getValue(); }

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
            return LazyMapValues.of(this);
        }

        @jdk.internal.ValueBased
        static final class LazyMapValues<K, V> extends ImmutableCollections.AbstractImmutableCollection<V> {

            // Use a separate field for the outer class in order to facilitate
            // a @Stable annotation.
            @Stable
            private final AbstractLazyMap<K, V> map;

            private LazyMapValues(AbstractLazyMap<K, V> map) {
                this.map = map;
                super();
            }

            @Override public Iterator<V> iterator() { return map.new ValueIterator(); }
            @Override public int         size() { return map.size(); }
            @Override public boolean     isEmpty() { return map.isEmpty(); }
            @Override public boolean     contains(Object v) { return map.containsValue(v); }

            // For @ValueBased
            private static <K, V> LazyMapValues<K, V> of(AbstractLazyMap<K, V> outer) {
                return new LazyMapValues<>(outer);
            }

        }

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

    public static <E> int indexOf(List<LazyConstant<E>> list, Object o) {
        Objects.requireNonNull(o);
        if (o instanceof LazyConstant<?> s) {
            final int size = list.size();
            for (int i = 0; i < size; i++) {
                if (Objects.equals(s, list.get(i))) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static <E> int lastIndexOf(List<LazyConstant<E>> list, Object o) {
        Objects.requireNonNull(o);
        if (o instanceof LazyConstant<?> s) {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (Objects.equals(s, list.get(i))) {
                    return i;
                }
            }
        }
        return -1;
    }

    @ForceInline
    private static long offsetFor(long index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index;
    }

    @SuppressWarnings("unchecked")
    private static <E> E[] newGenericArray(int length) {
        return (E[]) new Object[length];
    }

    public static <E> List<E> ofLazyList(int size,
                                         IntFunction<? extends E> computingFunction) {
        return new LazyList<>(size, computingFunction);
    }

    public static <K, V> Map<K, V> ofLazyMap(Set<K> keys,
                                             Function<? super K, ? extends V> computingFunction) {
        return new LazyMap<>(keys, computingFunction);
    }

    @SuppressWarnings("unchecked")
    public static <K, E extends Enum<E>, V>
    Map<K, V> ofLazyMapWithEnumKeys(Set<K> keys,
                                    Function<? super K, ? extends V> computingFunction) {
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
        return (Map<K, V>) new LazyEnumMap<>((Set<E>) keys, enumType, min, backingSize, member, (Function<E, V>) computingFunction);
    }

    @SuppressWarnings("unchecked")
    static <T> T orElseComputeSlowPath(final T[] array,
                                       final int index,
                                       final Object mutex,
                                       final Object input,
                                       final FunctionHolder<?> functionHolder) {
        preventReentry(mutex);
        synchronized (mutex) {
            final T t = array[index];  // Plain semantics suffice here
            if (t == null) {
                final T newValue;
                if (functionHolder == null) {
                    // If there is no functionHolder, the input must be a
                    // `Supplier` because we were called from `.orElseSet(Supplier)`
                    newValue = ((Supplier<T>) input).get();
                    Objects.requireNonNull(newValue);
                } else {
                    final Object u = functionHolder.function();
                    newValue = switch (u) {
                        case Supplier<?> sup -> (T) sup.get();
                        case IntFunction<?> iFun -> (T) iFun.apply((int) input);
                        case Function<?, ?> fun ->
                                ((Function<Object, T>) fun).apply(input);
                        default -> throw new InternalError("cannot reach here");
                    };
                    Objects.requireNonNull(newValue);
                    // Reduce the counter and if it reaches zero, clear the reference
                    // to the underlying holder.
                    functionHolder.countDown();
                }
                // The mutex is not reentrant so we know newValue should be returned
                set(array, index, mutex, newValue);
                return newValue;
            }
            return t;
        }
    }

    static void preventReentry(Object mutex) {
        if (Thread.holdsLock(mutex)) {
            throw new IllegalStateException("Recursive initialization of a lazy collection is illegal");
        }
    }

    static <T> void set(T[] array, int index, Object mutex, T newValue) {
        assert Thread.holdsLock(mutex) : index + "didn't hold " + mutex;
        // We know we hold the monitor here so plain semantic is enough
        if (array[index] == null) {
            UNSAFE.putReferenceRelease(array, Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * (long) index, newValue);
        }
    }

    /**
     * This class is thread safe. Any thread can create and use an instance of this class at
     * any time. The `function` field is only accessed if `counter` is positive so the setting
     * of function to `null` is safe.
     *
     * @param <U> the underlying function type
     */
    static final class FunctionHolder<U> {

        private static final long COUNTER_OFFSET = UNSAFE.objectFieldOffset(FunctionHolder.class, "counter");

        // This field can only transition at most once from being set to a
        // non-null reference to being `null`. Once `null`, it is never read.
        private U function;
        // Used reflectively via Unsafe
        private int counter;

        public FunctionHolder(U function, int counter) {
            this.function = (counter == 0) ? null : function;
            this.counter = counter;
            // Safe publication
            UNSAFE.storeStoreFence();
        }

        @ForceInline
        public U function() {
            return function;
        }

        public void countDown() {
            if (UNSAFE.getAndAddInt(this, COUNTER_OFFSET, -1) == 1) {
                // Do not reference the underlying function anymore so it can be collected.
                function = null;
            }
        }
    }

}
