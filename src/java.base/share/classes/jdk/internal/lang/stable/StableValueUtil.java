package jdk.internal.lang.stable;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public final class StableValueUtil {

    private StableValueUtil() {}

    // Unsafe allows StableValue to be used early in the boot sequence
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    // Used to indicate a holder value is `null` (see field `value` below)
    // A wrapper method `nullSentinel()` is used for generic type conversion.
    static final Object NULL_SENTINEL = new Object();

    // Wraps `null` values into a sentinel value
    @ForceInline
    static <T> T wrap(T t) {
        return (t == null) ? nullSentinel() : t;
    }

    // Unwraps null sentinel values into `null`
    @ForceInline
    public static <T> T unwrap(T t) {
        return t != nullSentinel() ? t : null;
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    static <T> T nullSentinel() {
        return (T) NULL_SENTINEL;
    }

    static <T> String render(T t) {
        return (t == null) ? ".unset" : "[" + unwrap(t) + "]";
    }

    @ForceInline
    static boolean cas(Object o, long offset, Object value) {
        // This upholds the invariant, a `@Stable` field is written to at most once
        // and implies release semantics.
        return UNSAFE.compareAndSetReference(o, offset, null, wrap(value));
    }

    @SuppressWarnings("unchecked")
    @ForceInline
    static <T> T getAcquire(Object o, long offset) {
        return (T) UNSAFE.getReferenceAcquire(o, offset);
    }

    @ForceInline
    static long arrayOffset(int index) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + (long) index * Unsafe.ARRAY_OBJECT_INDEX_SCALE;
    }

    // Factories

    public static <T> List<StableValueImpl<T>> ofList(int size) {
        if (size < 0) {
            throw new IllegalArgumentException();
        }
        @SuppressWarnings("unchecked")
        final var stableValues = (StableValueImpl<T>[]) new StableValueImpl<?>[size];
        for (int i = 0; i < size; i++) {
            stableValues[i] = StableValueImpl.newInstance();
        }
        return List.of(stableValues);
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

        final Supplier<T> memoized = CachingSupplier.of(original);

        if (factory != null) {
            final Thread thread = factory.newThread(new Runnable() {
                @Override
                public void run() {
                    memoized.get();
                }
            });
            thread.start();
        }
        return memoized;
    }

    public static <R> IntFunction<R> newCachingIntFunction(int size,
                                                           IntFunction<? extends R> original,
                                                           ThreadFactory factory) {

        final IntFunction<R> memoized = CachingIntFunction.of(size, original);

        if (factory != null) {
            for (int i = 0; i < size; i++) {
                final int input = i;
                final Thread thread = factory.newThread(new Runnable() {
                    @Override public void run() { memoized.apply(input); }
                });
                thread.start();
            }
        }
        return memoized;
    }

    public static <T, R> Function<T, R> newCachingFunction(Set<? extends T> inputs,
                                                    Function<? super T, ? extends R> original,
                                                    ThreadFactory factory) {

        final Function<T, R> memoized = CachingFunction.of(inputs, original);

        if (factory != null) {
            for (final T t : inputs) {
                final Thread thread = factory.newThread(new Runnable() {
                    @Override public void run() { memoized.apply(t); }
                });
                thread.start();
            }
        }
        return memoized;
    }

}
