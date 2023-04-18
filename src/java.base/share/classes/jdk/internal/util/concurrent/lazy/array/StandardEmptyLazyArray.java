package jdk.internal.util.concurrent.lazy.array;

import jdk.internal.util.concurrent.lazy.StandardEmptyLazyReference;
import jdk.internal.util.concurrent.lazy.StandardLazyReference;

import java.util.Objects;
import java.util.concurrent.lazy.BaseLazyArray;
import java.util.concurrent.lazy.EmptyLazyArray;
import java.util.concurrent.lazy.LazyArray;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class StandardEmptyLazyArray<V>
        extends AbstractBaseLazyArray<V, StandardEmptyLazyReference<V>>
        implements EmptyLazyArray<V> {

    @SuppressWarnings("unchecked")
    public StandardEmptyLazyArray(int length) {
        super(IntStream.range(0, length)
                .mapToObj(i -> new StandardEmptyLazyReference<>())
                .toArray(StandardEmptyLazyReference[]::new));
    }

    @Override
    public V computeIfEmpty(int index,
                            IntFunction<? extends V> mappper) {
        Objects.requireNonNull(mappper);
        return lazyObjects[index]
                .apply(() -> mappper.apply(index));
    }
}
