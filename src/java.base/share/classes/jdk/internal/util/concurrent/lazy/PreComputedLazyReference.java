package jdk.internal.util.concurrent.lazy;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.lazy.BaseLazyReference;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReference;
import java.util.function.Supplier;

public final class PreComputedLazyReference<V>
        extends AbstractPreComputedLazyReference<V>
        implements LazyReference<V> {

    public PreComputedLazyReference(V value) {
        super(value);
    }

    @Override
    public V get() {
        return value;
    }

}
