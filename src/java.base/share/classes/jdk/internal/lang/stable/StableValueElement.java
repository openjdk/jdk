package jdk.internal.lang.stable;

import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;

import java.util.NoSuchElementException;
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

    @ForceInline
    @Override
    public boolean isSet() {
        return states[index] != UNSET || stateVolatile() != UNSET;
    }

    @ForceInline
    @Override
    public V orThrow() {
        // Optimistically try plain semantics first
        V e = elements[index];
        if (e != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a value is set.
            return e;
        }
        if (states[index] == NULL) {
            // If we happen to see a status value of NULL under
            // plain semantics, we know a value is set to `null`.
            return null;
        }
        // Now, fall back to volatile semantics.
        return orThrowVolatile();
    }

    @DontInline
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

    @ForceInline
    @Override
    public V computeIfUnset(Supplier<? extends V> supplier) {
        return computeIfUnsetShared(supplier, null);
    }

    @ForceInline
    public V computeIfUnset(int index, IntFunction<? extends V> mapper) {
        return computeIfUnsetShared(mapper, index);
    }

    @ForceInline
    public <K> V computeIfUnset(K key, Function<? super K, ? extends V> mapper) {
        return computeIfUnsetShared(mapper, key);
    }

    @ForceInline
    private <K> V computeIfUnsetShared(Object provider, K key) {
        // Optimistically try plain semantics first
        V e = elements[index];
        if (e != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a value is set.
            return e;
        }
        if (states[index] == NULL) {
            return null;
        }
        // Now, fall back to volatile semantics.
        return computeIfUnsetVolatile(provider, key);
    }

    @DontInline
    private <K> V computeIfUnsetVolatile(Object provider, K key) {
        V e = elementVolatile();
        if (e != null) {
            // If we see a non-null value, we know a value is set.
            return e;
        }
        return switch (stateVolatile()) {
            case UNSET    -> computeIfUnsetVolatile0(provider, key);
            case NON_NULL -> orThrow(); // Race
            case NULL     -> null;
            default       -> throw shouldNotReachHere();
        };
    }

    private synchronized <K> V computeIfUnsetVolatile0(Object provider, K key) {
        synchronized (acquireMutex()) {
            if (isSet()) {
                clearMutex();
                return orThrow();
            }

            @SuppressWarnings("unchecked")
            V newValue = switch (provider) {
                case Supplier<?>    sup  -> (V) sup.get();
                case IntFunction<?> iFun -> (V) iFun.apply((int) key);
                case Function<?, ?> func -> ((Function<K, V>) func).apply(key);
                default                  -> throw shouldNotReachHere();
            };
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
        if (states[index] != UNSET) {
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
            mutex = caeMutex();
        }
        return mutex;
    }

    private Object caeMutex() {
        Object created = new Object();
        Object witness = UNSAFE.compareAndExchangeReference(mutexes, objectOffset(index), null, created);
        return witness == null ? created : witness;
    }

    private void clearMutex() {
        UNSAFE.putReferenceVolatile(mutexes, objectOffset(index), null);
    }
}
