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

import jdk.internal.javac.PreviewFeature;
import jdk.internal.vm.annotation.ForceInline;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;


/**
 * An intermediate operation that transforms a stream of input elements into a
 * stream of output elements, optionally applying a final action when the end of
 * the upstream is reached. The transformation may be stateless or stateful,
 * and may buffer input before producing any output.
 *
 * <p>Gatherer operations can be performed either sequentially,
 * or be parallelized -- if a combiner function is supplied.
 *
 * <p>There are many examples of gathering operations, including but not
 * limited to:
 * grouping elements into batches (windowing functions);
 * de-duplicating consecutively similar elements; incremental accumulation
 * functions (prefix scan); incremental reordering functions, etc.  The class
 * {@link java.util.stream.Gatherers} provides implementations of common
 * gathering operations.
 *
 * @apiNote
 * <p>A {@code Gatherer} is specified by four functions that work together to
 * process input elements, optionally using intermediate state, and optionally
 * perform a final action at the end of input.  They are: <ul>
 *     <li>creating a new, potentially mutable, state ({@link #initializer()})</li>
 *     <li>integrating a new input element ({@link #integrator()})</li>
 *     <li>combining two states into one ({@link #combiner()})</li>
 *     <li>performing an optional final action ({@link #finisher()})</li>
 * </ul>
 *
 * <p>Each invocation of {@link #initializer()}, {@link #integrator()},
 * {@link #combiner()}, and {@link #finisher()} must return a semantically
 * identical result.
 *
 * <p>Implementations of Gatherer must not capture, retain, or expose to
 * other threads, the references to the state instance, or the downstream
 * {@link Downstream} for longer than the invocation duration of the method
 * which they are passed to.
 *
 * <p>Performing a gathering operation with a {@code Gatherer} should produce a
 * result equivalent to:
 *
 * {@snippet lang = java:
 * Gatherer.Downstream<? super R> downstream = ...;
 * A state = gatherer.initializer().get();
 * for (T t : data) {
 *     gatherer.integrator().integrate(state, t, downstream);
 * }
 * gatherer.finisher().accept(state, downstream);
 * }
 *
 * <p>However, the library is free to partition the input, perform the
 * integrations on the partitions, and then use the combiner function to
 * combine the partial results to achieve a gathering operation.  (Depending
 * on the specific gathering operation, this may perform better or worse,
 * depending on the relative cost of the integrator and combiner functions.)
 *
 * <p>In addition to the predefined implementations in {@link Gatherers}, the
 * static factory methods {@code of(...)} and {@code ofSequential(...)}
 * can be used to construct gatherers.  For example, you could create a gatherer
 * that implements the equivalent of
 * {@link java.util.stream.Stream#map(java.util.function.Function)} with:
 *
 * {@snippet lang = java:
 * public static <T, R> Gatherer<T, ?, R> map(Function<? super T, ? extends R> mapper) {
 *     return Gatherer.of(
 *         (unused, element, downstream) -> // integrator
 *             downstream.push(mapper.apply(element))
 *     );
 * }
 * }
 *
 * <p>Gatherers are designed to be <em>composed</em>; two or more Gatherers can
 * be composed into a single Gatherer using the {@link #andThen(Gatherer)}
 * method.
 *
 * {@snippet lang = java:
 * // using the implementation of `map` as seen above
 * Gatherer<Integer, ?, Integer> increment = map(i -> i + 1);
 *
 * Gatherer<Object, ?, String> toString = map(i -> i.toString());
 *
 * Gatherer<Integer, ?, String> incrementThenToString = increment.andThen(toString);
 * }
 *
 * <p>As an example, a Gatherer implementing a sequential Prefix Scan could
 * be done the following way:
 *
 * {@snippet lang = java:
 * public static <T, R> Gatherer<T, ?, R> scan(
 *     Supplier<R> initial,
 *     BiFunction<? super R, ? super T, ? extends R> scanner) {
 *
 *     class State {
 *         R current = initial.get();
 *     }
 *
 *     return Gatherer.<T, State, R>ofSequential(
 *          State::new,
 *          Gatherer.Integrator.ofGreedy((state, element, downstream) -> {
 *              state.current = scanner.apply(state.current, element);
 *              return downstream.push(state.current);
 *          })
 *     );
 * }
 * }
 *
 * <p>Example of usage:
 *
 * {@snippet lang = java:
 * // will contain: ["1", "12", "123", "1234", "12345", "123456", "1234567", "12345678", "123456789"]
 * List<String> numberStrings =
 *     Stream.of(1,2,3,4,5,6,7,8,9)
 *           .gather(
 *               scan(() -> "", (string, number) -> string + number)
 *            )
 *           .toList();
 * }
 *
 * @implSpec Libraries that implement transformations based on {@code Gatherer},
 * such as {@link Stream#gather(Gatherer)}, must adhere to the following
 * constraints:
 * <ul>
 *     <li>Gatherers whose initializer is {@link #defaultInitializer()} are
 *     considered to be stateless, and invoking their initializer is optional.
 *     </li>
 *     <li>Gatherers whose integrator is an instance of {@link Integrator.Greedy}
 *     can be assumed not to short-circuit, and the return value of invoking
 *     {@link Integrator#integrate(Object, Object, Downstream)} does not need to
 *     be inspected.</li>
 *     <li>The first argument passed to the integration function, both
 *     arguments passed to the combiner function, and the argument passed to the
 *     finisher function must be the result of a previous invocation of the
 *     initializer or combiner functions.</li>
 *     <li>The implementation should not do anything with the result of any of
 *     the initializer or combiner functions other than to
 *     pass them again to the integrator, combiner, or finisher functions.</li>
 *     <li>Once a state object is passed to the combiner or finisher function,
 *     it is never passed to the integrator function again.</li>
 *     <li>When the integrator function returns {@code false},
 *     it shall be interpreted just as if there were no more elements to pass
 *     it.</li>
 *     <li>For parallel evaluation, the gathering implementation must manage
 *     that the input is properly partitioned, that partitions are processed
 *     in isolation, and combining happens only after integration is complete
 *     for both partitions.</li>
 *     <li>Gatherers whose combiner is {@link #defaultCombiner()} may only be
 *     evaluated sequentially. All other combiners allow the operation to be
 *     parallelized by initializing each partition in separation, invoking
 *     the integrator until it returns {@code false}, and then joining each
 *     partitions state using the combiner, and then invoking the finisher on
 *     the joined state. Outputs and state later in the input sequence will
 *     be discarded if processing an earlier partition short-circuits.</li>
 *     <li>Gatherers whose finisher is {@link #defaultFinisher()} are considered
 *     to not have an end-of-stream hook and invoking their finisher is
 *     optional.</li>
 * </ul>
 *
 * @see Stream#gather(Gatherer)
 * @see Gatherers
 *
 * @param <T> the type of input elements to the gatherer operation
 * @param <A> the potentially mutable state type of the gatherer operation
 *            (often hidden as an implementation detail)
 * @param <R> the type of output elements from the gatherer operation
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.STREAM_GATHERERS)
public interface Gatherer<T, A, R> {
    /**
     * A function that produces an instance of the intermediate state used for
     * this gathering operation.
     *
     * @implSpec The implementation in this interface returns
     *           {@link #defaultInitializer()}.
     *
     * @return A function that produces an instance of the intermediate state
     * used for this gathering operation
     */
    default Supplier<A> initializer() {
        return defaultInitializer();
    };

    /**
     * A function which integrates provided elements, potentially using
     * the provided intermediate state, optionally producing output to the
     * provided {@link Downstream}.
     *
     * @return a function which integrates provided elements, potentially using
     *         the provided state, optionally producing output to the provided
     *         Downstream
     */
    Integrator<A, T, R> integrator();

    /**
     * A function which accepts two intermediate states and combines them into
     * one.
     *
     * @implSpec The implementation in this interface returns
     *           {@link #defaultCombiner()}.
     *
     * @return a function which accepts two intermediate states and combines
     *         them into one
     */
    default BinaryOperator<A> combiner() {
        return defaultCombiner();
    }

    /**
     * A function which accepts the final intermediate state
     * and a {@link Downstream} object, allowing to perform a final action at
     * the end of input elements.
     *
     * @implSpec The implementation in this interface returns
     *           {@link #defaultFinisher()}.
     *
     * @return a function which transforms the intermediate result to the final
     *         result(s) which are then passed on to the provided Downstream
     */
    default BiConsumer<A, Downstream<? super R>> finisher() {
        return defaultFinisher();
    }

    /**
     * Returns a composed Gatherer which connects the output of this Gatherer
     * to the input of that Gatherer.
     *
     * @implSpec The implementation in this interface returns a new Gatherer
     *           which is semantically equivalent to the combination of
     *           {@code this} and {@code that} gatherer.
     *
     * @param that the other gatherer
     * @param <RR> The type of output of that Gatherer
     * @throws NullPointerException if the argument is {@code null}
     * @return returns a composed Gatherer which connects the output of this
     *         Gatherer as input that Gatherer
     */
    default <RR> Gatherer<T, ?, RR> andThen(Gatherer<? super R, ?, ? extends RR> that) {
        Objects.requireNonNull(that);
        return Gatherers.Composite.of(this, that);
    }

    /**
     * Returns an initializer which is the default initializer of a Gatherer.
     * The returned initializer identifies that the owner Gatherer is stateless.
     *
     * @implSpec This method always returns the same instance.
     *
     * @see Gatherer#initializer()
     * @return the instance of the default initializer
     * @param <A> the type of the state of the returned initializer
     */
    static <A> Supplier<A> defaultInitializer() {
        return Gatherers.Value.DEFAULT.initializer();
    }

    /**
     * Returns a combiner which is the default combiner of a Gatherer.
     * The returned combiner identifies that the owning Gatherer must only
     * be evaluated sequentially.
     *
     * @implSpec This method always returns the same instance.
     *
     * @see Gatherer#finisher()
     * @return the instance of the default combiner
     * @param <A> the type of the state of the returned combiner
     */
    static <A> BinaryOperator<A> defaultCombiner() {
        return Gatherers.Value.DEFAULT.combiner();
    }

    /**
     * Returns a {@code finisher} which is the default finisher of
     * a {@code Gatherer}.
     * The returned finisher identifies that the owning Gatherer performs
     * no additional actions at the end of input.
     *
     * @implSpec This method always returns the same instance.
     *
     * @see Gatherer#finisher()
     * @return the instance of the default finisher
     * @param <A> the type of the state of the returned finisher
     * @param <R> the type of the Downstream of the returned finisher
     */
    static <A, R> BiConsumer<A, Downstream<? super R>> defaultFinisher() {
        return Gatherers.Value.DEFAULT.finisher();
    }

    /**
     * Returns a new, sequential, and stateless {@code Gatherer} described by
     * the given {@code integrator}.
     *
     * @param integrator the integrator function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if the argument is {@code null}
     * @return the new {@code Gatherer}
     */
    static <T, R> Gatherer<T, Void, R> ofSequential(
            Integrator<Void, T, R> integrator) {
        return of(
                defaultInitializer(),
                integrator,
                defaultCombiner(),
                defaultFinisher()
        );
    }

    /**
     * Returns a new, sequential, and stateless {@code Gatherer} described by
     * the given {@code integrator} and {@code finisher}.
     *
     * @param integrator the integrator function for the new gatherer
     * @param finisher the finisher function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if any argument is {@code null}
     * @return the new {@code Gatherer}
     */
    static <T, R> Gatherer<T, Void, R> ofSequential(
            Integrator<Void, T, R> integrator,
            BiConsumer<Void, Downstream<? super R>> finisher) {
        return of(
                defaultInitializer(),
                integrator,
                defaultCombiner(),
                finisher
        );
    }

    /**
     * Returns a new, sequential, {@code Gatherer} described by the given
     * {@code initializer} and {@code integrator}.
     *
     * @param initializer the initializer function for the new gatherer
     * @param integrator the integrator function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <A> the type of state for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if any argument is {@code null}
     * @return the new {@code Gatherer}
     */
    static <T, A, R> Gatherer<T, A, R> ofSequential(
            Supplier<A> initializer,
            Integrator<A, T, R> integrator) {
        return of(
                initializer,
                integrator,
                defaultCombiner(),
                defaultFinisher()
        );
    }

    /**
     * Returns a new, sequential, {@code Gatherer} described by the given
     * {@code initializer}, {@code integrator}, and {@code finisher}.
     *
     * @param initializer the initializer function for the new gatherer
     * @param integrator the integrator function for the new gatherer
     * @param finisher the finisher function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <A> the type of state for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if any argument is {@code null}
     * @return the new {@code Gatherer}
     */
    static <T, A, R> Gatherer<T, A, R> ofSequential(
            Supplier<A> initializer,
            Integrator<A, T, R> integrator,
            BiConsumer<A, Downstream<? super R>> finisher) {
        return of(
                initializer,
                integrator,
                defaultCombiner(),
                finisher
        );
    }

    /**
     * Returns a new, parallelizable, and stateless {@code Gatherer} described
     * by the given {@code integrator}.
     *
     * @param integrator the integrator function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if any argument is {@code null}
     * @return the new {@code Gatherer}
     */
    static <T, R> Gatherer<T, Void, R> of(Integrator<Void, T, R> integrator) {
        return of(
                defaultInitializer(),
                integrator,
                Gatherers.Value.DEFAULT.statelessCombiner,
                defaultFinisher()
        );
    }

    /**
     * Returns a new, parallelizable, and stateless {@code Gatherer} described
     * by the given {@code integrator} and {@code finisher}.
     *
     * @param integrator the integrator function for the new gatherer
     * @param finisher the finisher function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if any argument is {@code null}
     * @return the new {@code Gatherer}
     */
    static <T, R> Gatherer<T, Void, R> of(
            Integrator<Void, T, R> integrator,
            BiConsumer<Void, Downstream<? super R>> finisher) {
        return of(
                defaultInitializer(),
                integrator,
                Gatherers.Value.DEFAULT.statelessCombiner,
                finisher
        );
    }

    /**
     * Returns a new, parallelizable, {@code Gatherer} described by the given
     * {@code initializer}, {@code integrator}, {@code combiner} and
     * {@code finisher}.
     *
     * @param initializer the initializer function for the new gatherer
     * @param integrator the integrator function for the new gatherer
     * @param combiner the combiner function for the new gatherer
     * @param finisher the finisher function for the new gatherer
     * @param <T> the type of input elements for the new gatherer
     * @param <A> the type of state for the new gatherer
     * @param <R> the type of results for the new gatherer
     * @throws NullPointerException if any argument is {@code null}
     * @return the new {@code Gatherer}
     */
    static <T, A, R> Gatherer<T, A, R> of(
            Supplier<A> initializer,
            Integrator<A, T, R> integrator,
            BinaryOperator<A> combiner,
            BiConsumer<A, Downstream<? super R>> finisher) {
        return new Gatherers.GathererImpl<>(
                Objects.requireNonNull(initializer),
                Objects.requireNonNull(integrator),
                Objects.requireNonNull(combiner),
                Objects.requireNonNull(finisher)
        );
    }

    /**
     * A Downstream object is the next stage in a pipeline of operations,
     * to which elements can be sent.
     * @param <T> the type of elements this downstream accepts
     * @since 22
     */
    @FunctionalInterface
    @PreviewFeature(feature = PreviewFeature.Feature.STREAM_GATHERERS)
    interface Downstream<T> {

        /**
         * Pushes, if possible, the provided element downstream -- to the next
         * stage in the pipeline.
         *
         * @implSpec If this method returns {@code false} then no further
         * elements will be accepted and subsequent invocations of this method
         * will return {@code false}.
         *
         * @param element the element to push downstream
         * @return {@code true} if more elements can be sent,
         *         and {@code false} if not.
         */
        boolean push(T element);

        /**
         * Checks whether the next stage is known to not want
         * any more elements sent to it.
         *
         * @apiNote This is best-effort only, once this returns {@code true} it
         * should never return {@code false} again for the same instance.
         *
         * @implSpec The implementation in this interface returns {@code false}.
         *
         * @return {@code true} if this Downstream is known not to want any
         *         more elements sent to it, {@code false} if otherwise
         */
        default boolean isRejecting() { return false; }
    }

    /**
     * An Integrator receives elements and processes them,
     * optionally using the supplied state, and optionally sends incremental
     * results downstream.
     *
     * @param <A> the type of state used by this integrator
     * @param <T> the type of elements this integrator consumes
     * @param <R> the type of results this integrator can produce
     * @since 22
     */
    @FunctionalInterface
    @PreviewFeature(feature = PreviewFeature.Feature.STREAM_GATHERERS)
    interface Integrator<A, T, R> {
        /**
         * Performs an action given: the current state, the next element, and
         * a downstream object; potentially inspecting and/or updating
         * the state, optionally sending any number of elements downstream
         * -- and then returns whether more elements are to be consumed or not.
         *
         * @param state The state to integrate into
         * @param element The element to integrate
         * @param downstream The downstream object of this integration
         * @return {@code true} if subsequent integration is desired,
         *         {@code false} if not
         */
        boolean integrate(A state, T element, Downstream<? super R> downstream);

        /**
         * Factory method for turning Integrator-shaped lambdas into
         * Integrators.
         *
         * @param integrator a lambda to create as Integrator
         * @return the given lambda as an Integrator
         * @param <A> the type of state used by this integrator
         * @param <T> the type of elements this integrator receives
         * @param <R> the type of results this integrator can produce
         */
        @ForceInline
        static <A, T, R> Integrator<A, T, R> of(Integrator<A, T, R> integrator) {
            return integrator;
        }

        /**
         * Factory method for turning Integrator-shaped lambdas into
         * {@link Greedy} Integrators.
         *
         * @param greedy a lambda to create as Integrator.Greedy
         * @return the given lambda as a Greedy Integrator
         * @param <A> the type of state used by this integrator
         * @param <T> the type of elements this integrator receives
         * @param <R> the type of results this integrator can produce
         */
        @ForceInline
        static <A, T, R> Greedy<A, T, R> ofGreedy(Greedy<A, T, R> greedy) {
            return greedy;
        }

        /**
         * Greedy Integrators consume all their input, and may only relay that
         * the downstream does not want more elements.
         *
         * @implSpec This interface is used to communicate that no
         * short-circuiting will be <i>initiated</i> by this Integrator, and that
         * information can then be used to optimize evaluation.
         *
         * @param <A> the type of state used by this integrator
         * @param <T> the type of elements this greedy integrator receives
         * @param <R> the type of results this greedy integrator can produce
         * @since 22
         */
        @FunctionalInterface
        @PreviewFeature(feature = PreviewFeature.Feature.STREAM_GATHERERS)
        interface Greedy<A, T, R> extends Integrator<A, T, R> { }
    }
}
