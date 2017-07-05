/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.util.stream;

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;

/**
 * An immutable container for describing an ordered sequence of elements of some
 * type {@code T}.
 *
 * <p>A {@code Node} contains a fixed number of elements, which can be accessed
 * via the {@link #count}, {@link #spliterator}, {@link #forEach},
 * {@link #asArray}, or {@link #copyInto} methods.  A {@code Node} may have zero
 * or more child {@code Node}s; if it has no children (accessed via
 * {@link #getChildCount} and {@link #getChild(int)}, it is considered <em>flat
 * </em> or a <em>leaf</em>; if it has children, it is considered an
 * <em>internal</em> node.  The size of an internal node is the sum of sizes of
 * its children.
 *
 * @apiNote
 * <p>A {@code Node} typically does not store the elements directly, but instead
 * mediates access to one or more existing (effectively immutable) data
 * structures such as a {@code Collection}, array, or a set of other
 * {@code Node}s.  Commonly {@code Node}s are formed into a tree whose shape
 * corresponds to the computation tree that produced the elements that are
 * contained in the leaf nodes.  The use of {@code Node} within the stream
 * framework is largely to avoid copying data unnecessarily during parallel
 * operations.
 *
 * @param <T> the type of elements.
 * @since 1.8
 */
interface Node<T> {

    /**
     * Returns a {@link Spliterator} describing the elements contained in this
     * {@code Node}.
     *
     * @return a {@code Spliterator} describing the elements contained in this
     *         {@code Node}
     */
    Spliterator<T> spliterator();

    /**
     * Traverses the elements of this node, and invoke the provided
     * {@code Consumer} with each element.  Elements are provided in encounter
     * order if the source for the {@code Node} has a defined encounter order.
     *
     * @param consumer a {@code Consumer} that is to be invoked with each
     *        element in this {@code Node}
     */
    void forEach(Consumer<? super T> consumer);

    /**
     * Returns the number of child nodes of this node.
     *
     * @implSpec The default implementation returns zero.
     *
     * @return the number of child nodes
     */
    default int getChildCount() {
        return 0;
    }

    /**
     * Retrieves the child {@code Node} at a given index.
     *
     * @implSpec The default implementation always throws
     * {@code IndexOutOfBoundsException}.
     *
     * @param i the index to the child node
     * @return the child node
     * @throws IndexOutOfBoundsException if the index is less than 0 or greater
     *         than or equal to the number of child nodes
     */
    default Node<T> getChild(int i) {
        throw new IndexOutOfBoundsException();
    }

    /**
     * Provides an array view of the contents of this node.
     *
     * <p>Depending on the underlying implementation, this may return a
     * reference to an internal array rather than a copy.  Since the returned
     * array may be shared, the returned array should not be modified.  The
     * {@code generator} function may be consulted to create the array if a new
     * array needs to be created.
     *
     * @param generator a factory function which takes an integer parameter and
     *        returns a new, empty array of that size and of the appropriate
     *        array type
     * @return an array containing the contents of this {@code Node}
     */
    T[] asArray(IntFunction<T[]> generator);

    /**
     * Copies the content of this {@code Node} into an array, starting at a
     * given offset into the array.  It is the caller's responsibility to ensure
     * there is sufficient room in the array.
     *
     * @param array the array into which to copy the contents of this
     *       {@code Node}
     * @param offset the starting offset within the array
     * @throws IndexOutOfBoundsException if copying would cause access of data
     *         outside array bounds
     * @throws NullPointerException if {@code array} is {@code null}
     */
    void copyInto(T[] array, int offset);

    /**
     * Gets the {@code StreamShape} associated with this {@code Node}.
     *
     * @implSpec The default in {@code Node} returns
     * {@code StreamShape.REFERENCE}
     *
     * @return the stream shape associated with this node
     */
    default StreamShape getShape() {
        return StreamShape.REFERENCE;
    }

    /**
     * Returns the number of elements contained in this node.
     *
     * @return the number of elements contained in this node
     */
    long count();

    /**
     * A mutable builder for a {@code Node} that implements {@link Sink}, which
     * builds a flat node containing the elements that have been pushed to it.
     */
    interface Builder<T> extends Sink<T> {

        /**
         * Builds the node.  Should be called after all elements have been
         * pushed and signalled with an invocation of {@link Sink#end()}.
         *
         * @return the resulting {@code Node}
         */
        Node<T> build();

        /**
         * Specialized @{code Node.Builder} for int elements
         */
        interface OfInt extends Node.Builder<Integer>, Sink.OfInt {
            @Override
            Node.OfInt build();
        }

        /**
         * Specialized @{code Node.Builder} for long elements
         */
        interface OfLong extends Node.Builder<Long>, Sink.OfLong {
            @Override
            Node.OfLong build();
        }

        /**
         * Specialized @{code Node.Builder} for double elements
         */
        interface OfDouble extends Node.Builder<Double>, Sink.OfDouble {
            @Override
            Node.OfDouble build();
        }
    }

    /**
     * Specialized {@code Node} for int elements
     */
    interface OfInt extends Node<Integer> {

        /**
         * {@inheritDoc}
         *
         * @return a {@link Spliterator.OfInt} describing the elements of this
         *         node
         */
        @Override
        Spliterator.OfInt spliterator();

        /**
         * {@inheritDoc}
         *
         * @param consumer a {@code Consumer} that is to be invoked with each
         *        element in this {@code Node}.  If this is an
         *        {@code IntConsumer}, it is cast to {@code IntConsumer} so the
         *        elements may be processed without boxing.
         */
        @Override
        default void forEach(Consumer<? super Integer> consumer) {
            if (consumer instanceof IntConsumer) {
                forEach((IntConsumer) consumer);
            }
            else {
                if (Tripwire.ENABLED)
                    Tripwire.trip(getClass(), "{0} calling Node.OfInt.forEachRemaining(Consumer)");
                spliterator().forEachRemaining(consumer);
            }
        }

        /**
         * Traverses the elements of this node, and invoke the provided
         * {@code IntConsumer} with each element.
         *
         * @param consumer a {@code IntConsumer} that is to be invoked with each
         *        element in this {@code Node}
         */
        void forEach(IntConsumer consumer);

        /**
         * {@inheritDoc}
         *
         * @implSpec the default implementation invokes the generator to create
         * an instance of an Integer[] array with a length of {@link #count()}
         * and then invokes {@link #copyInto(Integer[], int)} with that
         * Integer[] array at an offset of 0.  This is not efficient and it is
         * recommended to invoke {@link #asPrimitiveArray()}.
         */
        @Override
        default Integer[] asArray(IntFunction<Integer[]> generator) {
            Integer[] boxed = generator.apply((int) count());
            copyInto(boxed, 0);
            return boxed;
        }

        /**
         * {@inheritDoc}
         *
         * @implSpec the default implementation invokes {@link #asPrimitiveArray()} to
         * obtain an int[] array then and copies the elements from that int[]
         * array into the boxed Integer[] array.  This is not efficient and it
         * is recommended to invoke {@link #copyInto(int[], int)}.
         */
        @Override
        default void copyInto(Integer[] boxed, int offset) {
            if (Tripwire.ENABLED)
                Tripwire.trip(getClass(), "{0} calling Node.OfInt.copyInto(Integer[], int)");

            int[] array = asPrimitiveArray();
            for (int i = 0; i < array.length; i++) {
                boxed[offset + i] = array[i];
            }
        }

        @Override
        default Node.OfInt getChild(int i) {
            throw new IndexOutOfBoundsException();
        }

        /**
         * Views this node as an int[] array.
         *
         * <p>Depending on the underlying implementation this may return a
         * reference to an internal array rather than a copy.  It is the callers
         * responsibility to decide if either this node or the array is utilized
         * as the primary reference for the data.</p>
         *
         * @return an array containing the contents of this {@code Node}
         */
        int[] asPrimitiveArray();

        /**
         * Copies the content of this {@code Node} into an int[] array, starting
         * at a given offset into the array.  It is the caller's responsibility
         * to ensure there is sufficient room in the array.
         *
         * @param array the array into which to copy the contents of this
         *              {@code Node}
         * @param offset the starting offset within the array
         * @throws IndexOutOfBoundsException if copying would cause access of
         *         data outside array bounds
         * @throws NullPointerException if {@code array} is {@code null}
         */
        void copyInto(int[] array, int offset);

        /**
         * {@inheritDoc}
         * @implSpec The default in {@code Node.OfInt} returns
         * {@code StreamShape.INT_VALUE}
         */
        default StreamShape getShape() {
            return StreamShape.INT_VALUE;
        }

    }

    /**
     * Specialized {@code Node} for long elements
     */
    interface OfLong extends Node<Long> {

        /**
         * {@inheritDoc}
         *
         * @return a {@link Spliterator.OfLong} describing the elements of this
         *         node
         */
        @Override
        Spliterator.OfLong spliterator();

        /**
         * {@inheritDoc}
         *
         * @param consumer A {@code Consumer} that is to be invoked with each
         *        element in this {@code Node}.  If this is an
         *        {@code LongConsumer}, it is cast to {@code LongConsumer} so
         *        the elements may be processed without boxing.
         */
        @Override
        default void forEach(Consumer<? super Long> consumer) {
            if (consumer instanceof LongConsumer) {
                forEach((LongConsumer) consumer);
            }
            else {
                if (Tripwire.ENABLED)
                    Tripwire.trip(getClass(), "{0} calling Node.OfLong.forEachRemaining(Consumer)");
                spliterator().forEachRemaining(consumer);
            }
        }

        /**
         * Traverses the elements of this node, and invoke the provided
         * {@code LongConsumer} with each element.
         *
         * @param consumer a {@code LongConsumer} that is to be invoked with
         *        each element in this {@code Node}
         */
        void forEach(LongConsumer consumer);

        /**
         * {@inheritDoc}
         *
         * @implSpec the default implementation invokes the generator to create
         * an instance of a Long[] array with a length of {@link #count()} and
         * then invokes {@link #copyInto(Long[], int)} with that Long[] array at
         * an offset of 0.  This is not efficient and it is recommended to
         * invoke {@link #asPrimitiveArray()}.
         */
        @Override
        default Long[] asArray(IntFunction<Long[]> generator) {
            Long[] boxed = generator.apply((int) count());
            copyInto(boxed, 0);
            return boxed;
        }

        /**
         * {@inheritDoc}
         *
         * @implSpec the default implementation invokes {@link #asPrimitiveArray()}
         * to obtain a long[] array then and copies the elements from that
         * long[] array into the boxed Long[] array.  This is not efficient and
         * it is recommended to invoke {@link #copyInto(long[], int)}.
         */
        @Override
        default void copyInto(Long[] boxed, int offset) {
            if (Tripwire.ENABLED)
                Tripwire.trip(getClass(), "{0} calling Node.OfInt.copyInto(Long[], int)");

            long[] array = asPrimitiveArray();
            for (int i = 0; i < array.length; i++) {
                boxed[offset + i] = array[i];
            }
        }

        @Override
        default Node.OfLong getChild(int i) {
            throw new IndexOutOfBoundsException();
        }

        /**
         * Views this node as a long[] array.
         *
         * <p/>Depending on the underlying implementation this may return a
         * reference to an internal array rather than a copy. It is the callers
         * responsibility to decide if either this node or the array is utilized
         * as the primary reference for the data.
         *
         * @return an array containing the contents of this {@code Node}
         */
        long[] asPrimitiveArray();

        /**
         * Copies the content of this {@code Node} into a long[] array, starting
         * at a given offset into the array.  It is the caller's responsibility
         * to ensure there is sufficient room in the array.
         *
         * @param array the array into which to copy the contents of this
         *        {@code Node}
         * @param offset the starting offset within the array
         * @throws IndexOutOfBoundsException if copying would cause access of
         *         data outside array bounds
         * @throws NullPointerException if {@code array} is {@code null}
         */
        void copyInto(long[] array, int offset);

        /**
         * {@inheritDoc}
         * @implSpec The default in {@code Node.OfLong} returns
         * {@code StreamShape.LONG_VALUE}
         */
        default StreamShape getShape() {
            return StreamShape.LONG_VALUE;
        }


    }

    /**
     * Specialized {@code Node} for double elements
     */
    interface OfDouble extends Node<Double> {

        /**
         * {@inheritDoc}
         *
         * @return A {@link Spliterator.OfDouble} describing the elements of
         *         this node
         */
        @Override
        Spliterator.OfDouble spliterator();

        /**
         * {@inheritDoc}
         *
         * @param consumer A {@code Consumer} that is to be invoked with each
         *        element in this {@code Node}.  If this is an
         *        {@code DoubleConsumer}, it is cast to {@code DoubleConsumer}
         *        so the elements may be processed without boxing.
         */
        @Override
        default void forEach(Consumer<? super Double> consumer) {
            if (consumer instanceof DoubleConsumer) {
                forEach((DoubleConsumer) consumer);
            }
            else {
                if (Tripwire.ENABLED)
                    Tripwire.trip(getClass(), "{0} calling Node.OfLong.forEachRemaining(Consumer)");
                spliterator().forEachRemaining(consumer);
            }
        }

        /**
         * Traverses the elements of this node, and invoke the provided
         * {@code DoubleConsumer} with each element.
         *
         * @param consumer A {@code DoubleConsumer} that is to be invoked with
         *        each element in this {@code Node}
         */
        void forEach(DoubleConsumer consumer);

        //

        /**
         * {@inheritDoc}
         *
         * @implSpec the default implementation invokes the generator to create
         * an instance of a Double[] array with a length of {@link #count()} and
         * then invokes {@link #copyInto(Double[], int)} with that Double[]
         * array at an offset of 0.  This is not efficient and it is recommended
         * to invoke {@link #asPrimitiveArray()}.
         */
        @Override
        default Double[] asArray(IntFunction<Double[]> generator) {
            Double[] boxed = generator.apply((int) count());
            copyInto(boxed, 0);
            return boxed;
        }

        /**
         * {@inheritDoc}
         *
         * @implSpec the default implementation invokes {@link #asPrimitiveArray()}
         * to obtain a double[] array then and copies the elements from that
         * double[] array into the boxed Double[] array.  This is not efficient
         * and it is recommended to invoke {@link #copyInto(double[], int)}.
         */
        @Override
        default void copyInto(Double[] boxed, int offset) {
            if (Tripwire.ENABLED)
                Tripwire.trip(getClass(), "{0} calling Node.OfDouble.copyInto(Double[], int)");

            double[] array = asPrimitiveArray();
            for (int i = 0; i < array.length; i++) {
                boxed[offset + i] = array[i];
            }
        }

        @Override
        default Node.OfDouble getChild(int i) {
            throw new IndexOutOfBoundsException();
        }

        /**
         * Views this node as a double[] array.
         *
         * <p/>Depending on the underlying implementation this may return a
         * reference to an internal array rather than a copy.  It is the callers
         * responsibility to decide if either this node or the array is utilized
         * as the primary reference for the data.
         *
         * @return an array containing the contents of this {@code Node}
         */
        double[] asPrimitiveArray();

        /**
         * Copies the content of this {@code Node} into a double[] array, starting
         * at a given offset into the array.  It is the caller's responsibility
         * to ensure there is sufficient room in the array.
         *
         * @param array the array into which to copy the contents of this
         *        {@code Node}
         * @param offset the starting offset within the array
         * @throws IndexOutOfBoundsException if copying would cause access of
         *         data outside array bounds
         * @throws NullPointerException if {@code array} is {@code null}
         */
        void copyInto(double[] array, int offset);

        /**
         * {@inheritDoc}
         *
         * @implSpec The default in {@code Node.OfDouble} returns
         * {@code StreamShape.DOUBLE_VALUE}
         */
        default StreamShape getShape() {
            return StreamShape.DOUBLE_VALUE;
        }
    }
}
