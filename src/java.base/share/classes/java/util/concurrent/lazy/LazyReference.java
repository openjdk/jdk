package java.util.concurrent.lazy;

import jdk.internal.javac.PreviewFeature;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * A lazy references with a pre-set supplier...
 *
 * @param <V> The type of the value to be recorded
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public interface LazyReference<V>
        extends BaseLazyReference<V>, Supplier<V> {

    /**
     * Returns the present value or, if no present value exists, atomically attempts
     * to compute the value using the <em>pre-set {@linkplain Lazy#of(Supplier)} supplier}</em>.
     * <p>
     * If the pre-set supplier itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded. The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    LazyReference<V> lazy = Lazy.of(Value::new);
     *    // ...
     *    V value = lazy.get();
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException   if the pre-set supplier returns {@code null}.
     * @throws IllegalStateException  if a value was not already present and no
     *                                pre-set supplier was specified.
     * @throws NoSuchElementException if a supplier has previously thrown an exception.
     */
    @SuppressWarnings("unchecked")
    @Override
    public V get();

    /**
     * A builder that can be used to configure a LazyReference.
     *
     * @param <V> the type of the value.
     */
    public interface Builder<V>
            extends BaseLazyReference.Builder<V, LazyReference<V>, LazyReference.Builder<V>> {

        /**
         * {@return a builder that will use the provided {@code earliestEvaluation} when
         * eventially {@linkplain #build() building} a LazyReference}.
         * <p>
         * Any supplier configured with this builder must be referentially transparent
         * and thus must have no side-effect in order to allow transparent time-shifting of
         * evaluation.
         * <p>
         * No guarantees are made with respect to the latest time of evaluation and
         * consequently, the value might always be evaliate {@linkplain Lazy.Evaluation#AT_USE at use}.
         *
         * @param earliestEvaluation to use.
         */
        Builder<V> withEarliestEvaluation(Lazy.Evaluation earliestEvaluation);

    }
}
