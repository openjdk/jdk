package jdk.internal.lang;

import jdk.internal.lang.stable.StableArray2DImpl;
import jdk.internal.lang.stable.StableArrayImpl;
import jdk.internal.lang.stable.TrustedFieldType;

/**
 * An atomic, thread-safe, stable array holder for which components can be set at most once.
 * <p>
 * Stable arrays are eligible for certain optimizations by the JVM.
 * <p>
 * The total number of components in a stable array can not exceed about 2<sup>31</sup>.
 *
 * @param <V> type of StableValue that this stable array holds
 * @since 23
 */
public sealed interface StableArray2D<V>
        extends TrustedFieldType
        permits StableArray2DImpl {

    /**
     * {@return the {@code [i0][i1]} component of this stable array}
     *
     * @param i0 first index to use as a component index
     * @param i1 second index to use as a component index
     * @throws ArrayIndexOutOfBoundsException if {@code X \u2208 [0, 1],
     *         iX < 0 || iX >= length(X)}
     */
    StableValue<V> get(int i0, int i1);

    /**
     * {@return the length of the provided {@code dimension}}
     *
     * @throws IllegalArgumentException if {@code dimension < 0 || dimension >= 2}
     */
    int length(int dimension);

    /**
     * {@return a new StableArray2D with the provided dimensions}
     * @param <V> type of StableValue the stable array holds
     * @throws IllegalArgumentException if either of the provided dimensions
     *         {@code dim0} or {@code dim1} are {@code < 0}
     */
    static <V> StableArray2D<V> of(int dim0, int dim1) {
        if (dim0 < 0 || dim1 < 0) {
            throw new IllegalArgumentException();
        }
        return StableArray2DImpl.of(dim0, dim1);
    }

}
