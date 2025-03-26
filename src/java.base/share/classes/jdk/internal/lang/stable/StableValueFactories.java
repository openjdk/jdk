package jdk.internal.lang.stable;

import jdk.internal.access.SharedSecrets;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public final class StableValueFactories {

    private StableValueFactories() {}

    // Factories

    public static <T> StableValueImpl<T> of() {
        return StableValueImpl.newInstance();
    }

    public static <T> StableValueImpl<T> of(T value) {
        final StableValueImpl<T> stableValue = of();
        stableValue.trySet(value);
        return stableValue;
    }

    public static <T> Supplier<T> supplier(Supplier<? extends T> original) {
        return StableSupplier.of(original);
    }

    public static <R> IntFunction<R> intFunction(int size,
                                                 IntFunction<? extends R> original) {
        return StableIntFunction.of(size, original);
    }

    public static <T, R> Function<T, R> function(Set<? extends T> inputs,
                                                 Function<? super T, ? extends R> original) {
        return inputs instanceof EnumSet<?> && !inputs.isEmpty()
                ? StableEnumFunction.of(inputs, original)
                : StableFunction.of(inputs, original);
    }

    public static <E> List<E> list(int size, IntFunction<? extends E> mapper) {
        return SharedSecrets.getJavaUtilCollectionAccess().stableList(size, mapper);
    }

    public static <K, V> Map<K, V> map(Set<K> keys, Function<? super K, ? extends V> mapper) {
        return SharedSecrets.getJavaUtilCollectionAccess().stableMap(keys, mapper);
    }

    // Supporting methods

    public static <T> StableValueImpl<T>[] array(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        @SuppressWarnings("unchecked")
        final var stableValues = (StableValueImpl<T>[]) new StableValueImpl<?>[size];
        for (int i = 0; i < size; i++) {
            stableValues[i] = StableValueImpl.newInstance();
        }
        return stableValues;
    }

    public static <K, T> Map<K, StableValueImpl<T>> map(Set<K> keys) {
        Objects.requireNonNull(keys);
        @SuppressWarnings("unchecked")
        final var entries = (Map.Entry<K, StableValueImpl<T>>[]) new Map.Entry<?, ?>[keys.size()];
        int i = 0;
        for (K key : keys) {
            entries[i++] = Map.entry(key, StableValueImpl.newInstance());
        }
        return Map.ofEntries(entries);
    }

}
