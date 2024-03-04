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

public final class Monotonics2a {

    private Monotonics2a() {}

    /**
     * {@return a new read-only Monotonic for which a bound non-null value can be
     * backed by the provided {@code backingType}}
     * <p>
     * It is up to to the implementation if the provided {@code backingType} is actually
     * used.
     * <p>
     * The returned Monotonic will throw an {@linkplain UnsupportedOperationException}
     * if any of the following methods are invoked:
     * <ul>
     *     <li>{@linkplain Monotonic#computeIfUnbound(MethodHandle)}</li>
     *     <li>{@linkplain Monotonic#computeIfUnbound(Supplier)}</li>
     *     <li>{@linkplain Monotonic#bindIfUnbound(Object)}</li>
     *     <li>{@linkplain Monotonic#bind(Object)}
     * </ul>
     *
     * @param backingType a class literal that (optionally) can be used to store a bound
     *                    value
     * @param supplier    supplier to invoke when a bound value is requested via the
     *                    {@linkplain Monotonic#get()} or via the
     *                    {@linkplain Monotonic#getter getter()'s} method handle}
     * @param <V>         the type to bind
     */
    public static <V> Monotonic<V> of(Class<V> backingType,
                               Supplier<? extends V> supplier) {
        Objects.requireNonNull(backingType);
        Objects.requireNonNull(supplier);
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a new immutable, lazy {@linkplain List } of read-only monotonic
     * elements where bound values can be backed by the provided
     * {@code backingElementType} and with a {@linkplain List#size()} equal to the
     * provided {@code size}}
     *
     * <p>
     * The Monotonic elements will throw an {@linkplain UnsupportedOperationException}
     * if any of the following methods are invoked:
     * <ul>
     *     <li>{@linkplain Monotonic#computeIfUnbound(MethodHandle)}</li>
     *     <li>{@linkplain Monotonic#computeIfUnbound(Supplier)}</li>
     *     <li>{@linkplain Monotonic#bindIfUnbound(Object)}</li>
     *     <li>{@linkplain Monotonic#bind(Object)}
     * </ul>
     * @param backingElementType a class literal that (optionally) can be used to store a
     *                           bound monotonic value
     * @param size               the size of the returned monotonic list
     * @param <V>                the type of the monotonic values in the returned list
     */
    public static <V> Monotonic.MonotonicList<V> ofList(Class<V> backingElementType,
                                                 int size,
                                                 IntFunction<? extends V> mapper) {
        Objects.requireNonNull(backingElementType);
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        Objects.requireNonNull(mapper);
        throw new UnsupportedOperationException();
    }

    /**
     * {@return a new immutable, lazy {@linkplain Monotonic.MonotonicMap } with read-only
     * monotonic values that can be backed by the provided {@code backingValueType} and where
     * the {@linkplain Map#keySet() keys} are the same as the provided {@code keys}}
     * <p>
     * The Monotonic values will throw an {@linkplain UnsupportedOperationException}
     * if any of the following methods are invoked:
     * <ul>
     *     <li>{@linkplain Monotonic#computeIfUnbound(MethodHandle)}</li>
     *     <li>{@linkplain Monotonic#computeIfUnbound(Supplier)}</li>
     *     <li>{@linkplain Monotonic#bindIfUnbound(Object)}</li>
     *     <li>{@linkplain Monotonic#bind(Object)}
     * </ul>
     * @param backingValueType a class literal that (optionally) can be used to store a
     *                         bound monotonic value
     * @param keys             the keys in the map
     * @param <K>              the type of keys maintained by the returned map
     * @param <V>              the type of the monotonic values in the returned map
     */
    static <K, V> Monotonic.MonotonicMap<K, V> ofMap(Class<V> backingValueType,
                                                     Collection<? extends K> keys,
                                                     Function<? super K, ? extends V> mapper) {
        Objects.requireNonNull(backingValueType);
        Objects.requireNonNull(keys);
        Objects.requireNonNull(mapper);
        throw new UnsupportedOperationException();
    }


    public static void main(String[] args) {
        Monotonic<Integer> monotonicInt = Monotonics2a.of(int.class, () -> 42);
        Supplier<Integer> memoizedSupplier = monotonicInt::get;

        List<Monotonic<Integer>> monotonicIntList = Monotonics2a.ofList(int.class, 10, i -> i);
        IntFunction<Integer> memoizedIntFunction = i -> monotonicIntList.get(i).get();

        Map<Integer, Monotonic<Integer>> monotonicIntMap = Monotonics2a.ofMap(int.class, IntStream.range(0, 10).boxed().toList(), k -> k);

        Function<Integer, Integer> memoizedFunction = k -> monotonicIntMap.get(k).get();
    }

}
