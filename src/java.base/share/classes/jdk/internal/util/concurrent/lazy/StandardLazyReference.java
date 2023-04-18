package jdk.internal.util.concurrent.lazy;

import java.util.NoSuchElementException;
import java.util.concurrent.lazy.BaseLazyReference;
import java.util.concurrent.lazy.LazyReference;
import java.util.function.Supplier;

public final class StandardLazyReference<V>
        extends AbstractBaseLazyReference<V>
        implements LazyReference<V> {

    private Supplier<? extends V> supplier;

    public StandardLazyReference(Supplier<? extends V> supplier) {
        this.supplier = supplier;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get() {
        V v = value;
        if (v != null) {
            return v;
        }
        return LazyUtil.supplyIfEmpty(this, supplier);
    }

    @Override
    void afterSupplying() {
        // Make the supplier elagable for collection
        supplier = null;
    }

}
