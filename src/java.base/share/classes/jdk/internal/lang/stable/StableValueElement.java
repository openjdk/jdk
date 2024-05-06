package jdk.internal.lang.stable;

import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static jdk.internal.lang.stable.StableUtil.*;

// Records are ~10% faster than @ValueBased in JDK 23
public record StableValueElement<V>(
        @Stable V[] elements,
        @Stable int[] states,
        @Stable Object[] mutexes,
        boolean[] supplyings, // Todo: make this array more dense
        int index
) implements StableValue<V> {

    @ForceInline
    @Override
    public boolean isSet() {
        int s;
        return (s = states[index]) == NON_NULL || s == NULL ||
                (s = stateVolatile()) == NON_NULL || s == NULL;
    }

    @ForceInline
    @Override
    public boolean isError() {
        return states[index] == ERROR || stateVolatile() == ERROR;
    }

    @ForceInline
    @Override
    public V orThrow() {
        // Todo: consider UNSAFE.getReference(elements, ...) as we have checked the index
        // Optimistically try plain semantics first
        final V e = elements[index];
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

    @DontInline // Slow-path taken at most once per thread if set
    private V orThrowVolatile() {
        // This is intentionally an old switch statement as it generates
        // more compact byte code.
        switch (stateVolatile()) {
            case UNSET:    { throw StableUtil.notSet();}
            case NON_NULL: { return elementVolatile(); }
            case NULL:     { return null; }
            case ERROR:    { throw StableUtil.error(this); }
        }
        throw shouldNotReachHere();
    }

    @Override
    public V setIfUnset(V value) {
        if (isSet() || isError()) {
            return orThrow();
        }
        final var m = acquireMutex();
        if (m == TOMBSTONE) {
            return orThrow();
        }
        synchronized (m) {
            if (isSet()) {
                return orThrow();
            }
            if (isError()) {
                throw StableUtil.error(this);
            }
            setValue(value);
            return value;
        }
    }

    @Override
    public boolean trySet(V value) {
        if (isSet() || isError()) {
            return false;
        }
        final var m = acquireMutex();
        if (m == TOMBSTONE) {
            return false;
        }
        synchronized (m) {
            if (isSet() || isError()) {
                return false;
            }
            setValue(value);
            return true;
        }
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
        final V e = elements[index];
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
        // This is intentionally an old switch statement as it generates
        // more compact byte code.
        switch (stateVolatile()) {
            case UNSET:    { return computeIfUnsetVolatile0(provider, key);}
            case NON_NULL: { return elementVolatile(); }
            case NULL:     { return null; }
            case ERROR:    { throw StableUtil.error(this); }
        }
        throw shouldNotReachHere();
    }

    private <K> V computeIfUnsetVolatile0(Object provider, K key) {
        final var m = acquireMutex();
        if (m == TOMBSTONE) {
            return orThrow();
        }
        synchronized (m) {
            if (states[index] != UNSET) {
                return orThrow();
            }

            // A value is not set
            if (supplying()) {
                throw stackOverflow(provider, key);
            }
            try {
                supplying(true);
                try {
                    @SuppressWarnings("unchecked")
                    V newValue = switch (provider) {
                        case Supplier<?> sup     -> (V) sup.get();
                        case IntFunction<?> iFun -> (V) iFun.apply((int) key);
                        case Function<?, ?> func -> ((Function<K, V>) func).apply(key);
                        default                  -> throw shouldNotReachHere();
                    };
                    setValue(newValue);
                    return newValue;
                } catch (Throwable t) {
                    putState(ERROR);
                    putMutexTombstone();
                    throw t;
                }
            } finally {
                supplying(false);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private V elementVolatile() {
        return (V) UNSAFE.getReferenceVolatile(elements, objectOffset(index));
    }

    private void setValue(V value) {
        if (value != null) {
            putValue(value);
        }
        // Crucially, indicate a value is set _after_ it has actually been set.
        putState(value == null ? NULL : NON_NULL);
        putMutexTombstone(); // We do not need a mutex anymore
    }

    private void putValue(V created) {
        // Make sure no reordering of store operations
        freeze();
        UNSAFE.putReferenceVolatile(elements, objectOffset(index), created);
    }

    private byte stateVolatile() {
        return UNSAFE.getByteVolatile(states, StableUtil.intOffset(index));
    }

    private boolean supplying() {
        return supplyings[index];
    }

    private void supplying(boolean supplying) {
        supplyings[index] = supplying;
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
        final var created = new Object();
        final var witness = UNSAFE.compareAndExchangeReference(mutexes, objectOffset(index), null, created);
        return witness == null ? created : witness;
    }

    private void putMutexTombstone() {
        UNSAFE.putReferenceVolatile(mutexes, objectOffset(index), TOMBSTONE);
    }

}
