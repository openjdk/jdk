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
        AuxiliaryArrays aux,
        int index
) implements StableValue<V> {

    @ForceInline
    @Override
    public boolean isSet() {
        int s;
        return (s = aux.states()[index]) == NON_NULL || s == NULL ||
                (s = aux.stateVolatile(index)) == NON_NULL || s == NULL;
    }

    @ForceInline
    @Override
    public boolean isError() {
        return aux.states()[index] == ERROR || aux.stateVolatile(index) == ERROR;
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
        if (aux.states()[index] == NULL) {
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
        switch (aux.stateVolatile(index)) {
            case UNSET:    { throw StableUtil.notSet(); }
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
        final var m = aux.acquireMutex(index);
        if (isMutexNotNeeded(m)) {
            return orThrow();
        }
        synchronized (m) {
            if (isSet()) {
                return orThrow();
            }
            if (isError()) {
                throw StableUtil.error(this);
            }
            setElement(value);
            return value;
        }
    }

    @Override
    public boolean trySet(V value) {
        if (isSet() || isError()) {
            return false;
        }
        final var m = aux.acquireMutex(index);
        if (isMutexNotNeeded(m)) {
            return false;
        }
        synchronized (m) {
            if (isSet() || isError()) {
                return false;
            }
            setElement(value);
            return true;
        }
    }

    private static final Function<Object, String> ERROR_MESSAGE_EXTRACTOR = new Function<Object, String>() {
        @Override
        public String apply(Object stableValue) {
            StableValueElement<?> sve = (StableValueElement<?>) stableValue;
            return ((Class<?>) sve.aux.acquireMutex(sve.index))
                    .getName();
        }
    };

    @Override
    public String toString() {
        return StableUtil.toString(this, ERROR_MESSAGE_EXTRACTOR);
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
        if (aux.states()[index] == NULL) {
            return null;
        }
        // Now, fall back to volatile semantics.
        return computeIfUnsetVolatile(provider, key);
    }

    @DontInline
    private <K> V computeIfUnsetVolatile(Object provider, K key) {
        // This is intentionally an old switch statement as it generates
        // more compact byte code.
        switch (aux.stateVolatile(index)) {
            case UNSET:    { return computeIfUnsetVolatile0(provider, key); }
            case NON_NULL: { return elementVolatile(); }
            case NULL:     { return null; }
            case ERROR:    { throw StableUtil.error(this); }
        }
        throw shouldNotReachHere();
    }

    private <K> V computeIfUnsetVolatile0(Object provider, K key) {
        final var m = aux.acquireMutex(index);
        if (isMutexNotNeeded(m)) {
            return orThrow();
        }
        synchronized (m) {
            if (aux.states()[index] != UNSET) {
                return orThrow();
            }

            // A value is not set
            if (aux.supplying(index)) {
                throw stackOverflow(provider, key);
            }
            try {
                aux.supplying(index,true);
                try {
                    @SuppressWarnings("unchecked")
                    V newValue = switch (provider) {
                        case Supplier<?> sup     -> (V) sup.get();
                        case IntFunction<?> iFun -> (V) iFun.apply((int) key);
                        case Function<?, ?> func -> ((Function<K, V>) func).apply(key);
                        default                  -> throw shouldNotReachHere();
                    };
                    setElement(newValue);
                    return newValue;
                } catch (Throwable t) {
                    aux.putState(index, ERROR);
                    aux.putMutex(index, t.getClass());
                    throw t;
                }
            } finally {
                aux.supplying(index, false);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private V elementVolatile() {
        return (V) UNSAFE.getReferenceVolatile(elements, objectOffset(index));
    }

    private void setElement(V value) {
        if (value != null) {
            // Make sure no reordering of store operations
            freeze();
            UNSAFE.putReferenceVolatile(elements, objectOffset(index), value);
        }
        // Crucially, indicate a value is set _after_ it has actually been set.
        aux.putState(index, value == null ? NULL : NON_NULL);
        aux.putMutex(index, TOMBSTONE); // We do not need a mutex anymore
    }

}
