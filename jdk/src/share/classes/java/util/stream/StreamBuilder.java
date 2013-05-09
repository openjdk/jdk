/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * A mutable builder for a {@code Stream}.  This allows the creation of a
 * {@code Stream} by generating elements individually and adding them to the
 * {@code StreamBuilder} (without the copying overhead that comes from using
 * an {@code ArrayList} as a temporary buffer.)
 *
 * <p>A {@code StreamBuilder} has a lifecycle, where it starts in a building
 * phase, during which elements can be added, and then transitions to a built
 * phase, after which elements may not be added.  The built phase begins
 * when the {@link #build()}} method is called, which creates an ordered
 * {@code Stream} whose elements are the elements that were added to the stream
 * builder, in the order they were added.
 *
 * <p>Primitive specializations of {@code StreamBuilder} are provided
 * for {@link OfInt int}, {@link OfLong long}, and {@link OfDouble double}
 * values.
 *
 * @param <T> the type of stream elements
 * @see Stream#builder()
 * @since 1.8
 */
public interface StreamBuilder<T> extends Consumer<T> {

    /**
     * Adds an element to the stream being built.
     *
     * @throws IllegalStateException if the builder has already transitioned to
     * the built state
     */
    @Override
    void accept(T t);

    /**
     * Adds an element to the stream being built.
     *
     * @implSpec
     * The default implementation behaves as if:
     * <pre>{@code
     *     accept(t)
     *     return this;
     * }</pre>
     *
     * @param t the element to add
     * @return {@code this} builder
     * @throws IllegalStateException if the builder has already transitioned to
     * the built state
     */
    default StreamBuilder<T> add(T t) {
        accept(t);
        return this;
    }

    /**
     * Builds the stream, transitioning this builder to the built state.
     * An {@code IllegalStateException} is thrown if there are further attempts
     * to operate on the builder after it has entered the built state.
     *
     * @return the built stream
     * @throws IllegalStateException if the builder has already transitioned to
     * the built state
     */
    Stream<T> build();

    /**
     * A mutable builder for an {@code IntStream}.
     *
     * <p>A stream builder has a lifecycle, where it starts in a building
     * phase, during which elements can be added, and then transitions to a
     * built phase, after which elements may not be added.  The built phase
     * begins when the {@link #build()}} method is called, which creates an
     * ordered stream whose elements are the elements that were added to the
     * stream builder, in the order they were added.
     *
     * @see IntStream#builder()
     * @since 1.8
     */
    interface OfInt extends IntConsumer {

        /**
         * Adds an element to the stream being built.
         *
         * @throws IllegalStateException if the builder has already transitioned
         * to the built state
         */
        @Override
        void accept(int t);

        /**
         * Adds an element to the stream being built.
         *
         * @implSpec
         * The default implementation behaves as if:
         * <pre>{@code
         *     accept(t)
         *     return this;
         * }</pre>
         *
         * @param t the element to add
         * @return {@code this} builder
         * @throws IllegalStateException if the builder has already transitioned
         * to the built state
         */
        default StreamBuilder.OfInt add(int t) {
            accept(t);
            return this;
        }

        /**
         * Builds the stream, transitioning this builder to the built state.
         * An {@code IllegalStateException} is thrown if there are further
         * attempts to operate on the builder after it has entered the built
         * state.
         *
         * @return the built stream
         * @throws IllegalStateException if the builder has already transitioned to
         * the built state
         */
        IntStream build();
    }

    /**
     * A mutable builder for a {@code LongStream}.
     *
     * <p>A stream builder has a lifecycle, where it starts in a building
     * phase, during which elements can be added, and then transitions to a
     * built phase, after which elements may not be added.  The built phase
     * begins when the {@link #build()}} method is called, which creates an
     * ordered stream whose elements are the elements that were added to the
     * stream builder, in the order they were added.
     *
     * @see LongStream#builder()
     * @since 1.8
     */
    interface OfLong extends LongConsumer {

        /**
         * Adds an element to the stream being built.
         *
         * @throws IllegalStateException if the builder has already transitioned
         * to the built state
         */
        @Override
        void accept(long t);

        /**
         * Adds an element to the stream being built.
         *
         * @implSpec
         * The default implementation behaves as if:
         * <pre>{@code
         *     accept(t)
         *     return this;
         * }</pre>
         *
         * @param t the element to add
         * @return {@code this} builder
         * @throws IllegalStateException if the builder has already transitioned
         * to the built state
         */
        default StreamBuilder.OfLong add(long t) {
            accept(t);
            return this;
        }

        /**
         * Builds the stream, transitioning this builder to the built state.
         * An {@code IllegalStateException} is thrown if there are further
         * attempts to operate on the builder after it has entered the built
         * state.
         *
         * @return the built stream
         * @throws IllegalStateException if the builder has already transitioned
         * to the built state
         */
        LongStream build();
    }

    /**
     * A mutable builder for a {@code DoubleStream}.
     *
     * @see LongStream#builder()
     * @since 1.8
     */
    interface OfDouble extends DoubleConsumer {

        /**
         * Adds an element to the stream being built.
         *
         * <p>A stream builder  has a lifecycle, where it starts in a building
         * phase, during which elements can be added, and then transitions to a
         * built phase, after which elements may not be added.  The built phase
         * begins when the {@link #build()}} method is called, which creates an
         * ordered stream whose elements are the elements that were added to the
         * stream builder, in the order they were added.
         *
         * @throws IllegalStateException if the builder has already transitioned
         * to the built state
         */
        @Override
        void accept(double t);

        /**
         * Adds an element to the stream being built.
         *
         * @implSpec
         * The default implementation behaves as if:
         * <pre>{@code
         *     accept(t)
         *     return this;
         * }</pre>
         *
         * @param t the element to add
         * @return {@code this} builder
         * @throws IllegalStateException if the builder has already transitioned
         * to the built state
         */
        default StreamBuilder.OfDouble add(double t) {
            accept(t);
            return this;
        }

        /**
         * Builds the stream, transitioning this builder to the built state.
         * An {@code IllegalStateException} is thrown if there are further
         * attempts to operate on the builder after it has entered the built
         * state.
         *
         * @return the built stream
         * @throws IllegalStateException if the builder has already transitioned
         * to the built state
         */
        DoubleStream build();
    }
}
