package java.util.concurrent.lazy;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.util.concurrent.lazy.StandardKeyMapper;

import java.util.Objects;
import java.util.function.Function;

/**
 * This class represents an association between a key
 * of type K and a mapper that can compute an associated
 * value of type V at a later time.
 *
 * @param <K>    the type of the key maintained by this association
 * @param <V>    the type of mapped values
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public sealed interface KeyMapper<K, V>
    permits StandardKeyMapper {

    /**
     * {@return the key for this KeyMapper for
     * the associated {@linkplain #mapper() mapper}}.
     */
    K key();

    /**
     * {@return the mapper for this KeyMapper to be applied for
     * the associated {@linkplain #key key} at a later time}.
     */
    Function<? super K, ? extends V> mapper();

    /**
     * {@return a new KeyMapper for the provided {@code key}/{@code mapper} association}.
     *
     * @param <K>    the type of the key maintained by this association
     * @param <V>    the type of mapped values
     * @param key    to associate to a mapper
     * @param mapper to associate to a key and to be applied for the key at a later time
     */
    public static <K, V> KeyMapper<K, V> of(K key,
                                            Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(mapper);
        return new StandardKeyMapper<>(key, mapper);
    }

}
