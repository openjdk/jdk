package jdk.internal.lang.lazy;

import jdk.internal.ValueBased;
import jdk.internal.vm.annotation.Stable;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

import static jdk.internal.lang.lazy.LazyUtil.*;

/*
@ValueBased
Benchmark                            Mode  Cnt  Score   Error  Units
LazyListBenchmark.instanceArrayList  avgt   10  1.045 ? 0.039  ns/op
LazyListBenchmark.instanceDelegated  avgt   10  1.631 ? 0.007  ns/op <--
LazyListBenchmark.instanceLazyList   avgt   10  1.035 ? 0.040  ns/op
LazyListBenchmark.instanceWrapped    avgt   10  1.379 ? 0.006  ns/op
LazyListBenchmark.staticArrayList    avgt   10  0.837 ? 0.029  ns/op
LazyListBenchmark.staticLazyList     avgt   10  0.578 ? 0.079  ns/op

record
Benchmark                            Mode  Cnt  Score   Error  Units
LazyListBenchmark.instanceArrayList  avgt   10  1.044 ? 0.032  ns/op
LazyListBenchmark.instanceDelegated  avgt   10  1.421 ? 0.038  ns/op <-- 13% faster than @ValueBased
LazyListBenchmark.instanceLazyList   avgt   10  1.015 ? 0.010  ns/op
LazyListBenchmark.instanceWrapped    avgt   10  1.339 ? 0.043  ns/op <-- Delegated almost as fast as wrapped
LazyListBenchmark.staticArrayList    avgt   10  0.830 ? 0.027  ns/op
LazyListBenchmark.staticLazyList     avgt   10  0.563 ? 0.005  ns/op
 */


@ValueBased
public record LazyListElement<V>(
        V[] elements,
        byte[] sets,
        Object[] mutexes,
        int index
) implements Lazy<V> {

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
            throw new IllegalStateException("A value is already bound: " + orThrow());
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
    public V computeIfUnset(Supplier<? extends V> supplier) {
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
            V newValue = supplier.get();
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

    @SuppressWarnings("unchecked")
    private void casValue(V created) {
        // Make sure no reordering of store operations
        freeze();
        if (!UNSAFE.compareAndSetReference(elements, objectOffset(index), null, created)) {
            throw new IllegalStateException("A value is already set: " + orThrow());
        }
    }

    boolean set() {
        return sets[index] == 1;
    }

    boolean setVolatile() {
        return UNSAFE.getByteVolatile(sets, LazyUtil.byteOffset(index)) == 1;
    }

    private void casSet() {
        if (!UNSAFE.compareAndSetByte(sets, LazyUtil.byteOffset(index), (byte) 0, (byte) 1)) {
            throw new IllegalStateException("A value is already set: " + orThrow());
        }
    }

    private Object mutexVolatile() {
        return UNSAFE.getReferenceVolatile(mutexes, LazyUtil.objectOffset(index));
    }

    private Object casMutex() {
        Object created = new Object();
        Object mutex = UNSAFE.compareAndExchangeReference(mutexes, objectOffset(index), null, created);
        return mutex == null ? created : mutex;
    }

    private void clearMutex() {
        UNSAFE.putReferenceVolatile(mutexes, objectOffset(index), null);
    }

    static <V> Lazy<V> lazyListElement(V[] elements,
                                       byte[] sets,
                                       Object[] mutexes,
                                       int index) {
        return new LazyListElement<>(elements, sets, mutexes, index);
    }
}
