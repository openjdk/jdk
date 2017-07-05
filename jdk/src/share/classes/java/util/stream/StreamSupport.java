/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Supplier;

/**
 * Low-level utility methods for creating and manipulating streams.
 *
 * <p>This class is mostly for library writers presenting stream views
 * of their data structures; most static stream methods for end users are in
 * {@link Streams}.
 *
 * <p>Unless otherwise stated, streams are created as sequential
 * streams.  A sequential stream can be transformed into a parallel stream by
 * calling the {@code parallel()} method on the created stream.
 *
 * @since 1.8
 */
public class StreamSupport {
    /**
     * Creates a new sequential {@code Stream} from a {@code Spliterator}.
     *
     * <p>The spliterator is only traversed, split, or queried for estimated
     * size after the terminal operation of the stream pipeline commences.
     *
     * <p>It is strongly recommended the spliterator report a characteristic of
     * {@code IMMUTABLE} or {@code CONCURRENT}, or be
     * <a href="Spliterator.html#binding">late-binding</a>.  Otherwise,
     * {@link #stream(Supplier, int)} should be used to
     * reduce the scope of potential interference with the source.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param <T> the type of stream elements
     * @param spliterator a {@code Spliterator} describing the stream elements
     * @return a new sequential {@code Stream}
     */
    public static <T> Stream<T> stream(Spliterator<T> spliterator) {
        Objects.requireNonNull(spliterator);
        return new ReferencePipeline.Head<>(spliterator,
                                            StreamOpFlag.fromCharacteristics(spliterator),
                                            false);
    }

    /**
     * Creates a new parallel {@code Stream} from a {@code Spliterator}.
     *
     * <p>The spliterator is only traversed, split, or queried for estimated
     * size after the terminal operation of the stream pipeline commences.
     *
     * <p>It is strongly recommended the spliterator report a characteristic of
     * {@code IMMUTABLE} or {@code CONCURRENT}, or be
     * <a href="Spliterator.html#binding">late-binding</a>.  Otherwise,
     * {@link #stream(Supplier, int)} should be used to
     * reduce the scope of potential interference with the source.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param <T> the type of stream elements
     * @param spliterator a {@code Spliterator} describing the stream elements
     * @return a new parallel {@code Stream}
     */
    public static <T> Stream<T> parallelStream(Spliterator<T> spliterator) {
        Objects.requireNonNull(spliterator);
        return new ReferencePipeline.Head<>(spliterator,
                                            StreamOpFlag.fromCharacteristics(spliterator),
                                            true);
    }

    /**
     * Creates a new sequential {@code Stream} from a {@code Supplier} of
     * {@code Spliterator}.
     *
     * <p>The {@link Supplier#get()} method will be invoked on the supplier no
     * more than once, and after the terminal operation of the stream pipeline
     * commences.
     *
     * <p>For spliterators that report a characteristic of {@code IMMUTABLE}
     * or {@code CONCURRENT}, or that are
     * <a href="Spliterator.html#binding">late-binding</a>, it is likely
     * more efficient to use {@link #stream(java.util.Spliterator)} instead.
     * The use of a {@code Supplier} in this form provides a level of
     * indirection that reduces the scope of potential interference with the
     * source.  Since the supplier is only invoked after the terminal operation
     * commences, any modifications to the source up to the start of the
     * terminal operation are reflected in the stream result.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param <T> the type of stream elements
     * @param supplier a {@code Supplier} of a {@code Spliterator}
     * @param characteristics Spliterator characteristics of the supplied
     *        {@code Spliterator}.  The characteristics must be equal to
     *        {@code source.get().getCharacteristics()}.
     * @return a new sequential {@code Stream}
     * @see #stream(Spliterator)
     */
    public static <T> Stream<T> stream(Supplier<? extends Spliterator<T>> supplier,
                                      int characteristics) {
        Objects.requireNonNull(supplier);
        return new ReferencePipeline.Head<>(supplier,
                                            StreamOpFlag.fromCharacteristics(characteristics),
                                            false);
    }

    /**
     * Creates a new parallel {@code Stream} from a {@code Supplier} of
     * {@code Spliterator}.
     *
     * <p>The {@link Supplier#get()} method will be invoked on the supplier no
     * more than once, and after the terminal operation of the stream pipeline
     * commences.
     *
     * <p>For spliterators that report a characteristic of {@code IMMUTABLE}
     * or {@code CONCURRENT}, or that are
     * <a href="Spliterator.html#binding">late-binding</a>, it is likely
     * more efficient to use {@link #stream(Spliterator)} instead.
     * The use of a {@code Supplier} in this form provides a level of
     * indirection that reduces the scope of potential interference with the
     * source.  Since the supplier is only invoked after the terminal operation
     * commences, any modifications to the source up to the start of the
     * terminal operation are reflected in the stream result.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param <T> the type of stream elements
     * @param supplier a {@code Supplier} of a {@code Spliterator}
     * @param characteristics Spliterator characteristics of the supplied
     *        {@code Spliterator}.  The characteristics must be equal to
     *        {@code source.get().getCharacteristics()}
     * @return a new parallel {@code Stream}
     * @see #parallelStream(Spliterator)
     */
    public static <T> Stream<T> parallelStream(Supplier<? extends Spliterator<T>> supplier,
                                              int characteristics) {
        Objects.requireNonNull(supplier);
        return new ReferencePipeline.Head<>(supplier,
                                            StreamOpFlag.fromCharacteristics(characteristics),
                                            true);
    }

    /**
     * Creates a new sequential {@code IntStream} from a {@code Spliterator.OfInt}.
     *
     * <p>The spliterator is only traversed, split, or queried for estimated size
     * after the terminal operation of the stream pipeline commences.
     *
     * <p>It is strongly recommended the spliterator report a characteristic of
     * {@code IMMUTABLE} or {@code CONCURRENT}, or be
     * <a href="Spliterator.html#binding">late-binding</a>.  Otherwise,
     * {@link #stream(Supplier, int)}} should be used to
     * reduce the scope of potential interference with the source.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param spliterator a {@code Spliterator.OfInt} describing the stream elements
     * @return a new sequential {@code IntStream}
     */
    public static IntStream intStream(Spliterator.OfInt spliterator) {
        return new IntPipeline.Head<>(spliterator,
                                      StreamOpFlag.fromCharacteristics(spliterator),
                                      false);
    }

    /**
     * Creates a new parallel {@code IntStream} from a {@code Spliterator.OfInt}.
     *
     * <p>he spliterator is only traversed, split, or queried for estimated size
     * after the terminal operation of the stream pipeline commences.
     *
     * <p>It is strongly recommended the spliterator report a characteristic of
     * {@code IMMUTABLE} or {@code CONCURRENT}, or be
     * <a href="Spliterator.html#binding">late-binding</a>.  Otherwise,
     * {@link #stream(Supplier, int)}} should be used to
     * reduce the scope of potential interference with the source.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param spliterator a {@code Spliterator.OfInt} describing the stream elements
     * @return a new parallel {@code IntStream}
     */
    public static IntStream intParallelStream(Spliterator.OfInt spliterator) {
        return new IntPipeline.Head<>(spliterator,
                                      StreamOpFlag.fromCharacteristics(spliterator),
                                      true);
    }

    /**
     * Creates a new sequential {@code IntStream} from a {@code Supplier} of
     * {@code Spliterator.OfInt}.
     *
     * <p>The {@link Supplier#get()} method will be invoked on the supplier no
     * more than once, and after the terminal operation of the stream pipeline
     * commences.
     *
     * <p>For spliterators that report a characteristic of {@code IMMUTABLE}
     * or {@code CONCURRENT}, or that are
     * <a href="Spliterator.html#binding">late-binding</a>, it is likely
     * more efficient to use {@link #intStream(Spliterator.OfInt)} instead.
     * The use of a {@code Supplier} in this form provides a level of
     * indirection that reduces the scope of potential interference with the
     * source.  Since the supplier is only invoked after the terminal operation
     * commences, any modifications to the source up to the start of the
     * terminal operation are reflected in the stream result.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param supplier a {@code Supplier} of a {@code Spliterator.OfInt}
     * @param characteristics Spliterator characteristics of the supplied
     *        {@code Spliterator.OfInt}.  The characteristics must be equal to
     *        {@code source.get().getCharacteristics()}
     * @return a new sequential {@code IntStream}
     * @see #intStream(Spliterator.OfInt)
     */
    public static IntStream intStream(Supplier<? extends Spliterator.OfInt> supplier,
                                      int characteristics) {
        return new IntPipeline.Head<>(supplier,
                                      StreamOpFlag.fromCharacteristics(characteristics),
                                      false);
    }

    /**
     * Creates a new parallel {@code IntStream} from a {@code Supplier} of
     * {@code Spliterator.OfInt}.
     *
     * <p>The {@link Supplier#get()} method will be invoked on the supplier no
     * more than once, and after the terminal operation of the stream pipeline
     * commences.
     *
     * <p>For spliterators that report a characteristic of {@code IMMUTABLE}
     * or {@code CONCURRENT}, or that are
     * <a href="Spliterator.html#binding">late-binding</a>, it is likely
     * more efficient to use {@link #intStream(Spliterator.OfInt)} instead.
     * The use of a {@code Supplier} in this form provides a level of
     * indirection that reduces the scope of potential interference with the
     * source.  Since the supplier is only invoked after the terminal operation
     * commences, any modifications to the source up to the start of the
     * terminal operation are reflected in the stream result.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param supplier a {@code Supplier} of a {@code Spliterator.OfInt}
     * @param characteristics Spliterator characteristics of the supplied
     *        {@code Spliterator.OfInt}.  The characteristics must be equal to
     *        {@code source.get().getCharacteristics()}
     * @return a new parallel {@code IntStream}
     * @see #intParallelStream(Spliterator.OfInt)
     */
    public static IntStream intParallelStream(Supplier<? extends Spliterator.OfInt> supplier,
                                              int characteristics) {
        return new IntPipeline.Head<>(supplier,
                                      StreamOpFlag.fromCharacteristics(characteristics),
                                      true);
    }

    /**
     * Creates a new sequential {@code LongStream} from a {@code Spliterator.OfLong}.
     *
     * <p>The spliterator is only traversed, split, or queried for estimated
     * size after the terminal operation of the stream pipeline commences.
     *
     * <p>It is strongly recommended the spliterator report a characteristic of
     * {@code IMMUTABLE} or {@code CONCURRENT}, or be
     * <a href="Spliterator.html#binding">late-binding</a>.  Otherwise,
     * {@link #stream(Supplier, int)} should be used to
     * reduce the scope of potential interference with the source.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param spliterator a {@code Spliterator.OfLong} describing the stream
     * elements
     * @return a new sequential {@code LongStream}
     */
    public static LongStream longStream(Spliterator.OfLong spliterator) {
        return new LongPipeline.Head<>(spliterator,
                                       StreamOpFlag.fromCharacteristics(spliterator),
                                       false);
    }

    /**
     * Creates a new parallel {@code LongStream} from a {@code Spliterator.OfLong}.
     *
     * <p>The spliterator is only traversed, split, or queried for estimated
     * size after the terminal operation of the stream pipeline commences.
     *
     * <p>It is strongly recommended the spliterator report a characteristic of
     * {@code IMMUTABLE} or {@code CONCURRENT}, or be
     * <a href="Spliterator.html#binding">late-binding</a>.  Otherwise,
     * {@link #stream(Supplier, int)} should be used to
     * reduce the scope of potential interference with the source.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param spliterator a {@code Spliterator.OfLong} describing the stream elements
     * @return a new parallel {@code LongStream}
     */
    public static LongStream longParallelStream(Spliterator.OfLong spliterator) {
        return new LongPipeline.Head<>(spliterator,
                                       StreamOpFlag.fromCharacteristics(spliterator),
                                       true);
    }

    /**
     * Creates a new sequential {@code LongStream} from a {@code Supplier} of
     * {@code Spliterator.OfLong}.
     *
     * <p>The {@link Supplier#get()} method will be invoked on the supplier no
     * more than once, and after the terminal operation of the stream pipeline
     * commences.
     *
     * <p>For spliterators that report a characteristic of {@code IMMUTABLE}
     * or {@code CONCURRENT}, or that are
     * <a href="Spliterator.html#binding">late-binding</a>, it is likely
     * more efficient to use {@link #longStream(Spliterator.OfLong)} instead.
     * The use of a {@code Supplier} in this form provides a level of
     * indirection that reduces the scope of potential interference with the
     * source.  Since the supplier is only invoked after the terminal operation
     * commences, any modifications to the source up to the start of the
     * terminal operation are reflected in the stream result.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param supplier a {@code Supplier} of a {@code Spliterator.OfLong}
     * @param characteristics Spliterator characteristics of the supplied
     *        {@code Spliterator.OfLong}.  The characteristics must be equal to
     *        {@code source.get().getCharacteristics()}
     * @return a new sequential {@code LongStream}
     * @see #longStream(Spliterator.OfLong)
     */
    public static LongStream longStream(Supplier<? extends Spliterator.OfLong> supplier,
                                        int characteristics) {
        return new LongPipeline.Head<>(supplier,
                                       StreamOpFlag.fromCharacteristics(characteristics),
                                       false);
    }

    /**
     * Creates a new parallel {@code LongStream} from a {@code Supplier} of
     * {@code Spliterator.OfLong}.
     *
     * <p>The {@link Supplier#get()} method will be invoked on the supplier no
     * more than once, and after the terminal operation of the stream pipeline
     * commences.
     *
     * <p>For spliterators that report a characteristic of {@code IMMUTABLE}
     * or {@code CONCURRENT}, or that are
     * <a href="Spliterator.html#binding">late-binding</a>, it is likely
     * more efficient to use {@link #longStream(Spliterator.OfLong)} instead.
     * The use of a {@code Supplier} in this form provides a level of
     * indirection that reduces the scope of potential interference with the
     * source.  Since the supplier is only invoked after the terminal operation
     * commences, any modifications to the source up to the start of the
     * terminal operation are reflected in the stream result.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param supplier A {@code Supplier} of a {@code Spliterator.OfLong}
     * @param characteristics Spliterator characteristics of the supplied
     *        {@code Spliterator.OfLong}.  The characteristics must be equal to
     *        {@code source.get().getCharacteristics()}
     * @return A new parallel {@code LongStream}
     * @see #longParallelStream(Spliterator.OfLong)
     */
    public static LongStream longParallelStream(Supplier<? extends Spliterator.OfLong> supplier,
                                                int characteristics) {
        return new LongPipeline.Head<>(supplier,
                                       StreamOpFlag.fromCharacteristics(characteristics),
                                       true);
    }

    /**
     * Creates a new sequential {@code DoubleStream} from a
     * {@code Spliterator.OfDouble}.
     *
     * <p>The spliterator is only traversed, split, or queried for estimated size
     * after the terminal operation of the stream pipeline commences.
     *
     * <p>It is strongly recommended the spliterator report a characteristic of
     * {@code IMMUTABLE} or {@code CONCURRENT}, or be
     * <a href="Spliterator.html#binding">late-binding</a>.  Otherwise,
     * {@link #stream(Supplier, int)} should be used to
     * reduce the scope of potential interference with the source.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param spliterator A {@code Spliterator.OfDouble} describing the stream elements
     * @return A new sequential {@code DoubleStream}
     */
    public static DoubleStream doubleStream(Spliterator.OfDouble spliterator) {
        return new DoublePipeline.Head<>(spliterator,
                                         StreamOpFlag.fromCharacteristics(spliterator),
                                         false);
    }

    /**
     * Creates a new parallel {@code DoubleStream} from a
     * {@code Spliterator.OfDouble}.
     *
     * <p>The spliterator is only traversed, split, or queried for estimated size
     * after the terminal operation of the stream pipeline commences.
     *
     * <p>It is strongly recommended the spliterator report a characteristic of
     * {@code IMMUTABLE} or {@code CONCURRENT}, or be
     * <a href="Spliterator.html#binding">late-binding</a>.  Otherwise,
     * {@link #stream(Supplier, int)} should be used to
     * reduce the scope of potential interference with the source.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param spliterator A {@code Spliterator.OfDouble} describing the stream elements
     * @return A new parallel {@code DoubleStream}
     */
    public static DoubleStream doubleParallelStream(Spliterator.OfDouble spliterator) {
        return new DoublePipeline.Head<>(spliterator,
                                         StreamOpFlag.fromCharacteristics(spliterator),
                                         true);
    }

    /**
     * Creates a new sequential {@code DoubleStream} from a {@code Supplier} of
     * {@code Spliterator.OfDouble}.
     * <p>
     * The {@link Supplier#get()} method will be invoked on the supplier no
     * more than once, and after the terminal operation of the stream pipeline
     * commences.
     * <p>
     * For spliterators that report a characteristic of {@code IMMUTABLE}
     * or {@code CONCURRENT}, or that are
     * <a href="Spliterator.html#binding">late-binding</a>, it is likely
     * more efficient to use {@link #doubleStream(Spliterator.OfDouble)} instead.
     * The use of a {@code Supplier} in this form provides a level of
     * indirection that reduces the scope of potential interference with the
     * source.  Since the supplier is only invoked after the terminal operation
     * commences, any modifications to the source up to the start of the
     * terminal operation are reflected in the stream result.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param supplier A {@code Supplier} of a {@code Spliterator.OfDouble}
     * @param characteristics Spliterator characteristics of the supplied
     *        {@code Spliterator.OfDouble}.  The characteristics must be equal to
     *        {@code source.get().getCharacteristics()}
     * @return A new sequential {@code DoubleStream}
     * @see #doubleStream(Spliterator.OfDouble)
     */
    public static DoubleStream doubleStream(Supplier<? extends Spliterator.OfDouble> supplier,
                                            int characteristics) {
        return new DoublePipeline.Head<>(supplier,
                                         StreamOpFlag.fromCharacteristics(characteristics),
                                         false);
    }

    /**
     * Creates a new parallel {@code DoubleStream} from a {@code Supplier} of
     * {@code Spliterator.OfDouble}.
     *
     * <p>The {@link Supplier#get()} method will be invoked on the supplier no
     * more than once, and after the terminal operation of the stream pipeline
     * commences.
     *
     * <p>For spliterators that report a characteristic of {@code IMMUTABLE}
     * or {@code CONCURRENT}, or that are
     * <a href="Spliterator.html#binding">late-binding</a>, it is likely
     * more efficient to use {@link #doubleStream(Spliterator.OfDouble)} instead.
     * The use of a {@code Supplier} in this form provides a level of
     * indirection that reduces the scope of potential interference with the
     * source.  Since the supplier is only invoked after the terminal operation
     * commences, any modifications to the source up to the start of the
     * terminal operation are reflected in the stream result.  See
     * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
     * more details.
     *
     * @param supplier a {@code Supplier} of a {@code Spliterator.OfDouble}
     * @param characteristics Spliterator characteristics of the supplied
     *        {@code Spliterator.OfDouble}.  The characteristics must be equal to
     *        {@code source.get().getCharacteristics()}
     * @return a new parallel {@code DoubleStream}
     * @see #doubleParallelStream(Spliterator.OfDouble)
     */
    public static DoubleStream doubleParallelStream(Supplier<? extends Spliterator.OfDouble> supplier,
                                                    int characteristics) {
        return new DoublePipeline.Head<>(supplier,
                                         StreamOpFlag.fromCharacteristics(characteristics),
                                         true);
    }
}
