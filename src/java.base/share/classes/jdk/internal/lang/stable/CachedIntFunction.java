package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.ForceInline;

import java.util.List;
import java.util.function.IntFunction;

public record CachedIntFunction<R>(List<StableValueImpl<R>> stables,
                                   IntFunction<? extends R> original) implements IntFunction<R> {
    @ForceInline
    @Override
    public R apply(int value) {
        final StableValueImpl<R> stable = stables.get(value);
        R r = stable.value();
        if (r != null) {
            return StableValueImpl.unwrap(r);
        }
        synchronized (stable) {
            r = stable.value();
            if (r != null) {
                return StableValueImpl.unwrap(r);
            }
            r = original.apply(value);
            stable.setOrThrow(r);
        }
        return r;
    }

    public static <R> CachedIntFunction<R> of(int size, IntFunction<? extends R> original) {
        return new CachedIntFunction<>(StableValueImpl.ofList(size), original);
    }

}
