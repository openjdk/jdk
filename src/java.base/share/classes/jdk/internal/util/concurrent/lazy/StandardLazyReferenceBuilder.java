package jdk.internal.util.concurrent.lazy;

import java.util.Objects;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReference;
import java.util.function.Supplier;

public record StandardLazyReferenceBuilder<V>(Supplier<? extends V> s,
                                              V v,
                                              Lazy.Evaluation e)
        implements LazyReference.Builder<V> {

    public StandardLazyReferenceBuilder(Supplier<? extends V> s) {
        this(s, null, Lazy.Evaluation.AT_USE);
    }

    @Override
    public LazyReference.Builder<V> withValue(V v) {
        Objects.requireNonNull(v);
        return new StandardLazyReferenceBuilder<>(s, v, e);
    }

    @Override
    public LazyReference.Builder<V> withEarliestEvaluation(Lazy.Evaluation e) {
        return new StandardLazyReferenceBuilder<>(s, v, e);
    }

    @Override
    public LazyReference<V> build() {
        return (v != null)
                ? new PreComputedLazyReference<>(v)
                : new StandardLazyReference<>(s);
    }

}
