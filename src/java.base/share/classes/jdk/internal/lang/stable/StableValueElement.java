package jdk.internal.lang.stable;

import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.ForceInline;

import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static jdk.internal.lang.stable.StableUtil.*;

// Records are ~10% faster than @ValueBased in JDK 23
public record StableValueElement<V>(
        V[] elements,
        int[] states,
        Object[] mutexes,
        int index
) implements StableValue<V> {

    @Override
    public boolean isSet() {
        return state() != UNSET || stateVolatile() != UNSET;
    }

    @Override
    public V orThrow() {
        // Optimistically try plain semantics first
        V e = elements[index];
        if (e != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a value is set.
            return e;
        }
        if (state() == NULL) {
            // If we happen to see a status value of NULL under
            // plain semantics, we know a value is set to `null`.
            return null;
        }
        // Now, fall back to volatile semantics.
        return orThrowVolatile();
    }

    @ForceInline
    private V orThrowVolatile() {
        V v = elementVolatile();
        if (v != null) {
            // If we see a non-null value, we know a value is set.
            return v;
        }
        return switch (stateVolatile()) {
            case UNSET    -> throw new NoSuchElementException(); // No value was set
            case NON_NULL -> orThrowVolatile(); // Race: another thread has set a value
            case NULL     -> null;              // A value of `null` was set
            default       -> throw shouldNotReachHere();
        };
    }

    @Override
    public void setOrThrow(V value) {
        if (isSet()) {
            throw StableUtil.alreadySet(this);
        }
        synchronized (acquireMutex()) {
            try {
                if (isSet()) {
                    throw StableUtil.alreadySet(this);
                }
                setValue(value);
            } finally {
                // There might be a new mutex created even though
                // the value was previously set so, we need to always
                // clear the mutex.
                clearMutex();
            }
        }
    }

    @Override
    public V setIfUnset(V value) {
        if (isSet()) {
            return orThrow();
        }
        synchronized (acquireMutex()) {
            try {
                if (isSet()) {
                    return orThrow();
                }
                setValue(value);
            } finally {
                // There might be a new mutex created even though
                // the value was previously set so, we need to always
                // clear the mutex.
                clearMutex();
            }
        }
        return orThrow();
    }

    @Override
    public String toString() {
        return StableUtil.toString(this);
    }

    // Avoid creating lambdas by creating
    private static final BiFunction<Supplier<?>, Void, ?> SUPPLIER_EXTRACTOR =
            new BiFunction<Supplier<?>, Void, Object>() {
                @Override
                public Object apply(Supplier<?> supplier, Void unused) {
                    return supplier.get();
                }
            };

    private static final BiFunction<IntFunction<?>, Integer, ?> INT_FUNCTION_EXTRACTOR =
            new BiFunction<IntFunction<?>, Integer, Object>() {
                @Override
                public Object apply(IntFunction<?> function, Integer index) {
                    return function.apply(index);
                }
            };

    private static final BiFunction<Function<Object, Object>, Object, Object> FUNCTION_EXTRACTOR =
            new BiFunction<Function<Object, Object>, Object, Object>() {
                @Override
                public Object apply(Function<Object, Object> function, Object o) {
                    return function.apply(o);
                }
            };

    @SuppressWarnings("unchecked")
    private static <S, K, V> BiFunction<S, K, V> functionExtractor() {
        return (BiFunction<S, K, V>) FUNCTION_EXTRACTOR;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V computeIfUnset(Supplier<? extends V> supplier) {
        return computeIfUnsetShared(supplier,
                null,
                (BiFunction<? super Supplier<? extends V>, ?, V>) SUPPLIER_EXTRACTOR);
    }

    @SuppressWarnings("unchecked")
    public V computeIfUnset(int index, IntFunction<? extends V> mapper) {
        return computeIfUnsetShared(mapper,
                index,
                (BiFunction<IntFunction<?>, Integer, V>) INT_FUNCTION_EXTRACTOR);
    }

    public <K> V computeIfUnset(K key, Function<? super K, ? extends V> mapper) {
        return computeIfUnsetShared(mapper, key, functionExtractor());
    }

    private <S, K> V computeIfUnsetShared(S source,
                                          K key,
                                          BiFunction<S, K, V> extractor) {
        // Optimistically try plain semantics first
        V e = elements[index];
        if (e != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a value is set.
            return e;
        }
        if (state() == NULL) {
            return null;
        }
        // Now, fall back to volatile semantics.
        return computeIfUnsetVolatile(source, key, extractor);
    }

    private <S, K> V computeIfUnsetVolatile(S source,
                                            K key,
                                            BiFunction<S, K, V> extractor) {
        V e = elementVolatile();
        if (e != null) {
            // If we see a non-null value, we know a value is set.
            return e;
        }
        return switch (stateVolatile()) {
            case UNSET    -> computeIfUnsetVolatile0(source, key, extractor);
            case NON_NULL -> orThrow(); // Race
            case NULL     -> null;
            default       -> throw shouldNotReachHere();
        };
    }

    private synchronized <S, K> V computeIfUnsetVolatile0(S source,
                                                          K key,
                                                          BiFunction<S, K, V> extractor) {
        synchronized (acquireMutex()) {
            if (isSet()) {
                clearMutex();
                return orThrow();
            }

            V newValue = extractor.apply(source, key);
            // If the extractor throws an exception
            // the mutex is retained

            try {
                setValue(newValue);
            } finally {
                clearMutex();
            }
        }
        return orThrow();
    }

    @SuppressWarnings("unchecked")
    private V elementVolatile() {
        return (V) UNSAFE.getReferenceVolatile(elements, objectOffset(index));
    }

    private void setValue(V value) {
        if (state() != UNSET) {
            throw StableUtil.alreadySet(this);
        }
        if (value != null) {
            putValue(value);
        }
        // Crucially, indicate a value is set _after_ it has actually been set.
        putState(value == null ? NULL : NON_NULL);
    }

    private void putValue(V created) {
        // Make sure no reordering of store operations
        freeze();
        UNSAFE.putReferenceVolatile(elements, objectOffset(index), created);
    }

    int state() {
        return states[index];
    }

    byte stateVolatile() {
        return UNSAFE.getByteVolatile(states, StableUtil.intOffset(index));
    }

    private void putState(int newValue) {
        // This prevents `this.element[index]` to be seen
        // before `this.status[index]` is seen
        freeze();
        UNSAFE.putIntVolatile(states, StableUtil.intOffset(index), newValue);
    }

    private Object acquireMutex() {
        Object mutex = UNSAFE.getReferenceVolatile(mutexes, StableUtil.objectOffset(index));
        if (mutex == null) {
            mutex = casMutex();
        }
        return mutex;
    }

    private Object casMutex() {
        Object created = new Object();
        Object mutex = UNSAFE.compareAndExchangeReference(mutexes, objectOffset(index), null, created);
        return mutex == null ? created : mutex;
    }

    private void clearMutex() {
        UNSAFE.putReferenceVolatile(mutexes, objectOffset(index), null);
    }
}
