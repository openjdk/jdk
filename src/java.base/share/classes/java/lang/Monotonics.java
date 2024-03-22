package java.lang;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.javac.PreviewFeature.Feature;
import jdk.internal.lang.monotonic.MonotonicImpl;
import jdk.internal.lang.monotonic.MonotonicList;
import jdk.internal.lang.monotonic.MonotonicMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A collection of utility methods that makes it more convenient to use
 * {@linkplain Monotonic} values.
 */
@PreviewFeature(feature = Feature.MONOTONIC_VALUES)
public final class Monotonics {

    // Suppresses default constructor, ensuring non-instantiability.
    private Monotonics() {}

    /**
     * {@return a new Monotonic with an unbound value where the returned monotonic's
     * value is computed in a separate fresh background thread using the provided
     * (@code supplier}}
     * <p>
     * If the supplier throws an (unchecked) exception, the exception is ignored, and no
     * value is bound.
     *
     * @param <V>      the value type to bind
     * @param supplier to be used for computing a value
     * @see Monotonic#of
     */
    public static <V> Monotonic<V> ofBackground(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        Monotonic<V> monotonic = Monotonic.of();
        Thread.ofVirtual()
                .start(() -> {
                    try {
                        monotonic.computeIfUnbound(supplier);
                    } catch (Exception _) {}
                });
        return monotonic;
    }

    /**
     * If no value {@linkplain Monotonic#isBound()} for the provided {@code index}
     * in the provided {@code list}, attempts to compute and bind a value using the
     * provided {@code mapper}, returning the (pre-existing or newly bound) value.
     * <p>
     * If the mapper throws an (unchecked) exception, the exception is rethrown, and no
     * value is bound.
     *
     * @param list   from which to extract monotonic values
     * @param index  to inspect
     * @param mapper to be used for computing a value
     * @param <V>    the value type for the Monotonic elements in the list
     * @return the current (existing or computed) bound monotonic value
     * @throws IndexOutOfBoundsException if the provided {@code index} is less than
     *                                  zero or equal or greater than the list.size()
     */
    public static <V> V computeIfUnbound(List<Monotonic<V>> list,
                                         int index,
                                         IntFunction<? extends V> mapper) {
        Objects.requireNonNull(list);
        Objects.checkIndex(index, list.size());
        Objects.requireNonNull(mapper);
        return MonotonicList.computeIfUnbound(list, index, mapper);
    }

    /**
     * If no value {@linkplain Monotonic#isBound()} for the provided {@code key},
     * in the provided {@code map}, attempts to compute and bind a value using the
     * provided {@code mapper}, returning the (pre-existing or newly bound) value.
     * <p>
     * If the mapper throws an (unchecked) exception, the exception is rethrown, and no
     * value is bound.
     *
     * @param map    from which to extract monotonic values
     * @param key    to inspect
     * @param mapper to be used for computing a value
     * @param <K>    the type of keys maintained by the map
     * @param <V>    the value type for the Monotonic elements in the map
     * @return the current (existing or computed) bound monotonic value
     * @throws IllegalArgumentException if no association exists for the provided
     *                                  {@code key}.
     */
    public static <K, V> V computeIfUnbound(Map<K, Monotonic<V>> map,
                                            K key,
                                            Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(map);
        Objects.requireNonNull(key);
        Objects.requireNonNull(mapper);
        return MonotonicMap.computeIfUnbound(map, key, mapper);
    }

    /**
     * {@return a wrapped. thread-safe, memoized supplier backed by a new empty
     * monotonic value where the memoized value is obtained by invoking the provided
     * {@code suppler} at most once}
     * <p>
     * The returned memoized {@code Supplier} is equivalent to the following supplier:
     * {@snippet lang=java :
     * Monotonic<V> monotonic = Monotonic.of();
     * Supplier<V> memoized = () -> monotonic.computeIfUnbound(supplier);
     * }
     * except it promises the provided {@code supplier} is invoked once even
     * though the returned memoized Supplier is invoked simultaneously
     * by several threads.
     *
     * @param supplier   to be used for computing a value
     * @param <V>        the type of the value to memoize
     * @see Monotonic#computeIfUnbound(Supplier)
     */
    public static <V> Supplier<V> asSupplier(Supplier<? extends V> supplier) {
        Objects.requireNonNull(supplier);
        return MonotonicImpl.asMemoized(supplier);
    }

    /**
     * {@return a wrapped, thread-safe, memoized {@linkplain IntFunction} backed by a
     * new list of {@code size} empty monotonic elements where the memoized values are
     * obtained by invoking the provided {@code mapper} at most once per index}
     * <p>
     * The returned memoized {@linkplain  IntFunction} is equivalent to
     * the following {@linkplain  IntFunction}:
     *
     * {@snippet lang = java:
     * List<Monotonic<R>> list = Monotonic.ofList(size);
     * IntFunction<R> memoized = index -> computeIfUnbound(list, index, mapper);
     *}
     * except it promises the provided {@code mapper} is invoked only once per index
     * even though the returned memoized IntFunction is invoked simultaneously by several
     * threads using the same index.
     *
     * @param size   the size of the backing monotonic list
     * @param mapper to be used for computing values
     * @param <R>    the type of the result of the function (and the value type for the
     *               Monotonic elements in the backing list)
     * @see Monotonic#ofList(int)
     */
    public static <R> IntFunction<R> asIntFunction(int size,
                                                   IntFunction<? extends R> mapper) {
        Objects.requireNonNull(mapper);
        return MonotonicList.asMemoized(size, mapper);
    }

    /**
     * {@return a wrapped, thread-safe, memoized {@linkplain Function} backed by a
     * new map with the {@code keys} and of empty monotonic elements where the memoized
     * values are obtained by invoking the provided {@code mapper} at most once per key}
     * <p>
     * The returned memoized {@linkplain Function} is equivalent to
     * the following {@linkplain Function}:
     *
     * {@snippet lang = java:
     * Map<T, Monotonic<R>> map = Monotonic.ofMap(keys);
     * Function<T, R> memoized = key -> computeIfUnbound(map, key, mapper);
     *}
     * except it promises the provided {@code mapper} is invoked only once per key
     * even though the returned memoized Function is invoked simultaneously by several
     * threads using the same key.
     *
     * @param keys       the keys in the backing map (enumerating all the possible
     *                   input values to the returned memoized function)
     * @param mapper     to be used for computing values
     * @param <T>        the type of the input to the function (and keys maintained by
     *                   the backing map)
     * @param <R>        the type of the result of the function (and the Monotonic values
     *                   in the backing map)
     * @see Monotonic#ofMap(Set)
     */
    public static <T, R> Function<T, R> asFunction(Collection<? extends T> keys,
                                                   Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(keys);
        Objects.requireNonNull(mapper);
        return MonotonicMap.asMemoized(keys, mapper);
    }

}
