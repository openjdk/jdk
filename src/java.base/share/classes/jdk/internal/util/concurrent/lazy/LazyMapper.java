package jdk.internal.util.concurrent.lazy;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.lazy.KeyMapper;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReferenceArray;
import java.util.concurrent.lazy.playground.DemoMapObject;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

public final class LazyMapper<K, V>
        extends AbstractMapper<K, V>
        implements Function<K, Optional<V>> {

    private final Map<K, Function<? super K, ? extends V>> mappers;

    public LazyMapper(Collection<KeyMapper<K, V>> keyMappers) {
        super(keyMappers.stream(), KeyMapper::key);
        this.mappers = keyMappers.stream()
                .collect(toMap(KeyMapper::key, lms -> lms.mapper()));
    }

    @Override
    public Optional<V> apply(K key) {
        return Optional.ofNullable(keyToInt.get(key))
                .map(i -> lazyArray.computeIfEmpty(i, k2 -> mappers.get(key).apply(key)));
    }

}
