package jdk.internal.lang;

import jdk.internal.lang.stable.ShapeImpl;
import jdk.internal.lang.stable.StableArrayOne;
import jdk.internal.lang.stable.StableArrayZero;

import java.lang.invoke.MethodHandle;
import java.util.Objects;

/**
 * An atomic, thread-safe, stable array holder for which components can be set at most once.
 * <p>
 * Stable arrays are eligible for certain optimizations by the JVM. For multidimensional
 * arrays, they are also subject to array flattening.
 * <p>
 * The total number of components ({@linkplain Shape#size()})in stable arrays can not exceed
 * about 2<sup>31</sup>.
 *
 * Except for a StableArray's component values, all method parameters must be
 * <em>non-null</em> {@link NullPointerException} will be thrown.
 *
 * @param <V> type of StableValue that this stable array holds
 * @since 23
 */
public sealed interface StableArray<V>
        permits StableArrayZero, StableArrayOne {

    /**
     * {@return the {@linkplain Shape} of this stable array}
     */
    Shape shape();

    /**
     * {@return the component of this stable array or throws an
     * IllegalArgumentException if this stable array is not a zero-dimensional array}
     *
     * @throws UnsupportedOperationException if {@code shape().isZeroDimensional()}
     */
    StableValue<V> get();

    /**
     * {@return the {@code [firstIndex]} component of this stable array or throws an
     * IllegalArgumentException if this stable array is not a one-dimensional array}
     *
     * @param firstIndex to use as an index
     * @throws UnsupportedOperationException if {@code shape().dimensions() != 1}
     * @throws ArrayIndexOutOfBoundsException if {@code
     *         firstIndex < 0 || firstIndex >= size()}
     */
    StableValue<V> get(int firstIndex);

    /**
     * {@return the {@code [firstIndex, secondIndex]}component of this stable array
     * or throws an IllegalArgumentException if this stable array is not a
     * two-dimensional array}
     *
     * @param firstIndex  to use as a first index
     * @param secondIndex to use as a second index
     * @throws UnsupportedOperationException if {@code shape().dimensions() != 2}
     * @throws ArrayIndexOutOfBoundsException if {@code
     *         firstIndex < 0 ||
     *         secondIndex < 0 ||
     *         firstIndex >= shape().dimension(0) ||
     *         secondIndex >= shape().dimension(1)}
     */
    StableValue<V> get(int firstIndex,
                       int secondIndex);

    /**
     * {@return the {@code [firstIndex, secondIndex, thirdIndex]} component of this
     * stable array or throws an IllegalArgumentException if this stable array is not
     * a three-dimensional array}
     *
     * @param firstIndex  to use as a first index
     * @param secondIndex to use as a second index
     * @param thirdIndex  to use as a third index
     * @throws UnsupportedOperationException if {@code shape().dimensions() != 3}
     * @throws ArrayIndexOutOfBoundsException if {@code
     *         firstIndex < 0 ||
     *         secondIndex < 0 ||
     *         thirdIndex < 0 ||
     *         firstIndex >= shape().dimension(0) ||
     *         secondIndex >= shape().dimension(1) ||
     *         thirdIndex >= shape().dimension(2)}
     */
    StableValue<V> get(int firstIndex,
                       int secondIndex,
                       int thirdIndex);

    /**
     * {@return the component that corresponds to the given {@code indices} of this
     * stable array or throws an IllegalArgumentException if this stable array does
     * not have the same number of dimensions as {@code indices.length}}
     *
     * @param indices to use as indices for the stable array
     * @throws UnsupportedOperationException if {@code shape().dimensions() != indices.length}
     * @throws ArrayIndexOutOfBoundsException if an {@code n} exists such that
     *         {@code n >= 0, n < indices.length, indices[n] < 0 |
     *         indices[n] >= shape().dimension(n)}
     */
    StableValue<V> get(int... indices);

    /**
     * {@return a method handle that has a polymorphic signature that corresponds to the
     * {@linkplain #shape()} of this array and that returns a StableValue but where the
     * first parameter is "this" stable array}
     * <p>
     * More formally, the returned method handle features the following coordinates:
     * <ol start="0">
     *     <li>a StableArray representing the "this" object. The returned method handle
     *         will ensure the provided "this" object has the same dimensions as the
     *         this StableArray</li>
     *     <li>an {@code int} representing the first index (if any)</li>
     *     <li>an {@code int} representing the second index (if any)</li>
     *     <li>...</li>
     *     <li>an {@code int} representing the last index (if any)</li>
     * </ol>
     * The return type of the returned method handle is {@linkplain StableValue}.
     * <p>
     * The returned method handle will perform index checking corresponding to the
     * {@linkplain #shape()} of the StableArray provided as parameter zero.
     * The returned method handle will otherwise throw exceptions that corresponds to the
     * {@linkplain #get(int...)} method.
     */
    MethodHandle getter();

    static <V> StableArray<V> of(Shape shape) {
        return switch (shape.nDimensions()) {
            case 0  -> new StableArrayZero<>();
            case 1  -> new StableArrayOne<>(shape);
            default -> throw new UnsupportedOperationException();
        };
    }

    sealed interface Shape permits ShapeImpl {

        /**
         * {@return the number of dimensions for this shape}
         */
        int nDimensions();

        /**
         * {@return the dimension for the provided {@code index}}
         *
         * @throws IllegalArgumentException if {@code index < 0 | index >= nDimensions()}
         */
        int dimension(int index);

        /**
         * {@return the number of components in an array with this shape}
         * <p>
         * The {@code size()} of this shape is the product of all its dimensions.
         */
        int size();

        /**
         * {@return {@code true} if this shape represents a zero-dimensional array,
         * (i.e. {@code nDimensions() == 0}), {@code false} otherwise}
         */
        boolean isZeroDimensional();

        /**
         * {@return a Shape with the given {@code dimensions}}
         *
         * @param dimensions describing a shape
         * @throws ArithmeticException if the product of the given dimensions overflows
         * a {@code long}
         */
        static Shape of(int... dimensions) {
            Objects.requireNonNull(dimensions);
            return ShapeImpl.of(dimensions);
        }

    }

}
