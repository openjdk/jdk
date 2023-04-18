package jdk.internal.util.concurrent.lazy;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.lazy.BaseLazyReference;
import java.util.concurrent.lazy.EmptyLazyReference;
import java.util.concurrent.lazy.Lazy;
import java.util.function.Supplier;

abstract class AbstractPreComputedLazyReference<V>
        implements BaseLazyReference<V> {

    final V value;

    public AbstractPreComputedLazyReference(V value) {
        this.value = value;
    }

    @Override
    public Lazy.State state() {
        return Lazy.State.PRESENT;
    }

    @Override
    public Optional<Throwable> exception() {
        return Optional.empty();
    }

    @Override
    public V getOr(V defaultValue) {
        return value;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + value + "]";
    }
}
