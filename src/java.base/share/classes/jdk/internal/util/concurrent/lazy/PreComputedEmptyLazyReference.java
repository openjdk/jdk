package jdk.internal.util.concurrent.lazy;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.lazy.EmptyLazyReference;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReference;
import java.util.function.Supplier;

public final class PreComputedEmptyLazyReference<V>
    extends AbstractPreComputedLazyReference<V>
        implements EmptyLazyReference<V> {

    public PreComputedEmptyLazyReference(V value) {
        super(value);
    }

    @Override
    public V apply(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        return value;
    }

}
