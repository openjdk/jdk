package jdk.internal.util.concurrent.lazy;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.lazy.BaseLazyReference;
import java.util.concurrent.lazy.EmptyLazyReference;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReference;
import java.util.function.Supplier;

public final class StandardEmptyLazyReference<V>
        extends AbstractBaseLazyReference<V>
        implements EmptyLazyReference<V> {

    @SuppressWarnings("unchecked")
    @Override
    public V apply(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        V v = value;
        if (v != null) {
            return v;
        }
        return LazyUtil.supplyIfEmpty(this, supplier);
    }

    @Override
    void afterSupplying() {
        // Do nothing.
    }

}
