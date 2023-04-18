package jdk.internal.util.concurrent.lazy.array;

import jdk.internal.util.concurrent.lazy.StandardEmptyLazyReference;
import jdk.internal.util.concurrent.lazy.StandardLazyReference;

import java.util.concurrent.lazy.BaseLazyArray;
import java.util.concurrent.lazy.BaseLazyReference;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyArray;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public final class StandardLazyArray<V>
        extends AbstractBaseLazyArray<V, StandardLazyReference<V>>
        implements LazyArray<V> {

    @SuppressWarnings("unchecked")
    public StandardLazyArray(int length,
                             IntFunction<? extends V> presetMapper) {

        super(IntStream.range(0, length)
                .mapToObj(i -> new StandardLazyReference<>(() -> presetMapper.apply(i)))
                .toArray(StandardLazyReference[]::new));
    }

    @Override
    public V apply(int index) {
        return lazyObjects[index].get();
    }

    @Override
    public void force() {
        for (int i = 0; i < length(); i++) {
            lazyObjects[i].get();
        }
    }

}
