package java.lang;

import java.lang.invoke.MethodHandle;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public final class Monotonics2b {

    private Monotonics2b() {
    }

    /**
     * {@return a memoized Supplier}
     *
     * @param backingType a class literal that (optionally) can be used to store a bound
     *                    value
     * @param supplier    supplier to invoke when a bound value is first requested via the
     *                    returned Supplier's {@linkplain Supplier#get} method
     * @param <V>         the type to bind
     */
    public static <V> Supplier<V> ofMemoized(Class<V> backingType,
                                             Supplier<? extends V> supplier) {
        Objects.requireNonNull(backingType);
        Objects.requireNonNull(supplier);
        return Monotonics2a.of(backingType, supplier)::get;
    }

    /**
     * {@return a new immutable, lazy {@linkplain List } of read-only monotonic elements
     * where bound values can be backed by the provided {@code backingElementType} and
     * with a {@linkplain List#size()} equal to the provided {@code size}}
     *
     * <p>
     * The Monotonic elements will throw an {@linkplain UnsupportedOperationException} if
     * any of the following methods are invoked:
     * <ul>
     *     <li>{@linkplain Monotonic#computeIfUnbound(MethodHandle)}</li>
     *     <li>{@linkplain Monotonic#computeIfUnbound(Supplier)}</li>
     *     <li>{@linkplain Monotonic#bindIfUnbound(Object)}</li>
     *     <li>{@linkplain Monotonic#bind(Object)}
     * </ul>
     *
     * @param backingElementType a class literal that (optionally) can be used to store a
     *                           bound monotonic value
     * @param size               the size of the returned monotonic list
     * @param <V>                the type of the monotonic values in the returned list
     */
    public static <V> IntFunction<V> ofMemoized(Class<V> backingElementType,
                                                int size,
                                                IntFunction<? extends V> mapper) {
        Objects.requireNonNull(backingElementType);
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(mapper);
        // Create a non-capturing list instance
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a new immutable, lazy {@linkplain Monotonic.MonotonicMap } with read-only
     * monotonic values that can be backed by the provided {@code backingValueType} and
     * where the {@linkplain Map#keySet() keys} are the same as the provided
     * {@code keys}}
     * <p>
     * The Monotonic values will throw an {@linkplain UnsupportedOperationException} if
     * any of the following methods are invoked:
     * <ul>
     *     <li>{@linkplain Monotonic#computeIfUnbound(MethodHandle)}</li>
     *     <li>{@linkplain Monotonic#computeIfUnbound(Supplier)}</li>
     *     <li>{@linkplain Monotonic#bindIfUnbound(Object)}</li>
     *     <li>{@linkplain Monotonic#bind(Object)}
     * </ul>
     *
     * @param backingValueType a class literal that (optionally) can be used to store a
     *                         bound monotonic value
     * @param keys             the keys in the map
     * @param <K>              the type of keys maintained by the returned map
     * @param <V>              the type of the monotonic values in the returned map
     */
    static <K, V> Function<K, V> ofMemoized(Class<V> backingValueType,
                                            Collection<? extends K> keys,
                                            Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(backingValueType);
        Objects.requireNonNull(keys);
        Objects.requireNonNull(mapper);
        // Create a non-capturing list instance
        throw new UnsupportedOperationException();
    }


    public static void main(String[] args) {
        Supplier<Integer> memoizedSupplier = ofMemoized(int.class, () -> 42);
        IntFunction<Integer> memoizedIntFunction = ofMemoized(int.class, 10, i -> i);
        Function<Integer, Integer> memoizedFunction = ofMemoized(int.class, IntStream.range(0, 10).boxed().toList(), k -> k);
    }


    public interface ImprovedFunction<T, R> extends Function<T, R> {

        default Function<T, R> toMemoized(Class<R> backingValueType,
                                          Collection<? extends T> keys) {
            return ofMemoized(backingValueType, keys, this);
        }

    }

}
