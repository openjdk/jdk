package jdk.internal.lang.stable;

import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.ForceInline;

import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import static jdk.internal.lang.stable.StableUtil.*;

// Records are ~10% faster than @ValueBased in JDK 23
public record StableValueElement<V>(
        V[] elements,
        // Todo: Rename this variable
        byte[] sets,
        Object[] mutexes,
        int index
) implements StableValue<V> {

    @Override
    public boolean isSet() {
        return set() != UNSET || setVolatile() != UNSET;
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
        if (set() == NULL) {
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
        return switch (setVolatile()) {
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
            if (isSet()) {
                return orThrow();
            }
            setValue(value);
        }
        clearMutex();
        return orThrow();
    }

    @Override
    public String toString() {
        return StableUtil.toString(this);
    }

    // Avoid creating lambdas
    private static final Function<Supplier<?>, ?> SUPPLIER_EXTRACTOR =
            new Function<Supplier<?>, Object>() {
                @Override
                public Object apply(Supplier<?> supplier) {
                    return supplier.get();
                }
            };

    @SuppressWarnings("unchecked")
    private static <K, V> Function<? super K, ? extends V> supplierExtractor() {
        return (Function<? super K, ? extends V>) SUPPLIER_EXTRACTOR;
    }

    @Override
    public V computeIfUnset(Supplier<? extends V> supplier) {
        // Todo: This creates a lambda
        return computeIfUnsetShared(supplier, Supplier::get);
    }

    public V computeIfUnset(IntFunction<? extends V> mapper) {
        // Todo: This creates a lambda that captures
        return computeIfUnsetShared(mapper, m -> m.apply(index));
    }

    public <K> V computeIfUnset(K key, Function<? super K, ? extends V> mapper) {
        // Todo: This creates a lambda that captures
        return computeIfUnsetShared(mapper, m -> m.apply(key));
    }

    private <S> V computeIfUnsetShared(S source,
                                       Function<S, V> extractor) {
        // Optimistically try plain semantics first
        V e = elements[index];
        if (e != null) {
            // If we happen to see a non-null value under
            // plain semantics, we know a value is set.
            return e;
        }
        if (set() == NULL) {
            return null;
        }
        // Now, fall back to volatile semantics.
        return computeIfUnsetVolatile(source, extractor);
    }

    private <S> V computeIfUnsetVolatile(S source,
                                         Function<S, V> extractor) {
        V e = elementVolatile();
        if (e != null) {
            // If we see a non-null value, we know a value is set.
            return e;
        }
        return switch (setVolatile()) {
            case UNSET    -> computeIfUnsetVolatile0(source, extractor);
            case NON_NULL -> orThrow(); // Race
            case NULL     -> null;
            default       -> throw shouldNotReachHere();
        };
    }

    private synchronized <S> V computeIfUnsetVolatile0(S source,
                                                       Function<S, V> extractor) {
        Object mutex = mutexVolatile();
        if (mutex == null) {
            mutex = casMutex();
        }
        synchronized (mutex) {
            if (isSet()) {
                return orThrow();
            }
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
        // This prevents `this.element[index]` to be seen
        // before `this.sets[index]` is seen
        freeze();
        // Crucially, indicate a value is set _after_ it has actually been set.
        casSet(value == null ? NULL : NON_NULL);
    }

    private void casValue(V created) {
        // Make sure no reordering of store operations
        freeze();
        if (!UNSAFE.compareAndSetReference(elements, objectOffset(index), null, created)) {
            throw StableUtil.alreadySet(this);
        }
    }

    byte set() {
        return sets[index];
    }

    byte setVolatile() {
        return UNSAFE.getByteVolatile(sets, StableUtil.byteOffset(index));
    }

    private void casSet(byte newValue) {
        if (!UNSAFE.compareAndSetByte(sets, StableUtil.byteOffset(index), UNSET, newValue)) {
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
