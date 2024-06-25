package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.ForceInline;

import java.util.function.Supplier;

public record CachedSupplier<T>(StableValueImpl<T> stable,
                                Supplier<? extends T> original) implements Supplier<T> {
    @ForceInline
    @Override
    public T get() {
        T t = stable.value();
        if (t != null) {
            return StableValueImpl.unwrap(t);
        }
        synchronized (stable) {
            t = stable.value();
            if (t != null) {
                return StableValueImpl.unwrap(t);
            }
            t = original.get();
            stable.setOrThrow(t);
        }
        return t;
    }

    public static <T> CachedSupplier<T> of(Supplier<? extends T> original) {
        return new CachedSupplier<>(StableValueImpl.newInstance(), original);
    }

}
