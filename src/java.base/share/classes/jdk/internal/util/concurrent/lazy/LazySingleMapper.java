package jdk.internal.util.concurrent.lazy;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReferenceArray;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

public final class LazySingleMapper<K, V>
        extends AbstractMapper<K, V>
        implements Function<K, Optional<V>> {

    private final Function<? super K, ? extends V> mapper;

    public LazySingleMapper(Collection<K> keys,
                            Function<? super K, ? extends V> mapper) {
        super(keys.stream(), Function.identity());
        Objects.requireNonNull(mapper);
        this.mapper = mapper;
    }

    @Override
    public Optional<V> apply(K key) {
        return Optional.ofNullable(keyToInt.get(key))
                .map(i -> lazyArray.computeIfEmpty(i, k2 -> mapper.apply(key)));
    }

}