package jdk.internal.lang.stable;

import java.util.EnumSet;
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

    public static <T> Supplier<T> ofSupplier(Supplier<? extends T> original) {
        return StableSupplier.of(original);
    }

    public static <R> IntFunction<R> ofIntFunction(int size,
                                                   IntFunction<? extends R> original) {
        return StableIntFunction.of(size, original);
    }

    public static <T, R> Function<T, R> ofFunction(Set<? extends T> inputs,
                                                   Function<? super T, ? extends R> original) {
        if (inputs.isEmpty()) {
            return EmptyStableFunction.of(original);
        }
        return inputs instanceof EnumSet<?>
                ? StableEnumFunction.of(inputs, original)
                : StableFunction.of(inputs, original);
    }

    @SuppressWarnings("unchecked")
    private static <T, R extends Enum<R>> EnumSet<R> asEnumSet(Set<? extends T> original) {
        return (EnumSet<R>) original;
    }

    public static <T extends Enum<T>, R> Function<T, R> newCachingEnumFunction(EnumSet<T> inputs,
                                                                               Function<? super T, ? extends R> original) {

        return StableEnumFunction.of(inputs, original);
    }

    public static <T> StableValueImpl<T>[] ofArray(int size) {
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

    public static <K, T> Map<K, StableValueImpl<T>> ofMap(Set<K> keys) {
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
