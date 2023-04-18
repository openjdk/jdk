package java.util.concurrent.lazy;

import jdk.internal.javac.PreviewFeature;

import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An empty lazy references has no pre-set supplier...
 *
 * @param <V> The type of the value to be recorded
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.LAZY)
public interface EmptyLazyReference<V>
        extends BaseLazyReference<V>, Function<Supplier<? extends V>, V> {

    /**
     * Returns the present value or, if no present value exists, atomically attempts
     * to compute the value using the <em>provided {@code supplier}</em>.
     * <p>
     * If the provided {@code supplier} itself throws an (unchecked) exception, the
     * exception is rethrown, and no value is recorded.  The most
     * common usage is to construct a new object serving as a memoized result, as in:
     * <p>
     * {@snippet lang = java:
     *    EmptyLazyReference<V> lazy = Lazy.ofEmpty();
     *    // ...
     *    V value = lazy.apply(Value::new);
     *    assertNotNull(value); // Value is non-null
     *}
     * <p>
     * If another thread attempts to compute the value, the current thread will be suspended until
     * the atempt completes (successfully or not).
     *
     * @param supplier to apply if no previous value exists
     * @return the value (pre-existing or newly computed)
     * @throws NullPointerException   if the provided {@code supplier} is {@code null} or if the provider
     *                                {@code supplier} returns {@code null}.
     * @throws NoSuchElementException if a supplier has previously thrown an exception.
     */
    @Override
    public V apply(Supplier<? extends V> supplier);

    /**
     * A builder that can be used to configure an EmptyLazyReference.
     *
     * @param <V> the type of the value.
     */
    public interface Builder<V>
            extends BaseLazyReference.Builder<V, EmptyLazyReference<V>, Builder<V>> {

    }

}
