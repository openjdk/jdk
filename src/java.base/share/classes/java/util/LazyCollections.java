/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.lang.LazyConstantImpl;
import jdk.internal.misc.Unsafe;
import jdk.internal.util.ImmutableBitSetPredicate;
import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.reflect.Array;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Container class for lazy collections implementations. Not part of the public API.
 */
@AOTSafeClassInitializer
final class LazyCollections {

    /**
     * No instances.
     */
    private LazyCollections() { }

    // Unsafe allows LazyCollection classes to be used early in the boot sequence
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    @jdk.internal.vm.annotation.TrustFinalFields
    @jdk.internal.ValueBased
    static final class LazyList<E>
            extends ImmutableCollections.AbstractImmutableList<E> {

        @Stable
        private final E[] elements;
        // Keeping track of `size` separately reduces bytecode size compared to
        // using `elements.length`.
        private final int size;
        private final FunctionHolder<IntFunction<? extends E>> functionHolder;
        private final Mutexes mutexes;
        private final Throwables throwables;

        private LazyList(int size, IntFunction<? extends E> computingFunction) {
            this.elements = newGenericArray(size);
            this.size = size;
            this.functionHolder = new FunctionHolder<>(computingFunction, size);
            this.mutexes = new Mutexes(size);
            this.throwables = new Throwables(size);
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
            return orElseComputeSlowPath(elements, i, mutexes, throwables, i, functionHolder);
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
            Objects.requireNonNull(o);
            for (int i = 0; i < size; i++) {
                if (Objects.equals(o, get(i))) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public int lastIndexOf(Object o) {
            Objects.requireNonNull(o);
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

        @SuppressWarnings("unchecked")
        @ForceInline
        private E contentsAcquire(long offset) {
            return (E) UNSAFE.getReferenceAcquire(elements, offset);
        }

    }

    @jdk.internal.vm.annotation.TrustFinalFields
    private static final class LazyEnumMap<K extends Enum<K>, V>
            extends AbstractLazyMap<K, V> {

        private final Class<K> enumType;
        // We are using a wrapper class here to be able to use a min value of zero that
        // is also stable.
        private final Integer min;
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
                    return orElseCompute(k, indexFor(k));
                }
            }
            return defaultValue;
        }

        @ForceInline
        @Override
        int indexFor(K key) {
            return key.ordinal() - min;
        }

    }

    @jdk.internal.vm.annotation.TrustFinalFields
    private static final class LazyMap<K, V>
            extends AbstractLazyMap<K, V> {

        // Use an unmodifiable map with known entries that are @Stable. Lookups through this map can be folded because
        // it is created using Map.ofEntries. This allows us to avoid creating a separate hashing function.
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

        @ForceInline
        @Override
        // This method will throw an NPE if the key does not exist. So, callers need to
        // make sure the key exist before invoking this method.
        int indexFor(K key) {
            return indexMapper.get(key);
        }
    }

    @jdk.internal.vm.annotation.TrustFinalFields
    private static abstract sealed class AbstractLazyMap<K, V>
            extends ImmutableCollections.AbstractImmutableMap<K, V> {

        private final Mutexes mutexes;
        private final Throwables throwables;
        private final int size;
        private final FunctionHolder<Function<? super K, ? extends V>> functionHolder;
        private final Set<Entry<K, V>> entrySet;
        // This field shadows AbstractMap.values which is of another type
        @Stable
        private final V[] values;
        // This field shadows AbstractMap.keySet which is not trusted
        private final Set<K> keySet;

        // We are using a `long` here to get stable access even in the case
        // that the 32-bit hash code is zero.
        @Stable
        private long hash;

        private AbstractLazyMap(Set<K> keySet,
                                int size,
                                int backingSize,
                                Function<? super K, ? extends V> computingFunction) {
            this.size = size;
            this.functionHolder = new FunctionHolder<>(computingFunction, size);
            this.values = newGenericArray(backingSize);
            this.mutexes = new Mutexes(backingSize);
            this.throwables = new Throwables(backingSize);
            this.keySet = keySet;
            super();
            this.entrySet = LazyMapEntrySet.of(this);
        }

        // Abstract methods
        @Override public abstract boolean containsKey(Object o);
        abstract int indexFor(K key);

        // Public methods
        @Override public final int              size() { return size; }
        @Override public final boolean          isEmpty() { return size == 0; }
        @Override public final Set<Entry<K, V>> entrySet() { return entrySet; }
        @Override public Set<K>                 keySet() { return keySet; }

        @Override
        public final boolean containsValue(Object value) {
            Objects.requireNonNull(value);
            for (K key : keySet) {
                if (value.equals(orElseCompute(key, indexFor(key)))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public final int hashCode() {
            // Racy computation
            long h = hash;
            if (h == 0) {
                // Set a bit in the upper 32-bit region of the `long` to
                // cater for the case the lower 32-bit hash is zero.
                hash = h = expandToLong(hashCode0());
            }
            return reduceToInt(h);
        }

        private int hashCode0() {
            int hash = 0;
            for (K key : keySet) {
                hash += key.hashCode() ^ orElseCompute(key, indexFor(key)).hashCode();
            }
            return hash;
        }

        @Override
        public final void forEach(BiConsumer<? super K, ? super V> action) {
            Objects.requireNonNull(action);
            for (K key : keySet) {
                action.accept(key, orElseCompute(key, indexFor(key)));
            }
        }

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
            return orElseComputeSlowPath(values, index, mutexes, throwables, key, functionHolder);
        }

        @jdk.internal.vm.annotation.TrustFinalFields
        @jdk.internal.ValueBased
        private static final class LazyMapEntrySet<K, V> extends ImmutableCollections.AbstractImmutableSet<Entry<K, V>> {

            // Use a separate field for the outer class in order to facilitate
            // a trusted field.
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

            @jdk.internal.vm.annotation.TrustFinalFields
            @jdk.internal.ValueBased
            static final class LazyMapIterator<K, V> implements Iterator<Entry<K, V>> {

                // Use a separate field for the outer class in order to facilitate
                // a trusted field.
                private final AbstractLazyMap<K, V> map;
                private final Iterator<K> keyIterator;

                private LazyMapIterator(AbstractLazyMap<K, V> map) {
                    this.map = map;
                    this.keyIterator = map.keySet.iterator();
                    super();
                }

                @Override public boolean hasNext() { return keyIterator.hasNext(); }

                @Override
                public Entry<K, V> next() {
                    final K k = keyIterator.next();
                    return new LazyEntry<>(k, map);
                }

                @Override
                public void forEachRemaining(Consumer<? super Entry<K, V>> action) {
                    Objects.requireNonNull(action);
                    final Consumer<? super K> innerAction =
                            new Consumer<>() {
                                @Override
                                public void accept(K key) {
                                    action.accept(new LazyEntry<>(key, map));
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

        private record LazyEntry<K, V>(@Override K getKey, // trick
                                       AbstractLazyMap<K, V> map) implements Entry<K, V> {

            @Override public V      setValue(V value) { throw ImmutableCollections.uoe(); }
            @Override public V      getValue() { return map.orElseCompute(getKey, map.indexFor(getKey)); }
            @Override public int    hashCode() { return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue()); }
            @Override public String toString() { return getKey() + "=" + getValue(); }

            @Override
            public boolean equals(Object o) {
                return o instanceof Map.Entry<?, ?> e
                        && Objects.equals(getKey(), e.getKey())
                        // Invoke `getValue()` as late as possible to avoid evaluation
                        && Objects.equals(getValue(), e.getValue());
            }

        }

        @Override
        public Collection<V> values() {
            return LazyMapValues.of(this);
        }

        @jdk.internal.vm.annotation.TrustFinalFields
        @jdk.internal.ValueBased
        static final class LazyMapValues<K, V> extends ImmutableCollections.AbstractImmutableCollection<V> {

            // Use a separate field for the outer class in order to facilitate
            // a trusted field.
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

    @jdk.internal.vm.annotation.TrustFinalFields
    private static final class LazySet<E>
            extends ImmutableCollections.AbstractImmutableSet<E>
            implements Set<E> {

        private final Map<E, Boolean> map;

        // -1 is used as a sentinel value for zero so we can get
        // stable access for all `size` values. `size` is always non-negative.
        @Stable
        private int size;
        // We are using a `long` here to get stable access even in the case
        // that the 32-bit hash code is zero.
        @Stable
        private long hash;

        public LazySet(Set<? extends E> elementCandidates,
                       Predicate<? super E> computingFunction) {
            this.map = Map.ofLazy(elementCandidates, computingFunction::test);
            super();
        }

        @Override
        public boolean contains(Object o) {
            return map.getOrDefault(o, Boolean.FALSE).booleanValue();
        }

        @Override
        public int hashCode() {
            // Racy computation
            long h = hash;
            if (h == 0) {
                // Set a bit in the upper 32-bit region of the `long` to
                // cater for the case the lower 32-bit hash is zero.
                hash = h = expandToLong(hashCode0());
            }
            return reduceToInt(h);
        }

        private int hashCode0() {
            int hash = 0;
            for (var e: map.entrySet()) {
                if (e.getValue()) {
                    hash += e.getKey().hashCode();
                }
            }
            return hash;
        }

        @Override
        public Iterator<E> iterator() {
            return new LazySetIterator<>(map.entrySet().iterator());
        }

        @jdk.internal.vm.annotation.TrustFinalFields
        static final class LazySetIterator<E> implements Iterator<E> {

            private final Iterator<Map.Entry<E, Boolean>> iterator;

            E current;

            public LazySetIterator(Iterator<Map.Entry<E, Boolean>> iterator) {
                this.iterator = iterator;
                super();
            }

            @Override
            public boolean hasNext() {
                if (current != null) {
                    return true;
                }
                while (iterator.hasNext()) {
                    Map.Entry<E, Boolean> e = iterator.next();
                    if (e.getValue()) {
                        current = e.getKey();
                        return true;
                    }
                }
                return false;
            }

            @Override
            public E next() {
                E e = current;
                if (e != null) {
                    return consumeCurrent(e);
                }
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return consumeCurrent(current);
            }

            private E consumeCurrent(E e) {
                current = null;
                return e;
            }

        }

        @Override
        public int size() {
            // Racy computation
            int s = size;
            if (s == 0) {
                s = size0();
                if (s == 0) {
                    s = -1;
                }
                size = s;
            }
            return s == -1 ? 0 : s;
        }

        private int size0() {
            int size = 0;
            for (var e: map.entrySet()) {
                if (e.getValue()) {
                    size++;
                }
            }
            return size;
        }

    }

    private static final class Mutexes {

        private static final Object TOMB_STONE = new Object();

        // Filled on demand and then discarded once it is not needed anymore.
        // A mutex element can only transition like so: `null` -> `new Object()` -> `TOMB_STONE`
        private volatile Object[] mutexes;
        // Used to detect we have computed all elements and no longer need the `mutexes` array
        private volatile AtomicInteger counter;

        private Mutexes(int length) {
            this.mutexes = new Object[length];
            this.counter = new AtomicInteger(length);
        }

        private Object acquireMutex(long offset) {
            // Snapshot
            var mutexes = this.mutexes;
            if (mutexes == null) {
                // We have already computed all the elements and if we end up here
                // there was at least one unchecked exception thrown by the
                // computing function.
                return null;
            }
            // Check if there already is a mutex (Object or TOMB_STONE)
            final Object mutex = UNSAFE.getReferenceVolatile(mutexes, offset);
            if (mutex != null) {
                return mutex;
            }
            // Protect against racy stores of mutex candidates
            final Object candidate = new Object();
            final Object witness = UNSAFE.compareAndExchangeReference(mutexes, offset, null, candidate);
            return witness == null ? candidate : witness;
        }

        private void releaseMutex(long offset) {
            // Replace the old mutex with a tomb stone since now the old mutex can be collected.
            UNSAFE.putReferenceVolatile(mutexes, offset, TOMB_STONE);
            if (counter != null && counter.decrementAndGet() == 0) {
                mutexes = null;
                counter = null;
            }
        }

    }

    /** Holds the throwable class names produced by the computing function.
     * <p>
     * Class names are used instead of Class objects to avoid pinning class loaders after
     * a failed computation.
     * <p>
     * This class is not thread safe across indices. However, it will always be accessed
     * under the same monitor for a given index.
     */
    private static final class Throwables {

        @Stable
        final String[] throwables;

        Throwables(int size) {
            this.throwables = new String[size];
            super();
        }

        Optional<String> get(int index) {
            return Optional.ofNullable(throwables[index]);
        }

        void set(int index, Throwable throwable) {
            throwables[index] = throwable.getClass().getName().intern();
        }
    }

    @ForceInline
    private static long offsetFor(long index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * index;
    }

    @SuppressWarnings("unchecked")
    private static <E> E[] newGenericArray(int length) {
        return (E[]) new Object[length];
    }


    @SuppressWarnings("unchecked")
    private static <T> T orElseComputeSlowPath(final T[] array,
                                       final int index,
                                       final Mutexes mutexes,
                                       final Throwables throwables,
                                       final Object input,
                                       final FunctionHolder<?> functionHolder) {
        final long offset = offsetFor(index);
        final Object mutex = mutexes.acquireMutex(offset);
        if (mutex == null) {
            throwIfPreviousException(index, throwables, input);
            // There must be an exception
            throw cannotReachHere(functionHolder, input);
        }
        preventReentry(mutex, input);
        synchronized (mutex) {
            final T t = array[index];  // Plain semantics suffice here
            if (t == null) {
                throwIfPreviousException(index, throwables, input);
                try {
                    final T newValue = switch (functionHolder.function()) {
                        case IntFunction<?> iFun -> (T) iFun.apply((int) input);
                        case Function<?, ?> fun  -> ((Function<Object, T>) fun).apply(input);
                        default                  -> throw cannotReachHere(functionHolder, input);
                    };
                    Objects.requireNonNull(newValue);

                    // The mutex is not reentrant so we know newValue should be returned
                    set(array, index, mutex, newValue);
                    return newValue;
                } catch (Throwable x) {
                    throwables.set(index, x);
                    // Wrap the initial throwable without pinning its class loader.
                    throw noSuchElementException(x.getClass().getName(), input, x);
                } finally {
                    // Reduce the counter and if it reaches zero, clear the reference
                    // to the underlying holder.
                    functionHolder.countDown();

                    // We do not need the mutex anymore
                    mutexes.releaseMutex(offset);
                }
            }
            return t;
        }
    }

    private static void throwIfPreviousException(int index, Throwables throwables, Object input) {
        final var throwable = throwables.get(index);
        if (throwable.isPresent()) {
            throw noSuchElementException(throwable.get(), input, null);
        }
    }

    private static NoSuchElementException noSuchElementException(String throwableName,
                                                         Object input,
                                                         Throwable cause) {
        final String isolatedToString = LazyConstantImpl.isolateToString(input);
        var message = "Unable to access the lazy collection because " + throwableName +
                " was thrown at initial computation for input '" + isolatedToString + "'";
        return new NoSuchElementException(message, cause);
    }

    private static InternalError cannotReachHere(FunctionHolder<?> functionHolder, Object input) {
        return new InternalError("cannot reach here: " + functionHolder.function() + " for " + LazyConstantImpl.isolateToString(input));
    }

    private static void preventReentry(Object mutex, Object input) {
        if (Thread.holdsLock(mutex)) {
            throw new IllegalStateException("Recursive initialization of a lazy collection is illegal: " + LazyConstantImpl.isolateToString(input));
        }
    }

    private static <T> void set(T[] array, int index, Object mutex, T newValue) {
        assert Thread.holdsLock(mutex) : index + "didn't hold " + mutex;
        // We know we hold the monitor here so plain semantic is enough
        // This is an extra safety net to emulate a CAS op.
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
    @AOTSafeClassInitializer
    private static final class FunctionHolder<U> {

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

    // Methods for supporting stable `int` values using a `long` field.

    private static long expandToLong(int value) {
        return (value + (1L << 33));
    }

    private static int reduceToInt(long value) {
        return (int) value;
    }

    // Factories

    static <E> List<E> ofLazyList(int size,
                                         IntFunction<? extends E> computingFunction) {
        return new LazyList<>(size, computingFunction);
    }

    static <K, V> Map<K, V> ofLazyMap(Set<K> keys,
                                             Function<? super K, ? extends V> computingFunction) {
        return new LazyMap<>(keys, computingFunction);
    }

    static <E> Set<E> ofLazySet(Set<? extends E> elementCandidates,
                                       Predicate<? super E> computingFunction) {
        return new LazySet<>(elementCandidates, computingFunction);
    }

    @SuppressWarnings("unchecked")
    static <K, E extends Enum<E>, V> Map<K, V> ofLazyMapWithEnumKeys(Set<K> keys,
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

}
