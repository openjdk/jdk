package jdk.internal.util.concurrent.lazy;

import java.util.Objects;
import java.util.concurrent.lazy.EmptyLazyReference;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReference;
import java.util.function.Supplier;

public record StandardEmptyLazyReferenceBuilder<V>(V v)
        implements EmptyLazyReference.Builder<V> {

    public StandardEmptyLazyReferenceBuilder() {
        this(null);
    }

    @Override
    public EmptyLazyReference.Builder<V> withValue(V v) {
        Objects.requireNonNull(v);
        return new StandardEmptyLazyReferenceBuilder<>(v);
    }

    @Override
    public EmptyLazyReference<V> build() {
        return v != null
                ? new PreComputedEmptyLazyReference<>(v)
                : new StandardEmptyLazyReference<>();
    }

}
