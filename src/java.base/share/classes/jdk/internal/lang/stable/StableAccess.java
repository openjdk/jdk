package jdk.internal.lang.stable;

import jdk.internal.ValueBased;
import jdk.internal.access.JavaUtilCollectionAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.lang.StableValue;
import jdk.internal.vm.annotation.Stable;

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

    public static <T> Supplier<T> ofSupplier(StableValue<T> stable,
                                             Supplier<? extends T> original) {
        return new MemoizedSupplier<>(stable, original);
    }

    public static <R> IntFunction<R> ofIntFunction(List<StableValue<R>> stableList,
                                                   IntFunction<? extends R> original) {
        return new MemoizedIntFunction<>(stableList, original);
    }

    public static <T, R> Function<T, R> ofFunction(Map<T, StableValue<R>> stableMap,
                                                   Function<? super T, ? extends R> original) {
        return new MemoizedFunction<>(stableMap, original);
    }

    @ValueBased
    private static final class MemoizedSupplier<T> implements Supplier<T> {

        @Stable private final StableValue<T> stable;
        @Stable private final Supplier<? extends T> original;

        private MemoizedSupplier(StableValue<T> stable,
                                 Supplier<? extends T> original) {
            this.stable = stable;
            this.original = original;
        }

        @Override
        public T get() {
            return stable.computeIfUnset(original);
        }

        @Override
        public String toString() {
            return "MemoizedSupplier[" +
                    "stable=" + stable + ", " +
                    "original=" + original + ']';
        }


    }

    @ValueBased
    private static final class MemoizedIntFunction<R> implements IntFunction<R> {

        @Stable private final List<StableValue<R>> stableList;
        @Stable private final IntFunction<? extends R> original;

        private MemoizedIntFunction(List<StableValue<R>> stableList,
                                   IntFunction<? extends R> original) {
            this.stableList = stableList;
            this.original = original;
        }

        @Override
            public R apply(int value) {
                return StableValue.computeIfUnset(stableList, value, original);
            }

        @Override
        public String toString() {
            return "MemoizedIntFunction[" +
                    "stableList=" + stableList + ", " +
                    "original=" + original + ']';
        }

    }

    @ValueBased
    private static final class MemoizedFunction<T, R> implements Function<T, R> {

        @Stable private final Map<T, StableValue<R>> stableMap;
        @Stable private final Function<? super T, ? extends R> original;

        private MemoizedFunction(Map<T, StableValue<R>> stableMap,
                                 Function<? super T, ? extends R> original) {
            this.stableMap = stableMap;
            this.original = original;
        }

        @Override
        public R apply(T t) {
            return StableValue.computeIfUnset(stableMap, t, original);
        }

        @Override
        public String toString() {
            return "MemoizedFunction[" +
                    "stableMap=" + stableMap + ", " +
                    "original=" + original + ']';
        }

    }

}
