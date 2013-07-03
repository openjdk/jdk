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

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

// @@@ Specification to-do list @@@
// - Describe the difference between sequential and parallel streams
// - More general information about reduce, better definitions for associativity, more description of
//   how reduce employs parallelism, more examples
// - Role of stream flags in various operations, specifically ordering
//   - Whether each op preserves encounter order
// @@@ Specification to-do list @@@

/**
 * A sequence of elements supporting sequential and parallel bulk operations.
 * Streams support lazy intermediate operations (transforming a stream to
 * another stream) such as {@code filter} and {@code map}, and terminal
 * operations (consuming the contents of a stream to produce a result or
 * side-effect), such as {@code forEach}, {@code findFirst}, and {@code
 * iterator}.  Once an operation has been performed on a stream, it
 * is considered <em>consumed</em> and no longer usable for other operations.
 *
 * <p>For sequential stream pipelines, all operations are performed in the
 * <a href="package-summary.html#Ordering">encounter order</a> of the pipeline
 * source, if the pipeline source has a defined encounter order.
 *
 * <p>For parallel stream pipelines, unless otherwise specified, intermediate
 * stream operations preserve the <a href="package-summary.html#Ordering">
 * encounter order</a> of their source, and terminal operations
 * respect the encounter order of their source, if the source
 * has an encounter order.  Provided that and parameters to stream operations
 * satisfy the <a href="package-summary.html#NonInterference">non-interference
 * requirements</a>, and excepting differences arising from the absence of
 * a defined encounter order, the result of a stream pipeline should be the
 * stable across multiple executions of the same operations on the same source.
 * However, the timing and thread in which side-effects occur (for those
 * operations which are allowed to produce side-effects, such as
 * {@link #forEach(Consumer)}), are explicitly nondeterministic for parallel
 * execution of stream pipelines.
 *
 * <p>Unless otherwise noted, passing a {@code null} argument to any stream
 * method may result in a {@link NullPointerException}.
 *
 * @apiNote
 * Streams are not data structures; they do not manage the storage for their
 * elements, nor do they support access to individual elements.  However,
 * you can use the {@link #iterator()} or {@link #spliterator()} operations to
 * perform a controlled traversal.
 *
 * @param <T> type of elements
 * @since 1.8
 * @see <a href="package-summary.html">java.util.stream</a>
 */
public interface Stream<T> extends BaseStream<T, Stream<T>> {

    /**
     * Returns a stream consisting of the elements of this stream that match
     * the given predicate.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param predicate a <a href="package-summary.html#NonInterference">
     *                  non-interfering, stateless</a> predicate to apply to
     *                  each element to determine if it should be included
     * @return the new stream
     */
    Stream<T> filter(Predicate<? super T> predicate);

    /**
     * Returns a stream consisting of the results of applying the given
     * function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param <R> The element type of the new stream
     * @param mapper a <a href="package-summary.html#NonInterference">
     *               non-interfering, stateless</a> function to apply to each
     *               element
     * @return the new stream
     */
    <R> Stream<R> map(Function<? super T, ? extends R> mapper);

    /**
     * Returns an {@code IntStream} consisting of the results of applying the
     * given function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">
     *     intermediate operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">
     *               non-interfering, stateless</a> function to apply to each
     *               element
     * @return the new stream
     */
    IntStream mapToInt(ToIntFunction<? super T> mapper);

    /**
     * Returns a {@code LongStream} consisting of the results of applying the
     * given function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">
     *               non-interfering, stateless</a> function to apply to each
     *               element
     * @return the new stream
     */
    LongStream mapToLong(ToLongFunction<? super T> mapper);

    /**
     * Returns a {@code DoubleStream} consisting of the results of applying the
     * given function to the elements of this stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">
     *               non-interfering, stateless</a> function to apply to each
     *               element
     * @return the new stream
     */
    DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper);

    /**
     * Returns a stream consisting of the results of replacing each element of
     * this stream with the contents of the stream produced by applying the
     * provided mapping function to each element.  If the result of the mapping
     * function is {@code null}, this is treated as if the result is an empty
     * stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @apiNote
     * The {@code flatMap()} operation has the effect of applying a one-to-many
     * tranformation to the elements of the stream, and then flattening the
     * resulting elements into a new stream. For example, if {@code orders}
     * is a stream of purchase orders, and each purchase order contains a
     * collection of line items, then the following produces a stream of line
     * items:
     * <pre>{@code
     *     orderStream.flatMap(order -> order.getLineItems().stream())...
     * }</pre>
     *
     * @param <R> The element type of the new stream
     * @param mapper a <a href="package-summary.html#NonInterference">
     *               non-interfering, stateless</a> function to apply to each
     *               element which produces a stream of new values
     * @return the new stream
     */
    <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper);

    /**
     * Returns an {@code IntStream} consisting of the results of replacing each
     * element of this stream with the contents of the stream produced by
     * applying the provided mapping function to each element.  If the result of
     * the mapping function is {@code null}, this is treated as if the result is
     * an empty stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">
     *               non-interfering, stateless</a> function to apply to each
     *               element which produces a stream of new values
     * @return the new stream
     */
    IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper);

    /**
     * Returns a {@code LongStream} consisting of the results of replacing each
     * element of this stream with the contents of the stream produced
     * by applying the provided mapping function to each element.  If the result
     * of the mapping function is {@code null}, this is treated as if the
     * result is an empty stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">
     *               non-interfering, stateless</a> function to apply to
     *               each element which produces a stream of new values
     * @return the new stream
     */
    LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper);

    /**
     * Returns a {@code DoubleStream} consisting of the results of replacing each
     * element of this stream with the contents of the stream produced
     * by applying the provided mapping function to each element.  If the result
     * of the mapping function is {@code null}, this is treated as if the result
     * is an empty stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * @param mapper a <a href="package-summary.html#NonInterference">
     *               non-interfering, stateless</a> function to apply to each
     *               element which produces a stream of new values
     * @return the new stream
     */
    DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper);

    /**
     * Returns a stream consisting of the distinct elements (according to
     * {@link Object#equals(Object)}) of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">stateful
     * intermediate operation</a>.
     *
     * @return the new stream
     */
    Stream<T> distinct();

    /**
     * Returns a stream consisting of the elements of this stream, sorted
     * according to natural order.  If the elements of this stream are not
     * {@code Comparable}, a {@code java.lang.ClassCastException} may be thrown
     * when the stream pipeline is executed.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">stateful
     * intermediate operation</a>.
     *
     * @return the new stream
     */
    Stream<T> sorted();

    /**
     * Returns a stream consisting of the elements of this stream, sorted
     * according to the provided {@code Comparator}.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">stateful
     * intermediate operation</a>.
     *
     * @param comparator a <a href="package-summary.html#NonInterference">
     *                   non-interfering, stateless</a> {@code Comparator} to
     *                   be used to compare stream elements
     * @return the new stream
     */
    Stream<T> sorted(Comparator<? super T> comparator);

    /**
     * Returns a stream consisting of the elements of this stream, additionally
     * performing the provided action on each element as elements are consumed
     * from the resulting stream.
     *
     * <p>This is an <a href="package-summary.html#StreamOps">intermediate
     * operation</a>.
     *
     * <p>For parallel stream pipelines, the action may be called at
     * whatever time and in whatever thread the element is made available by the
     * upstream operation.  If the action modifies shared state,
     * it is responsible for providing the required synchronization.
     *
     * @apiNote This method exists mainly to support debugging, where you want
     * to see the elements as they flow past a certain point in a pipeline:
     * <pre>{@code
     *     list.stream()
     *         .filter(filteringFunction)
     *         .peek(e -> {System.out.println("Filtered value: " + e); });
     *         .map(mappingFunction)
     *         .peek(e -> {System.out.println("Mapped value: " + e); });
     *         .collect(Collectors.intoList());
     * }</pre>
     *
     * @param consumer a <a href="package-summary.html#NonInterference">
     *                 non-interfering</a> action to perform on the elements as
     *                 they are consumed from the stream
     * @return the new stream
     */
    Stream<T> peek(Consumer<? super T> consumer);

    /**
     * Returns a stream consisting of the elements of this stream, truncated
     * to be no longer than {@code maxSize} in length.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * stateful intermediate operation</a>.
     *
     * @param maxSize the number of elements the stream should be limited to
     * @return the new stream
     * @throws IllegalArgumentException if {@code maxSize} is negative
     */
    Stream<T> limit(long maxSize);

    /**
     * Returns a stream consisting of the remaining elements of this stream
     * after indexing {@code startInclusive} elements into the stream. If the
     * {@code startInclusive} index lies past the end of this stream then an
     * empty stream will be returned.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">stateful
     * intermediate operation</a>.
     *
     * @param startInclusive the number of leading elements to skip
     * @return the new stream
     * @throws IllegalArgumentException if {@code startInclusive} is negative
     */
    Stream<T> substream(long startInclusive);

    /**
     * Returns a stream consisting of the remaining elements of this stream
     * after indexing {@code startInclusive} elements into the stream and
     * truncated to contain no more than {@code endExclusive - startInclusive}
     * elements. If the {@code startInclusive} index lies past the end
     * of this stream then an empty stream will be returned.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * stateful intermediate operation</a>.
     *
     * @param startInclusive the starting position of the substream, inclusive
     * @param endExclusive the ending position of the substream, exclusive
     * @return the new stream
     * @throws IllegalArgumentException if {@code startInclusive} or
     * {@code endExclusive} is negative or {@code startInclusive} is greater
     * than {@code endExclusive}
     */
    Stream<T> substream(long startInclusive, long endExclusive);

    /**
     * Performs an action for each element of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * <p>For parallel stream pipelines, this operation does <em>not</em>
     * guarantee to respect the encounter order of the stream, as doing so
     * would sacrifice the benefit of parallelism.  For any given element, the
     * action may be performed at whatever time and in whatever thread the
     * library chooses.  If the action accesses shared state, it is
     * responsible for providing the required synchronization.
     *
     * @param action a <a href="package-summary.html#NonInterference">
     *               non-interfering</a> action to perform on the elements
     */
    void forEach(Consumer<? super T> action);

    /**
     * Performs an action for each element of this stream, guaranteeing that
     * each element is processed in encounter order for streams that have a
     * defined encounter order.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @param action a <a href="package-summary.html#NonInterference">
     *               non-interfering</a> action to perform on the elements
     * @see #forEach(Consumer)
     */
    void forEachOrdered(Consumer<? super T> action);

    /**
     * Returns an array containing the elements of this stream.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @return an array containing the elements of this stream
     */
    Object[] toArray();

    /**
     * Returns an array containing the elements of this stream, using the
     * provided {@code generator} function to allocate the returned array.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @param <A> the element type of the resulting array
     * @param generator a function which produces a new array of the desired
     *                  type and the provided length
     * @return an array containing the elements in this stream
     * @throws ArrayStoreException if the runtime type of the array returned
     * from the array generator is not a supertype of the runtime type of every
     * element in this stream
     */
    <A> A[] toArray(IntFunction<A[]> generator);

    /**
     * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
     * elements of this stream, using the provided identity value and an
     * <a href="package-summary.html#Associativity">associative</a>
     * accumulation function, and returns the reduced value.  This is equivalent
     * to:
     * <pre>{@code
     *     T result = identity;
     *     for (T element : this stream)
     *         result = accumulator.apply(result, element)
     *     return result;
     * }</pre>
     *
     * but is not constrained to execute sequentially.
     *
     * <p>The {@code identity} value must be an identity for the accumulator
     * function. This means that for all {@code t},
     * {@code accumulator.apply(identity, t)} is equal to {@code t}.
     * The {@code accumulator} function must be an
     * <a href="package-summary.html#Associativity">associative</a> function.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @apiNote Sum, min, max, average, and string concatenation are all special
     * cases of reduction. Summing a stream of numbers can be expressed as:
     *
     * <pre>{@code
     *     Integer sum = integers.reduce(0, (a, b) -> a+b);
     * }</pre>
     *
     * or more compactly:
     *
     * <pre>{@code
     *     Integer sum = integers.reduce(0, Integer::sum);
     * }</pre>
     *
     * <p>While this may seem a more roundabout way to perform an aggregation
     * compared to simply mutating a running total in a loop, reduction
     * operations parallelize more gracefully, without needing additional
     * synchronization and with greatly reduced risk of data races.
     *
     * @param identity the identity value for the accumulating function
     * @param accumulator an <a href="package-summary.html#Associativity">associative</a>
     *                    <a href="package-summary.html#NonInterference">non-interfering,
     *                    stateless</a> function for combining two values
     * @return the result of the reduction
     */
    T reduce(T identity, BinaryOperator<T> accumulator);

    /**
     * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
     * elements of this stream, using an
     * <a href="package-summary.html#Associativity">associative</a> accumulation
     * function, and returns an {@code Optional} describing the reduced value,
     * if any. This is equivalent to:
     * <pre>{@code
     *     boolean foundAny = false;
     *     T result = null;
     *     for (T element : this stream) {
     *         if (!foundAny) {
     *             foundAny = true;
     *             result = element;
     *         }
     *         else
     *             result = accumulator.apply(result, element);
     *     }
     *     return foundAny ? Optional.of(result) : Optional.empty();
     * }</pre>
     *
     * but is not constrained to execute sequentially.
     *
     * <p>The {@code accumulator} function must be an
     * <a href="package-summary.html#Associativity">associative</a> function.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @param accumulator an <a href="package-summary.html#Associativity">associative</a>
     *                    <a href="package-summary.html#NonInterference">non-interfering,
     *                    stateless</a> function for combining two values
     * @return the result of the reduction
     * @see #reduce(Object, BinaryOperator)
     * @see #min(java.util.Comparator)
     * @see #max(java.util.Comparator)
     */
    Optional<T> reduce(BinaryOperator<T> accumulator);

    /**
     * Performs a <a href="package-summary.html#Reduction">reduction</a> on the
     * elements of this stream, using the provided identity, accumulation
     * function, and a combining functions.  This is equivalent to:
     * <pre>{@code
     *     U result = identity;
     *     for (T element : this stream)
     *         result = accumulator.apply(result, element)
     *     return result;
     * }</pre>
     *
     * but is not constrained to execute sequentially.
     *
     * <p>The {@code identity} value must be an identity for the combiner
     * function.  This means that for all {@code u}, {@code combiner(identity, u)}
     * is equal to {@code u}.  Additionally, the {@code combiner} function
     * must be compatible with the {@code accumulator} function; for all
     * {@code u} and {@code t}, the following must hold:
     * <pre>{@code
     *     combiner.apply(u, accumulator.apply(identity, t)) == accumulator.apply(u, t)
     * }</pre>
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @apiNote Many reductions using this form can be represented more simply
     * by an explicit combination of {@code map} and {@code reduce} operations.
     * The {@code accumulator} function acts as a fused mapper and accumulator,
     * which can sometimes be more efficient than separate mapping and reduction,
     * such as in the case where knowing the previously reduced value allows you
     * to avoid some computation.
     *
     * @param <U> The type of the result
     * @param identity the identity value for the combiner function
     * @param accumulator an <a href="package-summary.html#Associativity">associative</a>
     *                    <a href="package-summary.html#NonInterference">non-interfering,
     *                    stateless</a> function for incorporating an additional
     *                    element into a result
     * @param combiner an <a href="package-summary.html#Associativity">associative</a>
     *                 <a href="package-summary.html#NonInterference">non-interfering,
     *                 stateless</a> function for combining two values, which
     *                 must be compatible with the accumulator function
     * @return the result of the reduction
     * @see #reduce(BinaryOperator)
     * @see #reduce(Object, BinaryOperator)
     */
    <U> U reduce(U identity,
                 BiFunction<U, ? super T, U> accumulator,
                 BinaryOperator<U> combiner);

    /**
     * Performs a <a href="package-summary.html#MutableReduction">mutable
     * reduction</a> operation on the elements of this stream.  A mutable
     * reduction is one in which the reduced value is a mutable value holder,
     * such as an {@code ArrayList}, and elements are incorporated by updating
     * the state of the result, rather than by replacing the result.  This
     * produces a result equivalent to:
     * <pre>{@code
     *     R result = resultFactory.get();
     *     for (T element : this stream)
     *         accumulator.accept(result, element);
     *     return result;
     * }</pre>
     *
     * <p>Like {@link #reduce(Object, BinaryOperator)}, {@code collect} operations
     * can be parallelized without requiring additional synchronization.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @apiNote There are many existing classes in the JDK whose signatures are
     * a good match for use as arguments to {@code collect()}.  For example,
     * the following will accumulate strings into an ArrayList:
     * <pre>{@code
     *     List<String> asList = stringStream.collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
     * }</pre>
     *
     * <p>The following will take a stream of strings and concatenates them into a
     * single string:
     * <pre>{@code
     *     String concat = stringStream.collect(StringBuilder::new, StringBuilder::append,
     *                                          StringBuilder::append)
     *                                 .toString();
     * }</pre>
     *
     * @param <R> type of the result
     * @param resultFactory a function that creates a new result container.
     *                      For a parallel execution, this function may be
     *                      called multiple times and must return a fresh value
     *                      each time.
     * @param accumulator an <a href="package-summary.html#Associativity">associative</a>
     *                    <a href="package-summary.html#NonInterference">non-interfering,
     *                    stateless</a> function for incorporating an additional
     *                    element into a result
     * @param combiner an <a href="package-summary.html#Associativity">associative</a>
     *                 <a href="package-summary.html#NonInterference">non-interfering,
     *                 stateless</a> function for combining two values, which
     *                 must be compatible with the accumulator function
     * @return the result of the reduction
     */
    <R> R collect(Supplier<R> resultFactory,
                  BiConsumer<R, ? super T> accumulator,
                  BiConsumer<R, R> combiner);

    /**
     * Performs a <a href="package-summary.html#MutableReduction">mutable
     * reduction</a> operation on the elements of this stream using a
     * {@code Collector} object to describe the reduction.  A {@code Collector}
     * encapsulates the functions used as arguments to
     * {@link #collect(Supplier, BiConsumer, BiConsumer)}, allowing for reuse of
     * collection strategies, and composition of collect operations such as
     * multiple-level grouping or partitioning.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * <p>When executed in parallel, multiple intermediate results may be
     * instantiated, populated, and merged, so as to maintain isolation of
     * mutable data structures.  Therefore, even when executed in parallel
     * with non-thread-safe data structures (such as {@code ArrayList}), no
     * additional synchronization is needed for a parallel reduction.
     *
     * @apiNote
     * The following will accumulate strings into an ArrayList:
     * <pre>{@code
     *     List<String> asList = stringStream.collect(Collectors.toList());
     * }</pre>
     *
     * <p>The following will classify {@code Person} objects by city:
     * <pre>{@code
     *     Map<String, Collection<Person>> peopleByCity
     *         = personStream.collect(Collectors.groupBy(Person::getCity));
     * }</pre>
     *
     * <p>The following will classify {@code Person} objects by state and city,
     * cascading two {@code Collector}s together:
     * <pre>{@code
     *     Map<String, Map<String, Collection<Person>>> peopleByStateAndCity
     *         = personStream.collect(Collectors.groupBy(Person::getState,
     *                                                   Collectors.groupBy(Person::getCity)));
     * }</pre>
     *
     * @param <R> the type of the result
     * @param collector the {@code Collector} describing the reduction
     * @return the result of the reduction
     * @see #collect(Supplier, BiConsumer, BiConsumer)
     * @see Collectors
     */
    <R> R collect(Collector<? super T, R> collector);

    /**
     * Returns the minimum element of this stream according to the provided
     * {@code Comparator}.  This is a special case of a
     * <a href="package-summary.html#MutableReduction">reduction</a>.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal operation</a>.
     *
     * @param comparator a <a href="package-summary.html#NonInterference">non-interfering,
     *                   stateless</a> {@code Comparator} to use to compare
     *                   elements of this stream
     * @return an {@code Optional} describing the minimum element of this stream,
     * or an empty {@code Optional} if the stream is empty
     */
    Optional<T> min(Comparator<? super T> comparator);

    /**
     * Returns the maximum element of this stream according to the provided
     * {@code Comparator}.  This is a special case of a
     * <a href="package-summary.html#MutableReduction">reduction</a>.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal
     * operation</a>.
     *
     * @param comparator a <a href="package-summary.html#NonInterference">non-interfering,
     *                   stateless</a> {@code Comparator} to use to compare
     *                   elements of this stream
     * @return an {@code Optional} describing the maximum element of this stream,
     * or an empty {@code Optional} if the stream is empty
     */
    Optional<T> max(Comparator<? super T> comparator);

    /**
     * Returns the count of elements in this stream.  This is a special case of
     * a <a href="package-summary.html#MutableReduction">reduction</a> and is
     * equivalent to:
     * <pre>{@code
     *     return mapToLong(e -> 1L).sum();
     * }</pre>
     *
     * <p>This is a <a href="package-summary.html#StreamOps">terminal operation</a>.
     *
     * @return the count of elements in this stream
     */
    long count();

    /**
     * Returns whether any elements of this stream match the provided
     * predicate.  May not evaluate the predicate on all elements if not
     * necessary for determining the result.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * @param predicate a <a href="package-summary.html#NonInterference">non-interfering,
     *                  stateless</a> predicate to apply to elements of this
     *                  stream
     * @return {@code true} if any elements of the stream match the provided
     * predicate otherwise {@code false}
     */
    boolean anyMatch(Predicate<? super T> predicate);

    /**
     * Returns whether all elements of this stream match the provided predicate.
     * May not evaluate the predicate on all elements if not necessary for
     * determining the result.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * @param predicate a <a href="package-summary.html#NonInterference">non-interfering,
     *                  stateless</a> predicate to apply to elements of this
     *                  stream
     * @return {@code true} if all elements of the stream match the provided
     * predicate otherwise {@code false}
     */
    boolean allMatch(Predicate<? super T> predicate);

    /**
     * Returns whether no elements of this stream match the provided predicate.
     * May not evaluate the predicate on all elements if not necessary for
     * determining the result.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * @param predicate a <a href="package-summary.html#NonInterference">non-interfering,
     *                  stateless</a> predicate to apply to elements of this
     *                  stream
     * @return {@code true} if no elements of the stream match the provided
     * predicate otherwise {@code false}
     */
    boolean noneMatch(Predicate<? super T> predicate);

    /**
     * Returns an {@link Optional} describing the first element of this stream
     * (in the encounter order), or an empty {@code Optional} if the stream is
     * empty.  If the stream has no encounter order, then any element may be
     * returned.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * @return an {@code Optional} describing the first element of this stream,
     * or an empty {@code Optional} if the stream is empty
     * @throws NullPointerException if the element selected is null
     */
    Optional<T> findFirst();

    /**
     * Returns an {@link Optional} describing some element of the stream, or an
     * empty {@code Optional} if the stream is empty.
     *
     * <p>This is a <a href="package-summary.html#StreamOps">short-circuiting
     * terminal operation</a>.
     *
     * <p>The behavior of this operation is explicitly nondeterministic; it is
     * free to select any element in the stream.  This is to allow for maximal
     * performance in parallel operations; the cost is that multiple invocations
     * on the same source may not return the same result.  (If the first element
     * in the encounter order is desired, use {@link #findFirst()} instead.)
     *
     * @return an {@code Optional} describing some element of this stream, or an
     * empty {@code Optional} if the stream is empty
     * @throws NullPointerException if the element selected is null
     * @see #findFirst()
     */
    Optional<T> findAny();

    // Static factories

    /**
     * Returns a builder for a {@code Stream}.
     *
     * @param <T> type of elements
     * @return a stream builder
     */
    public static<T> StreamBuilder<T> builder() {
        return new Streams.StreamBuilderImpl<>();
    }

    /**
     * Returns an empty sequential {@code Stream}.
     *
     * @param <T> the type of stream elements
     * @return an empty sequential stream
     */
    public static<T> Stream<T> empty() {
        return StreamSupport.stream(Spliterators.<T>emptySpliterator(), false);
    }

    /**
     * Returns a sequential {@code Stream} containing a single element.
     *
     * @param t the single element
     * @param <T> the type of stream elements
     * @return a singleton sequential stream
     */
    public static<T> Stream<T> of(T t) {
        return StreamSupport.stream(new Streams.StreamBuilderImpl<>(t), false);
    }

    /**
     * Returns a sequential stream whose elements are the specified values.
     *
     * @param <T> the type of stream elements
     * @param values the elements of the new stream
     * @return the new stream
     */
    @SafeVarargs
    public static<T> Stream<T> of(T... values) {
        return Arrays.stream(values);
    }

    /**
     * Returns an infinite sequential {@code Stream} produced by iterative
     * application of a function {@code f} to an initial element {@code seed},
     * producing a {@code Stream} consisting of {@code seed}, {@code f(seed)},
     * {@code f(f(seed))}, etc.
     *
     * <p>The first element (position {@code 0}) in the {@code Stream} will be
     * the provided {@code seed}.  For {@code n > 0}, the element at position
     * {@code n}, will be the result of applying the function {@code f} to the
     * element at position {@code n - 1}.
     *
     * @param <T> the type of stream elements
     * @param seed the initial element
     * @param f a function to be applied to to the previous element to produce
     *          a new element
     * @return a new sequential {@code Stream}
     */
    public static<T> Stream<T> iterate(final T seed, final UnaryOperator<T> f) {
        Objects.requireNonNull(f);
        final Iterator<T> iterator = new Iterator<T>() {
            @SuppressWarnings("unchecked")
            T t = (T) Streams.NONE;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public T next() {
                return t = (t == Streams.NONE) ? seed : f.apply(t);
            }
        };
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                iterator,
                Spliterator.ORDERED | Spliterator.IMMUTABLE), false);
    }

    /**
     * Returns a sequential {@code Stream} where each element is
     * generated by a {@code Supplier}.  This is suitable for generating
     * constant streams, streams of random elements, etc.
     *
     * @param <T> the type of stream elements
     * @param s the {@code Supplier} of generated elements
     * @return a new sequential {@code Stream}
     */
    public static<T> Stream<T> generate(Supplier<T> s) {
        Objects.requireNonNull(s);
        return StreamSupport.stream(
                new StreamSpliterators.InfiniteSupplyingSpliterator.OfRef<>(Long.MAX_VALUE, s), false);
    }

    /**
     * Creates a lazy concatenated {@code Stream} whose elements are all the
     * elements of a first {@code Stream} succeeded by all the elements of the
     * second {@code Stream}. The resulting stream is ordered if both
     * of the input streams are ordered, and parallel if either of the input
     * streams is parallel.
     *
     * @param <T> The type of stream elements
     * @param a the first stream
     * @param b the second stream to concatenate on to end of the first
     *        stream
     * @return the concatenation of the two input streams
     */
    public static <T> Stream<T> concat(Stream<? extends T> a, Stream<? extends T> b) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);

        @SuppressWarnings("unchecked")
        Spliterator<T> split = new Streams.ConcatSpliterator.OfRef<>(
                (Spliterator<T>) a.spliterator(), (Spliterator<T>) b.spliterator());
        return StreamSupport.stream(split, a.isParallel() || b.isParallel());
    }
}
