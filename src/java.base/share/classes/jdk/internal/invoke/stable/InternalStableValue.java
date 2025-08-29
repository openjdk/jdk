package jdk.internal.invoke.stable;

import java.lang.invoke.StableValue;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

// Wrapper interface to allow internal implementations that are not public
public non-sealed interface InternalStableValue<T> extends StableValue<T> {

    T orElseSet(int input, FunctionHolder<?> functionHolder);

    T orElseSet(Object key, FunctionHolder<?> functionHolder);

    Object contentsPlain();

    Object contentsAcquire();

    boolean set(T newValue);

    @SuppressWarnings("unchecked")
    default T orElseSetSlowPath(final Object mutex,
                                final Object input,
                                final FunctionHolder<?> functionHolder) {
        preventReentry(mutex);
        synchronized (mutex) {
            final Object t = contentsPlain();  // Plain semantics suffice here
            if (t == null) {
                final T newValue;
                if (functionHolder == null) {
                    // If there is no functionHolder, the input must be a
                    // `Supplier` because we were called from `.orElseSet(Supplier)`
                    newValue = ((Supplier<T>) input).get();
                    Objects.requireNonNull(newValue);
                } else {
                    final Object u = functionHolder.function();
                    newValue = switch (u) {
                        case Supplier<?> sup -> (T) sup.get();
                        case IntFunction<?> iFun -> (T) iFun.apply((int) input);
                        case Function<?, ?> fun ->
                                ((Function<Object, T>) fun).apply(input);
                        default -> throw new InternalError("cannot reach here");
                    };
                    Objects.requireNonNull(newValue);
                    // Reduce the counter and if it reaches zero, clear the reference
                    // to the underlying holder.
                    functionHolder.countDown();
                }
                // The mutex is not reentrant so we know newValue should be returned
                set(newValue);
                return newValue;
            }
            return (T) t;
        }
    }

    default void preventReentry(Object mutex) {
        // This method is not annotated with @ForceInline as it is always called
        // in a slow path.
        if (Thread.holdsLock(mutex)) {
            throw new IllegalStateException("Recursive initialization of a stable value is illegal");
        }
    }

}
