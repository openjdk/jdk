package java.lang;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

public final class Monotonics1b {

    private Monotonics1b() {}

    public static <T> Supplier<T> asMemoized(Monotonic<T> monotonic,
                                             Supplier<T> supplier) {
        return () -> monotonic.computeIfUnbound(supplier);
    }


    public static <V> IntFunction<V> asMemoized(List<Monotonic<V>> monotonicList,
                                                IntFunction<? extends V> mapper) {
        return i -> {
            Monotonic<V> monotonic = monotonicList.get(i);
            // Prevent capturing if possible
            if (monotonic.isBound()) {
                return monotonic.get();
            }
            Supplier<V> supplier = () -> mapper.apply(i); // Captures!
            return monotonic.computeIfUnbound(supplier);
        };
    }

    public static <K, V> Function<K, V> asMemoized(Map<K, Monotonic<V>> monotonicList,
                                                   Function<? super K, ? extends V> mapper) {
        return k -> {
            Monotonic<V> monotonic = monotonicList.get(k);
            // Prevent capturing if possible
            if (monotonic.isBound()) {
                return monotonic.get();
            }
            Supplier<V> supplier = () -> mapper.apply(k); // Captures!
            return monotonic.computeIfUnbound(supplier);
        };
    }

    public static void main(String[] args) {
        Monotonic<Integer> monotonicInt = Monotonic.of(int.class);
        Supplier<Integer> supplier = () -> 42;
        Supplier<Integer> memoizedSupplier = () -> monotonicInt.computeIfUnbound(supplier);
        Supplier<Integer> memoizedSupplier2 = asMemoized(monotonicInt, () -> 42);

        List<Monotonic<Integer>> monotonicIntList = Monotonic.ofList(int.class, 10);
        IntFunction<Integer> memoizedIntFunction = asMemoized(monotonicIntList, i -> i);

        Map<Integer, Monotonic<Integer>> monotonicIntMap = Monotonic.ofMap(int.class, IntStream.range(0, 10).boxed().toList());

        Function<Integer, Integer> memoizedFunction = asMemoized(monotonicIntMap, k -> k);
    }

}
