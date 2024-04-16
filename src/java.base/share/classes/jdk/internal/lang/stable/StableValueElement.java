package jdk.internal.lang.stable;

import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static jdk.internal.lang.stable.StableUtil.*;

// Records are ~10% faster than @ValueBased
public record StableValueElement<V>(
        V[] elements,
        byte[] sets,
        Object[] mutexes,
        int index
) implements StableValue<V> {

    @Override
    public V orThrow() {
        // Optimistically try plain semantics first
        V e = elements[index];
        if (e != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a value is present.
            return e;
        }
        if (set()) {
            return null;
        }
        // Now, fall back to volatile semantics.
        e = elementVolatile();
        if (e != null) {
            // If we see a non-null value, we know a value is present.
            return e;
        }
        if (setVolatile()) {
            return null;
        }
        throw new NoSuchElementException();
    }

    @Override
    public boolean isSet() {
        return set() || setVolatile();
    }

    @Override
    public void setOrThrow(V value) {
        if (isSet()) {
            throw StableUtil.alreadySet(this);
        }
        Object mutex = mutexVolatile();
        if (mutex == null) {
            mutex = casMutex();
        }
        synchronized (mutex) {
            setValue(value);
        }
        clearMutex();
    }

    @Override
    public V setIfUnset(V value) {
        if (isSet()) {
            return orThrow();
        }
        Object mutex = mutexVolatile();
        if (mutex == null) {
            mutex = casMutex();
        }
        synchronized (mutex) {
            setValue(value);
        }
        clearMutex();
        return orThrow();
    }

    @Override
    public String toString() {
        return StableUtil.toString(this);
    }

    @Override
    public V computeIfUnset(Supplier<? extends V> supplier) {
        return computeIfUnset0(supplier, Supplier::get);
    }

    public V computeIfUnset(IntFunction<? extends V> mapper) {
        return computeIfUnset0(mapper, m -> m.apply(index));
    }

    public <K> V computeIfUnset(K key, Function<? super K, ? extends V> mapper) {
        return computeIfUnset0(mapper, m -> m.apply(key));
    }

    private <S> V computeIfUnset0(S source,
                                  Function<S, V> extractor) {
        // Optimistically try plain semantics first
        V e = elements[index];
        if (e != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a value is present.
            return e;
        }
        if (set()) {
            return null;
        }
        // Now, fall back to volatile semantics.
        e = elementVolatile();
        if (e != null) {
            // If we see a non-null value, we know a value is present.
            return e;
        }
        if (setVolatile()) {
            return null;
        }
        Object mutex = mutexVolatile();
        if (mutex == null) {
            mutex = casMutex();
        }
        synchronized (mutex) {
            V newValue = extractor.apply(source);
            setValue(newValue);
        }
        clearMutex();
        return orThrow();
    }

    @SuppressWarnings("unchecked")
    private V elementVolatile() {
        return (V) UNSAFE.getReferenceVolatile(elements, objectOffset(index));
    }

    private void setValue(V value) {
        if (value != null) {
            casValue(value);
        }
        // Crucially, indicate a value is present _after_ it has been set.
        casSet();
    }

    private void casValue(V created) {
        // Make sure no reordering of store operations
        freeze();
        if (!UNSAFE.compareAndSetReference(elements, objectOffset(index), null, created)) {
            throw StableUtil.alreadySet(this);
        }
    }

    boolean set() {
        return sets[index] == SET;
    }

    boolean setVolatile() {
        return UNSAFE.getByteVolatile(sets, StableUtil.byteOffset(index)) == SET;
    }

    private void casSet() {
        if (!UNSAFE.compareAndSetByte(sets, StableUtil.byteOffset(index), NOT_SET, SET)) {
            throw StableUtil.alreadySet(this);
        }
    }

    private Object mutexVolatile() {
        return UNSAFE.getReferenceVolatile(mutexes, StableUtil.objectOffset(index));
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
