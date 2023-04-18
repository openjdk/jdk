package jdk.internal.util.concurrent.lazy.array;

import jdk.internal.util.concurrent.lazy.StandardEmptyLazyReference;

import java.util.Objects;
import java.util.concurrent.lazy.EmptyLazyArray;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

public final class TranslatedEmptyLazyArray<V>
        extends AbstractBaseLazyArray<V, StandardEmptyLazyReference<V>>
        implements EmptyLazyArray<V> {

    private final int factor;

    @SuppressWarnings("unchecked")
    public TranslatedEmptyLazyArray(int length,
                                    int factor) {
        super(IntStream.range(0, length)
                .mapToObj(i -> new StandardEmptyLazyReference<>())
                .toArray(StandardEmptyLazyReference[]::new));
        this.factor = factor;
    }

    @Override
    public V computeIfEmpty(int index,
                            IntFunction<? extends V> mappper) {
        Objects.requireNonNull(mappper);
        if (index % factor == 0) {
            int translatedIndex = index / factor;
            return lazyObjects[translatedIndex]
                    .apply(() -> mappper.apply(index));
        }
        return mappper.apply(index);
    }

}
