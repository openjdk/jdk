package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.ForceInline;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

// Note: It would be possible to just use `LazyMap::get` with some additional logic
// instead of this class but explicitly providing a class like this provides better
// debug capability, exception handling, and may provide better performance.
public record CachedFunction<T, R>(Map<T, StableValueImpl<R>> stables,
                                   Function<? super T, ? extends R> original) implements Function<T, R> {
    @ForceInline
    @Override
    public R apply(T value) {
        final StableValueImpl<R> stable = stables.get(value);
        if (stable == null) {
            throw new IllegalArgumentException("Input not allowed: " + value);
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

    public static <T, R> CachedFunction<T, R> of(Set<T> inputs,
                                                 Function<? super T, ? extends R> original) {
        return new CachedFunction<>(StableValueImpl.ofMap(inputs), original);
    }

}
