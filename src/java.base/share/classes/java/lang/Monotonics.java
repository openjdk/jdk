package java.lang;

import jdk.internal.lang.monotonic.InternalMonotonic;
import jdk.internal.lang.monotonic.InternalMonotonicList;
import jdk.internal.lang.monotonic.InternalMonotonicMap;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A collection of utility methods that makes it more convenient to use
 * {@linkplain Monotonic} values.
 */
public final class Monotonics {

    // Suppresses default constructor, ensuring non-instantiability.
    private Monotonics() {}

    /**
     * If no value {@linkplain Monotonic#isPresent()} for the provided {@code index}
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
     * @return the current (existing or computed) present monotonic value
     * @throws IllegalArgumentException if no association exists for the provided
     *                                  {@code key}.
     */
    public static <V> V computeIfAbsent(List<Monotonic<V>> list,
                                        int index,
                                        IntFunction<? extends V> mapper) {
        Objects.requireNonNull(list);
        Objects.checkIndex(index, list.size());
        Objects.requireNonNull(mapper);
        Monotonic<V> monotonic = list.get(index);
        if (monotonic.isPresent()) {
            return monotonic.get();
        }
        synchronized (mapper) {
            if (monotonic.isPresent()) {
                return monotonic.get();
            }
            Supplier<V> supplier = new Supplier<V>() {
                @Override
                public V get() {
                    return mapper.apply(index);
                }
            };
            return monotonic.computeIfAbsent(supplier);
        }
    }

    /**
     * If no value {@linkplain Monotonic#isPresent()} for the provided {@code key},
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
     * @return the current (existing or computed) present monotonic value
     * @throws IllegalArgumentException if no association exists for the provided
     *                                  {@code key}.
     */
    public static <K, V> V computeIfAbsent(Map<K, Monotonic<V>> map,
                                           K key,
                                           Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(map);
        Objects.requireNonNull(key);
        Objects.requireNonNull(mapper);
        Monotonic<V> monotonic = map.get(key);
        if (monotonic == null) {
            throw new IllegalArgumentException("No such key:" + key);
        }
        if (monotonic.isPresent()) {
            return monotonic.get();
        }
        synchronized (mapper) {
            if (monotonic.isPresent()) {
                return monotonic.get();
            }
            Supplier<V> supplier = new Supplier<V>() {
                @Override
                public V get() {
                    return mapper.apply(key);
                }
            };
            return monotonic.computeIfAbsent(supplier);
        }
    }

    /**
     * {@return a thread-safe, memoized supplier backed by a new empty monotonic value
     * where the memoized value is obtained by invoking the provided {@code suppler}}
     *
     * @param supplier   to be used for computing a value
     * @param background if true, spawns a virtual background thread that per-computes a
     *                   memoized value
     * @param <V>        the type of the value to memoize
     * @see Monotonic#computeIfAbsent(Supplier)
     */
    public static <V> Supplier<V> asMemoized(Supplier<? extends V> supplier,
                                             boolean background) {
        Objects.requireNonNull(supplier);
        return InternalMonotonic.asMemoized(supplier, background);
    }

    /**
     * {@return a thread-safe, memoized {@linkplain IntFunction} backed by a new list
     * of {@code size} empty monotonic elements where the memoized values is obtained by
     * invoking the provided {@code mapper}}
     *
     * @param size       the size of the backing monotonic list
     * @param mapper     to be used for computing values
     * @param background if true, spawns a virtual background thread that per-computes the
     *                   memoized values in an unspecified order
     * @param <V>        the value type for the Monotonic elements in the backing list
     * @see Monotonic#ofList(int)
     */
    public static <V> IntFunction<V> asMemoized(int size,
                                                IntFunction<? extends V> mapper,
                                                boolean background) {
        Objects.requireNonNull(mapper);
        return InternalMonotonicList.asMemoized(size, mapper, background);
    }

    /**
     * {@return a thread-safe, memoized {@linkplain Function} backed by a new map
     * with the {@code keys} and of empty monotonic elements where the memoized values
     * is obtained by invoking the provided {@code mapper}}
     *
     * @param keys       the keys in the backing map
     * @param mapper     to be used for computing values
     * @param background if true, spawns a virtual background thread that per-computes the
     *                   memoized values in order 0, 1, ... , size-1
     * @param <K>        the type of keys maintained by the backing map
     * @param <V>        the value type for the Monotonic values in the backing map
     * @see Monotonic#ofMap(Collection)
     */
    public static <K, V> Supplier<V> asMemoized(Collection<? extends K> keys,
                                                Function<? super K, ? extends V> mapper,
                                                boolean background) {
        Objects.requireNonNull(mapper);
        return InternalMonotonicMap.asMemoized(keys, mapper, background);
    }

}
