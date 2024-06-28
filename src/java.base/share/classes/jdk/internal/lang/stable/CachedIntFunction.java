package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.ForceInline;

import java.util.List;
import java.util.function.IntFunction;

// Note: It would be possible to just use `LazyList::get` instead of this
// class but explicitly providing a class like this provides better
// debug capability, exception handling, and may provide better performance.
public record CachedIntFunction<R>(List<StableValueImpl<R>> stables,
                                   IntFunction<? extends R> original) implements IntFunction<R> {
    @ForceInline
    @Override
    public R apply(int value) {
        final StableValueImpl<R> stable;
        try {
            // Todo: Will the exception handling here impair performance?
            stable = stables.get(value);
        } catch (IndexOutOfBoundsException e) {
            throw new IllegalArgumentException(e);
        }
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
