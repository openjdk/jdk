package jdk.internal.util.concurrent.lazy;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReferenceArray;
import java.util.concurrent.lazy.playground.DemoMapObject;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

class AbstractMapper<K, V> {

    protected final Map<K, Integer> keyToInt;
    protected final LazyReferenceArray<V> lazyArray;

    protected <T> AbstractMapper(Stream<T> keyHolders,
                                 Function<T, K> keyExtractor) {
        AtomicInteger cnt = new AtomicInteger();
        this.keyToInt = keyHolders
                .collect(toMap(keyExtractor, k -> cnt.getAndIncrement()));
        this.lazyArray = Lazy.ofEmptyArray(keyToInt.size());
    }
}
