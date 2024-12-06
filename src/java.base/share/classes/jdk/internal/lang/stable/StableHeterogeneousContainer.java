package jdk.internal.lang.stable;

import jdk.internal.vm.annotation.Stable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A stable heterogeneous container that can associate types to implementing instances.
 * <p>
 * Attempting to associate a type with an instance that is {@code null} will result in
 * a {@linkplain NullPointerException}.
 *
 * @implNote Implementations of this interface are thread-safe

 */
public sealed interface StableHeterogeneousContainer {

    /**
     * {@return {@code true} if the provided {@code type} was associated with the
     *          provided {@code instance}, {@code false} otherwise}
     * <p>
     * When this method returns, the provided {@code type} is always associated with
     * an instance.
     *
     * @param type     the class type of the instance to store
     * @param instance the instance to store
     * @throws IllegalArgumentException if the provided {@code type} is not a type
     *         specified at creation
     */
    <T> boolean tryPut(Class<T> type, T instance);

    <T> T computeIfAbsent(Class<T> type, Function<Class<T>, T> constructor);

    /**
     * {@return the instance associated with the provided {@code type}, {@code null}
     *          otherwise}
     * @param type used to retrieve an associated instance
     * @param <T> type of the associated instance
     * @throws IllegalArgumentException if the provided {@code type} is not a type
     *         specified at creation
     */
    <T> T get(Class<T> type);

    final class Impl implements StableHeterogeneousContainer {

        @Stable
        private final Map<Class<?>, StableValueImpl<Object>> map;

        public Impl(Set<Class<?>> types) {
            this.map = StableValueFactories.ofMap(types);
        }

        @Override
        public <T> boolean tryPut(Class<T> type, T instance) {
            Objects.requireNonNull(type);
            Objects.requireNonNull(instance);
            return of(type)
                    .trySet(
                            type.cast(instance)
                    );
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T computeIfAbsent(Class<T> type, Function<Class<T>, T> constructor) {
            Objects.requireNonNull(type);
            Objects.requireNonNull(constructor);
            return (T) of(type).computeIfUnset(new Supplier<Object>() {
                @Override
                public Object get() {
                    return type.cast(
                            Objects.requireNonNull(
                                    constructor.apply(type)
                            ));
                }
            });
        }

        @Override
        public <T> T get(Class<T> type) {
            return type.cast(
                    of(type)
                            .orElse(null)
            );
        }

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
