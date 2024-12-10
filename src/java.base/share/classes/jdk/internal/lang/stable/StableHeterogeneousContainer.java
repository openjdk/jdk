package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A stable heterogeneous container that can associate types to implementing instances.
 * <p>
 * Attempting to associate a type with an instance that is {@code null} or attempting to
 * provide a {@code type} that is {@code null} will result in a {@linkplain NullPointerException}
 * for all methods in this class.
 *
 * @implNote Implementations of this interface are thread-safe
 */
public sealed interface StableHeterogeneousContainer {

    /**
     * {@return {@code true} if the provided {@code type} was associated with the provided
     * {@code instance}, {@code false} otherwise}
     * <p>
     * When this method returns, the provided {@code type} is always associated with an
     * instance.
     *
     * @param type     the class type of the instance to store
     * @param instance the instance to store
     * @throws ClassCastException       if the provided {@code instance} is not
     *                                  {@code null} and is not assignable to the type T
     * @throws IllegalArgumentException if the provided {@code type} is not a type
     *                                  specified at creation
     */
    <T> boolean tryPut(Class<T> type, T instance);

    /**
     * {@return the instance associated with the provided {@code type}, {@code null}
     * otherwise}
     *
     * @param type used to retrieve an associated instance
     * @param <T>  type of the associated instance
     * @throws ClassCastException       if the associated instance is not assignable to
     *                                  the type T
     * @throws IllegalArgumentException if the provided {@code type} is not a type
     *                                  specified at creation
     */
    <T> T get(Class<T> type);

    /**
     * {@return the instance associated with the provided {@code type}, or throws
     * NoSuchElementException}
     *
     * @param type used to retrieve an associated instance
     * @param <T>  type of the associated instance
     * @throws ClassCastException       if the associated instance is not assignable to
     *                                  the type T
     * @throws IllegalArgumentException if the provided {@code type} is not a type
     *                                  specified at creation
     * @throws NoSuchElementException   if the provided {@code type} is not associated
     *                                  with an instance
     */
    <T> T getOrThrow(Class<T> type);

    /**
     * {@return the instance associated with the provided {@code type}, if no association
     *          is made, first attempts to compute and associate an instance with the
     *          provided {@code type} using the provided {@code mapper}}
     * <p>
     * The provided {@code mapper} is guaranteed to be invoked at most once if it
     * completes without throwing an exception.
     * <p>
     * If the mapper throws an (unchecked) exception, the exception is rethrown, and no
     * association is made.
     * <p>
     * When this method returns, the provided {@code type} is always associated with an
     * instance.
     *
     * @param type   used to retrieve an associated instance
     * @param <T>    type of the associated instance
     * @param mapper to be used for computing the associated instance
     */
    <T> T computeIfAbsent(Class<T> type, Function<Class<T>, T> mapper);

    final class Impl implements StableHeterogeneousContainer {

        @Stable
        private final Map<Class<?>, StableValueImpl<Object>> map;

        public Impl(Set<Class<?>> types) {
            this.map = StableValueFactories.ofMap(types);
        }

        @ForceInline
        @Override
        public <T> boolean tryPut(Class<T> type, T instance) {
            Objects.requireNonNull(type);
            Objects.requireNonNull(instance, "The provided instance for '" + type + "' was null");
            return of(type)
                    .trySet(
                            type.cast(instance)
                    );
        }

        @SuppressWarnings("unchecked")
        @ForceInline
        @Override
        public <T> T computeIfAbsent(Class<T> type, Function<Class<T>, T> mapper) {
            Objects.requireNonNull(type);
            Objects.requireNonNull(mapper);
            final StableValue<Object> stableValue = of(type);
            if (stableValue.isSet()) {
                return (T) stableValue.orElseThrow();
            }
            return computeIfAbsentSlowPath(type, mapper, stableValue);
        }

        @SuppressWarnings("unchecked")
        @DontInline
        public <T> T computeIfAbsentSlowPath(Class<T> type,
                                             Function<Class<T>, T> constructor,
                                             StableValue<Object> stableValue) {
            return (T) stableValue.computeIfUnset(new Supplier<Object>() {
                @Override
                public Object get() {
                    return type.cast(
                            Objects.requireNonNull(
                                    constructor.apply(type),
                                    "The constructor for `" + type + "` returned null"
                            ));
                }
            });
        }

        @ForceInline
        @Override
        public <T> T get(Class<T> type) {
            Objects.requireNonNull(type);
            return type.cast(
                    of(type)
                            .orElse(null)
            );
        }

        @ForceInline
        @Override
        public <T> T getOrThrow(Class<T> type) {
            final T t = get(type);
            if (t == null) {
                throw new NoSuchElementException("The type `" + type + "` is know but there is no instance associated with it");
            }
            return t;
        }

        @ForceInline
        private StableValue<Object> of(Class<?> type) {
            final StableValue<Object> stableValue = map.get(type);
            if (stableValue == null) {
                throw new IllegalArgumentException("No such type: " + type);
            }
            return stableValue;
        }

        @Override
        public String toString() {
            return map.toString();
        }
    }

}
