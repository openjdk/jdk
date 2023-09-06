/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.ForceInline;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;


/**
 * An intermediate operation that processes input elements, optionally mutating intermediate
 * state, optionally transforming the input elements into a different type of output elements,
 * and optionally applies final actions at end-of-upstream. Gatherer operations can be performed
 * either sequentially, or be parallelized -- if a combiner function is supplied.
 *
 * <p>Examples of gathering operations include, but is not limited to:
 * grouping elements into batches, also known as windowing functions;
 * de-duplicating consecutively similar elements; incremental accumulation functions;
 * incremental reordering functions, etc.  The class {@link java.util.stream.Gatherers}
 * provides implementations of common gathering operations.
 *
 * @apiNote
 * <p>A {@code Gatherer} is specified by four functions that work together to
 * process input elements, optionally using intermediate state, and optionally perform
 * a final operation at the end of input.  They are: <ul>
 *     <li>creation of a new, potentially mutable, state ({@link #initializer()})</li>
 *     <li>integrating a new input element ({@link #integrator()})</li>
 *     <li>combining two states into one ({@link #combiner()})</li>
 *     <li>performing an optional final operation ({@link #finisher()})</li>
 * </ul>
 *
 * <p>Implementations of Gatherer must not capture, retain, or expose to other threads,
 * the references to the state instance, or the downstream {@link java.util.stream.Gatherer.Sink}
 * for longer than the invocation duration of the method which they are passed to.
 *
 * <p>Each invocation to {@link #initializer()}, {@link #integrator()}, {@link #combiner()},
 * and {@link #finisher()} must return an equivalent result.
 *
 * <p>If a combiner function is supplied, then the operation can be parallelized by initializing
 * each partition in separation, invoking the integrator until it returns {@code false}, and then
 * joining each partitions state using the combiner, and then invoking the finalizer on the joined
 * state. Outputs and state later in the input sequence will be discarded if processing an earlier
 * segment short-circuits.
 *
 * @apiNote
 * Performing a gathering operation with a {@code Gatherer} should produce a
 * result equivalent to:
 *
 * <pre>{@code
 *     Gatherer.Sink<? super R> outputSink = r -> { System.out.println(r); return true; };
 *     A state = gatherer.initializer().get();
 *     for (T t : data) {
 *         gatherer.integrator().integrate(state, t, outputSink);
 *     }
 *     gatherer.finisher().apply(state, outputSink);
 * }</pre>
 *
 * <p>However, the library is free to partition the input, perform the integrations
 * on the partitions, and then use the combiner function to combine the partial
 * results to achieve a gathering operation.  (Depending on the specific gathering
 * operation, this may perform better or worse, depending on the relative cost
 * of the integrator and combiner functions.)
 * 
 * <p>In addition to the predefined implementations in {@link Gatherers}, the
 * static factory methods {@code of(...)} and {@code ofSequential(...)}
 * can be used to construct gatherers.  For example, you could create a gatherer
 * that implements that implements the equivalent of
 * {@link java.util.stream.Stream#map(java.util.function.Function)} with:
 *
 * <pre>{@code
 *     public static <T,R> Gatherer<T,?,R> map(Function<? super T, ? extends R> mapper) {
 *         return Gatherer.of(
 *             (unused, element, downstream) -> // integrator
 *                 downstream.flush(mapper.apply(element)),
 *             (left, right) -> // combiner
 *                 left
 *         );
 *     }
 * }</pre>
 *
 * <p>Gatherers are designed to be <em>composed</em>; two or more Gatherers can be
 * composed into a single Gatherer using the {@link #andThen(Gatherer)} method,
 * and a Gatherer can be composed with/prepended to a {@link Collector} using the
 * {@link #collect(Collector)} method.
 *
 * <pre>{@code
 *     // using the implementation of `map` as seen above
 *     Gatherer<Integer,?,Integer> increment = map(i -> i + 1);
 *
 *     Gatherer<Integer,?,String> intToString = map(i -> i.toString());
 *
 *     Gatherer<Integer,?,String> incrementThenToString = plusOne.andThen(intToString);
 *
 *     Collector<Integer,?,List<String>> incrementThenToStringList =
 *         incrementThenToString.collect(Collectors.toList());
 * }</pre>
 *
 * AS an example, in order to create a gatherer to implement a sequential Prefix Scan
 * as a Gatherer, it could be done the following way:
 *
 * <pre>{@code
 *     public static <T,R> Gatherer<T,?,R> scan(
 *         Supplier<R> initial,
 *         BiFunction<? super R, ? super T, ? extends R> scanner) {
 *
 *         class State {
 *             R current;
 *             State() {
 *                 current = initial.get();
 *             }
 *         }
 *
 *         return Gatherer.<T,State,R>ofSequential(
 *              State::new,
 *              Gatherer.Integrator.ofGreedy((state, element, downstream) -> {
 *                  state.current = scanner.apply(state.current, element);
 *                  return downstream.flush(state.current);
 *              })
 *         );
 *     }
 * }</pre>
 *
 * @implSpec Libraries that implement transformation based on {@code Gatherer}, such as
 * {@link Stream#gather(Gatherer)}, must adhere to the following constraints:
 * <ul>
 *     <li>The first argument passed to the integration function, both
 *     arguments passed to the combiner function, and the argument passed to the
 *     finisher function must be the result of a previous invocation of the
 *     result initializer, integrator, or combiner functions.</li>
 *     <li>The implementation should not do anything with the result of any of
 *     the result initializer, integrator, or combiner functions other than to
 *     pass them again to the integrator, combiner, or finisher functions.</li>
 *     <li>Once a state object is passed to the combiner or finisher function, it
 *     is never passed to the integrator function again.</li>
 *     <li>When the integrator function returns {@code false},
 *     it shall be interpreted just as if there were no more elements to pass it.</li>
 *     <li>For parallel evaluation, the gathering implementation must manage that
 *     the input is properly partitioned, that partitions are processed in isolation,
 *     and combining happens only after integration is complete for both partitions.</li>
 * </ul>
 *
 * @see Stream#gather(Gatherer)
 * @see Gatherers
 *
 * @param <T> the type of input elements to the gatherer operation
 * @param <A> the potentially mutable state type of the gatherer operation (often
 *            hidden as an implementation detail)
 * @param <R> the type of output elements from the gatherer operation
 * @since 22
 */
public interface Gatherer<T, A, R> {
    /**
     * A function that produces an instance of the intermediate state used for this
     * gathering operation.
     *
     * <p>By default, this method returns a function indicating that this Gatherer is
     * stateless.
     *
     * @return A function that produces an instance of the intermediate state used for this
     *         gathering operation
     */
    default Supplier<A> initializer() {
        return Gatherers.initializerNotNeeded();
    };

    /**
     * A function which integrates provided elements, potentially using the provided
     * intermediate state, optionally producing output to the provided downstream sink.
     *
     * @return a function which integrates provided elements, potentially using the
     *         provided state, optionally producing output to the provided downstream sink
     */
    Integrator<A, T, R> integrator();

    /**
     * A function which accepts two intermediate states and combines them into one.
     *
     * <p>By default, this method returns a function indicating that this Gatherer must
     * not be parallelized and invoking this function will throw an {@link java.lang.UnsupportedOperationException}.
     *
     * @return a function which accepts two intermediate states and combines them into one
     */
    default BinaryOperator<A> combiner() {
        return Gatherers.combinerNotPossible();
    }

    /**
     * A function which accepts the final intermediate state and a downstream handle,
     * allowing to perform a final action at the end of input elements.
     *
     * <p>By default, this method returns a function indicating that this Gatherer does not
     * need to perform any actions at the end of input elements.
     *
     * @return a function which transforms the intermediate result to the final result(s) which are
     * then passed on to the supplied downstream consumer
     */
    default BiConsumer<A, Sink<? super R>> finisher() {
        return Gatherers.finisherNotNeeded();
    }

    /**
     * A {@link Gatherer.ThenCollector} which first runs this Gatherer and feeds the output into the given Collector.
     *
     * <p>If the provided collector is a {@link Gatherer.ThenCollector}, the following is equivalent:
     *
     * <pre>{@code
     *     var a = gatherer.collect(thenCollector);
     *     var b = gatherer.andThen(thenCollector.gatherer).collect(thenCollector.collector);
     * }</pre>
     *
     * @param collector a Collector
     * @param <AA>      the accumulator type of the provided, and the returned, Collector
     * @param <RR>      the result type of the provided, and the returned, Collector
     * @return a new Collector which consists of this Gatherer and the given Collector
     */
    default <AA,RR> ThenCollector<T, A, R, AA, RR, ?> collect(Collector<R, AA, RR> collector) {
        return Gatherers.ThenCollectorImpl.of(this, collector);
    }

    /**
     * Returns a composed function that first applies this function to
     * its input, and then applies the {@code after} function to the result.
     * If evaluation of either function throws an exception, it is relayed to
     * the caller of the composed function.
     *
     * @param <V> the type of output of the {@code after} function, and of the
     *           composed function
     * @param after the function to apply after this function is applied
     * @return a composed function that first applies this function and then
     * applies the {@code after} function
     * @throws NullPointerException if after is null
     *
     * @see #compose(Function)
     */

    /**
     * Returns a composed Gatherer which passes the output of this Gatherer
     * as input that Gatherer.
     *
     * @param that the other gatherer
     * @param <AA> The type of the initializer of that gatherer
     * @param <RR> The type of output that gatherer has
     * @throws NullPointerException if the argument is null
     * @return returns a composed Gatherer which passes the output of this Gatherer
     *         as input that Gatherer
     */
    default <AA, RR> Gatherer<T, ?, RR> andThen(Gatherer<R, AA, RR> that) {
        Objects.requireNonNull(that);
        return Gatherers.Composite.of(this, that);
    }

    /**
     * Returns a stateless, sequential, gatherer from the supplied logic.
     *
     * @param integrator the integrator function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if the argument is null
     * @return a new gatherer comprised of the supplied logic
     */
    static <T, R> Gatherer<T, Void, R> ofSequential(Integrator<Void, T, R> integrator) {
        return of(Gatherers.initializerNotNeeded(), integrator, Gatherers.combinerNotPossible(), Gatherers.finisherNotNeeded());
    }

    /**
     * Returns a stateless, sequential, gatherer from the supplied logic.
     *
     * @param integrator the integrator function for the new gatherer
     * @param finisher the finisher function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if any argument is null
     * @return a new gatherer comprised of the supplied logic
     */
    static <T, R> Gatherer<T, Void, R> ofSequential(Integrator<Void, T, R> integrator,
                                                    BiConsumer<Void, Sink<? super R>> finisher) {
        return of(Gatherers.initializerNotNeeded(), integrator, Gatherers.combinerNotPossible(), finisher);
    }

    /**
     * Returns a sequential gatherer from the supplied logic.
     *
     * @param initializer the supplier function for the new gatherer
     * @param integrator the integrator function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <A> the type of initializer for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if any argument is null
     * @return a new gatherer comprised of the supplied logic
     */
    static <T, A, R> Gatherer<T, A, R> ofSequential(Supplier<A> initializer,
                                                    Integrator<A, T, R> integrator) {
        return of(initializer, integrator, Gatherers.combinerNotPossible(), Gatherers.finisherNotNeeded());
    }

    /**
     * Returns a sequential gatherer from the supplied logic.
     *
     * @param initializer the supplier function for the new gatherer
     * @param integrator the integrator function for the new gatherer
     * @param finisher the finisher function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <A> the type of initializer for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if any argument is null
     * @return a new gatherer comprised of the supplied logic
     */
    static <T, A, R> Gatherer<T, A, R> ofSequential(Supplier<A> initializer,
                                                    Integrator<A, T, R> integrator,
                                                    BiConsumer<A, Sink<? super R>> finisher) {
        return of(initializer, integrator, Gatherers.combinerNotPossible(), finisher);
    }

    /**
     * Returns a stateless, parallelizable, gatherer from the supplied logic.
     *
     * @param integrator the integrator function for the new gatherer
     * @param combiner the combiner function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if any argument is null
     * @return a new gatherer comprised of the supplied logic
     */
    static <T, R> Gatherer<T, Void, R> of(Integrator<Void, T, R> integrator,
                                          BinaryOperator<Void> combiner) {
        return of(Gatherers.initializerNotNeeded(), integrator, combiner, Gatherers.finisherNotNeeded());
    }

    /**
     * Returns a stateless, parallelizable, gatherer from the supplied logic.
     *
     * @param integrator the integrator function for the new gatherer
     * @param combiner the combiner function for the new gatherer
     * @param finisher the finisher function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if any argument is null
     * @return a new gatherer comprised of the supplied logic
     */
    static <T, R> Gatherer<T, Void, R> of(Integrator<Void, T, R> integrator,
                                          BinaryOperator<Void> combiner,
                                          BiConsumer<Void, Sink<? super R>> finisher) {
        return of(Gatherers.initializerNotNeeded(), integrator, combiner, finisher);
    }

    /**
     * Returns a parallelizable gatherer from the supplied logic.
     *
     * @param initializer the supplier function for the new gatherer
     * @param integrator the integrator function for the new gatherer
     * @param combiner the combiner function for the new gatherer
     * @param finisher the finisher function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <A> the type of initializer for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if any argument is null
     * @return a new gatherer comprised of the supplied logic
     */
    static <T, A, R> Gatherer<T, A, R> of(Supplier<A> initializer,
                                          Integrator<A, T, R> integrator,
                                          BinaryOperator<A> combiner,
                                          BiConsumer<A, Sink<? super R>> finisher) {
        return new Gatherers.GathererImpl<>(
                Objects.requireNonNull(initializer),
                Objects.requireNonNull(integrator),
                Objects.requireNonNull(combiner),
                Objects.requireNonNull(finisher)
        );
    }

    /**
     * A Sink represents a destination to which elements can be sent.
     * @param <T> the type of elements to flush
     */
    interface Sink<T> {

        /**
         * Sends, if possible, the provided element to the destination represented by this sink.
         *
         * <p>If this method returns {@code false} then this destination does not want any more elements.
         *
         * @param element the element to send
         * @return {@code true} if more elements can be sent, and {@code false} if not.
         */
        boolean flush(T element);

        /**
         * Allows for checking whether the destination represented by this sink
         * accepts more elements sent to it.
         *
         * @apiNote This is best-effort only, once this returns true it should
         *          never return false again for the same instance.
         *
         * By default this method returns {@code false}.
         *
         * @return {@code true} if this Sink is known not to want any more elements sent to it, {@code false} if otherwise
         */
        default boolean isKnownDone() { return false; }
    }

    /**
     * An Integrator receives elements and processes them, optionally using the supplied state,
     * and optionally sends incremental results downstream.
     *
     * @param <A> the type of elements this integrator receives
     * @param <T> the type of initializer this integrator consumes
     * @param <R> the type of results this integrator can produce
     */
    @FunctionalInterface
    interface Integrator<A, T, R> {
        /** Integrate is the method which given: the current state, the next element, and a downstream handle;
         * performs the main logic -- potentially inspecting and/or updating the state, optionally sending any
         * number of elements downstream -- and then returns whether more elements are to be consumed or not.
         *
         * @param element The element to integrate
         * @param state The state to integrate into
         * @param sink The downstream reference of this integration, returns false if doesn't want any more elements
         * @return {@code true} if subsequent integration is desired, {@code false} if not
         */
        boolean integrate(A state, T element, Sink<? super R> sink);

        /**
         * Factory method for converting Integrator-shaped lambdas into Integrators.
         *
         * @param integrator a lambda to create as Integrator
         * @return the given lambda as an Integrator
         * @param <A> the type of elements this integrator receives
         * @param <T> the type of initializer this integrator consumes
         * @param <R> the type of results this integrator can produce
         */
        @ForceInline
        static <A, T, R> Integrator<A, T, R>of(Integrator<A, T, R> integrator) {
            return integrator;
        }

        /**
         * Factory method for converting Integrator-shaped lambdas into {@link Greedy} Integrators.
         *
         * @param greedy a lambda to create as Integrator.Greedy
         * @return the given lambda as a Greedy Integrator
         * @param <A> the type of elements this integrator receives
         * @param <T> the type of initializer this integrator consumes
         * @param <R> the type of results this integrator can produce
         */
        @ForceInline
        static <A, T, R> Greedy<A, T, R>ofGreedy(Greedy<A, T, R> greedy) {
            return greedy;
        }

        /**
         * Greedy Integrators consume all their input, and may only relay that
         * the downstream Sink does not want more elements.
         *
         * This is used to clarify that no short-circuiting will be initiated by
         * this Integrator, and that information can then be used to optimize evaluation.
         *
         * @param <A> the type of elements this greedy integrator receives
         * @param <T> the type of initializer this greedy integrator consumes
         * @param <R> the type of results this greedy integrator can produce
         */
        @FunctionalInterface
        interface Greedy<A, T, R> extends Integrator<A, T, R> { }
    }

    /**
     * Representation of the combination of a Gatherer and a Collector which can be used as a Collector.
     *
     * <p>If a ThenCollector is used as any Collector then it will not be able to short-circuit evaluation,
     * and instead will discard all elements which are passed to the accumulator, which can lead to non-termination
     * if used on infinite streams, or poor performance if used on large streams.
     *
     * <p>ThenCollector allows for composition of {@link java.util.stream.Gatherer} and {@link java.util.stream.Collector},
     * where operations in the form of Gatherers can be prepended to a Collector to form a new Collector.
     *
     * <pre>{@code
     *     Gatherer<Integer,?,Integer> increment = Gatherer.of((unused, element, downstream) -> downstream.flush(element + 1), (left, right) -> left);
     *
     *     Collector<Integer,?,List<Integer>> toIntegerList = Collectors.toList();
     *
     *     Collector<Integer,?,List<Integer>> incrementTwiceThenToIntegerList = increment.andThen(increment).collect(toIntegerList);
     * }</pre>
     *
     * @param <T> the input type of the Gatherer, and of the ThenCollector when used as a Collector
     * @param <A> the state type of the Gatherer
     * @param <R> the output type of the Gatherer and the input type of the Collector
     * @param <AA> the state type of the Collector
     * @param <RR> the result type of the Collector, and of the ThenCollector when used as a Collector
     * @param <AAA> the state type of the ThenCollector when used as a Collector
     */
    sealed interface ThenCollector<T, A, R, AA, RR, AAA> extends Collector<T, AAA, RR> permits Gatherers.ThenCollectorImpl {
        /**
         * Returns the {@link java.util.stream.Gatherer} associated with this ThenCollector.
         * @return the gatherer associated with this ThenCollector
         */
        Gatherer<T,A,R> gatherer();

        /**
         * Returns the {@link java.util.stream.Collector} associated with this ThenCollector
         * @return the collector associated with this ThenCollector
         */
        Collector<R,AA,RR> collector();
    }
}