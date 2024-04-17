package jdk.internal.lang.stable;

import jdk.internal.access.JavaUtilCollectionAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.lang.StableValue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public final class StableAccess {

    public StableAccess() {}

    private static final JavaUtilCollectionAccess ACCESS =
            SharedSecrets.getJavaUtilCollectionAccess();

    public static <V> List<StableValue<V>> ofList(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        if (size == 0) {
            return List.of();
        }
        return ACCESS.stableList(size);
    }

    public static <V> V computeIfUnset(List<StableValue<V>> list,
                                       int index,
                                       IntFunction<? extends V> mapper) {
        return ACCESS.computeIfUnset(list, index, mapper);
    }

    public static <K, V> Map<K, StableValue<V>> ofMap(Set<? extends K> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        return ACCESS.stableMap(keys);
    }

    public static <K, V> V computeIfUnset(Map<K, StableValue<V>> map,
                                          K key,
                                          Function<? super K, ? extends V> mapper) {
        return ACCESS.computeIfUnset(map, key, mapper);
    }

    // Records supports out-of-the-box trusted fields and so, we use them to implement
    // memoized constructs.

    public record MemoizedSupplier<T>(StableValue<T> stable,
                                      Supplier<? extends T> original) implements Supplier<T> {
        @Override
        public T get() {
            return stable.computeIfUnset(original);
        }
    }

    public record MemoizedIntFunction<T>(List<StableValue<T>> stableList,
                                         IntFunction<? extends T> original) implements IntFunction<T> {
        @Override
        public T apply(int value) {
            return StableValue.computeIfUnset(stableList, value, original);
        }
    }

    public record MemoizedFunction<T, R>(Map<T, StableValue<R>> stableMap,
                                         Function<? super T, ? extends R> original) implements Function<T, R> {
        @Override
        public R apply(T t) {
            return StableValue.computeIfUnset(stableMap, t, original);
        }
    }

}
