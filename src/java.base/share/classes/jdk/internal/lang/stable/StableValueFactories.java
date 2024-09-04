package jdk.internal.lang.stable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public final class StableValueFactories {

    private StableValueFactories() {}

    // Factories

    public static <T> StableValueImpl<T> newInstance() {
        return StableValueImpl.newInstance();
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

    public static <T> Supplier<T> newCachingSupplier(Supplier<? extends T> original,
                                                     ThreadFactory factory) {

        final Supplier<T> caching = CachingSupplier.of(original);

        if (factory != null) {
            final Thread thread = factory.newThread(new Runnable() {
                @Override
                public void run() {
                    caching.get();
                }
            });
            thread.start();
        }
        return caching;
    }

    public static <R> IntFunction<R> newCachingIntFunction(int size,
                                                           IntFunction<? extends R> original,
                                                           ThreadFactory factory) {

        final IntFunction<R> caching = CachingIntFunction.of(size, original);

        if (factory != null) {
            for (int i = 0; i < size; i++) {
                final int input = i;
                final Thread thread = factory.newThread(new Runnable() {
                    @Override public void run() { caching.apply(input); }
                });
                thread.start();
            }
        }
        return caching;
    }

    public static <T, R> Function<T, R> newCachingFunction(Set<? extends T> inputs,
                                                           Function<? super T, ? extends R> original,
                                                           ThreadFactory factory) {

        final Function<T, R> caching = CachingFunction.of(inputs, original);

        if (factory != null) {
            for (final T t : inputs) {
                final Thread thread = factory.newThread(new Runnable() {
                    @Override public void run() { caching.apply(t); }
                });
                thread.start();
            }
        }
        return caching;
    }

}
