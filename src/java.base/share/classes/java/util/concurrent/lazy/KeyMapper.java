package java.util.concurrent.lazy;

import java.util.function.Function;

/**
 * This class represents a binding between a key
 * of type K and a mapper that can compute an associated
 * value of type V at a later time.
 *
 * @param key    to bind to a mapper
 * @param mapper to be applied for the key at a later time
 * @param <K>    the type of the key maintained by this association
 * @param <V>    the type of mapped values
 */
public record KeyMapper<K, V>(
        K key,
        Function<? super K, ? extends V> mapper) {
}
