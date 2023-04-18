package java.util.concurrent.lazy;

import jdk.internal.javac.PreviewFeature;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Base interface for lazy references , which are ... // Todo: write more here
 *
 * @param <V> The type of the value to be recorded
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public interface BaseLazyReference<V> {

    /**
     * {@return The {@link Lazy.State } of this Lazy}.
     * <p>
     * The value is a snapshot of the current State.
     * No attempt is made to compute a value if it is not already present.
     * <p>
     * If the returned State is either {@link Lazy.State#PRESENT} or
     * {@link Lazy.State#ERROR}, it is guaranteed the state will
     * never change in the future.
     * <p>
     * This method can be used to act on a value if it is present:
     * {@snippet lang = java:
     *     if (lazy.state() == State.PRESENT) {
     *         // perform action on the value
     *     }
     *}
     */
    public Lazy.State state();

    /**
     * {@return the excption thrown by the supplier invoked or
     * {@link Optional#empty()} if no exception was thrown}.
     */
    public Optional<Throwable> exception();

    /**
     * {@return the value if the value is {@link Lazy.State#PRESENT}
     * or {@code defaultValue} if the value is {@link Lazy.State#EMPTY} or {@link Lazy.State#CONSTRUCTING}}.
     *
     * @param defaultValue to use if no value is present
     * @throws NoSuchElementException if a provider has previously thrown an exception.
     */
    V getOr(V defaultValue);

    /**
     * A builder that can be used to configure a LazyReference.
     *
     * @param <V> the type of the value.
     * @param <L> the lazy reference type to build
     * @param <B> the builder type
     */
    @PreviewFeature(feature = PreviewFeature.Feature.LAZY)
    public interface Builder<V, L extends BaseLazyReference<V>, B extends Builder<V, L, B>> {

        /**
         * {@return a builder that will use the provided eagerly computed {@code value} when
         * eventially {@linkplain #build() building} a lazy reference}.
         *
         * @param value to use
         */
        B withValue(V value);

        /**
         * {@return a new lazy reference with the builder's configured setting}.
         */
        L build();
    }

}
