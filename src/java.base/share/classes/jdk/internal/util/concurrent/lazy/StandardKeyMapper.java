package jdk.internal.util.concurrent.lazy;

import java.util.concurrent.lazy.KeyMapper;
import java.util.function.Function;

public record StandardKeyMapper<K, V>(K key,
                                      Function<? super K, ? extends V> mapper)
        implements KeyMapper<K, V> {}
