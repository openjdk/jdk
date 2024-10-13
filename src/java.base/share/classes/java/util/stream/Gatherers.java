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

import jdk.internal.access.SharedSecrets;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.vm.annotation.ForceInline;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Gatherer.Integrator;
import java.util.stream.Gatherer.Downstream;

/**
 * Implementations of {@link Gatherer} that provide useful intermediate
 * operations, such as windowing functions, folding functions,
 * transforming elements concurrently, etc.
 *
 * @since 22
*/
@PreviewFeature(feature = PreviewFeature.Feature.STREAM_GATHERERS)
public final class Gatherers {
    private Gatherers() { } // This class is not intended to be instantiated

    // Public built-in Gatherers and factory methods for them

    /**
     * Returns a Gatherer that gathers elements into windows
     * -- encounter-ordered groups of elements -- of a fixed size.
     * If the stream is empty then no window will be produced.
     * The last window may contain fewer elements than the supplied window size.
     *
     * <p>Example:
     * {@snippet lang = java:
     * // will contain: [[1, 2, 3], [4, 5, 6], [7, 8]]
     * List<List<Integer>> windows =
     *     Stream.of(1,2,3,4,5,6,7,8).gather(Gatherers.windowFixed(3)).toList();
     * }
     *
     * @implSpec Each window produced is an unmodifiable List; calls to any
     * mutator method will always cause {@code UnsupportedOperationException}
     * to be thrown. There are no guarantees on the implementation type or
     * serializability of the produced Lists.
     *
     * @apiNote For efficiency reasons, windows may be allocated contiguously
     *          and eagerly. This means that choosing large window sizes for
     *          small streams may use excessive memory for the duration of
     *          evaluation of this operation.
     *
     * @param windowSize the size of the windows
     * @param <TR> the type of elements the returned gatherer consumes
     *             and the contents of the windows it produces
     * @return a new gatherer which groups elements into fixed-size windows
     * @throws IllegalArgumentException when {@code windowSize} is less than 1
     */
    public static <TR> Gatherer<TR, ?, List<TR>> windowFixed(int windowSize) {
        if (windowSize < 1)
            throw new IllegalArgumentException("'windowSize' must be greater than zero");

        class FixedWindow {
            Object[] window;
            int at;

            FixedWindow() {
                at = 0;
                window = new Object[windowSize];
            }

            boolean integrate(TR element, Downstream<? super List<TR>> downstream) {
                window[at++] = element;
                if (at < windowSize) {
                    return true;
                } else {
                    final var oldWindow = window;
                    window = new Object[windowSize];
                    at = 0;
                    return downstream.push(
                        SharedSecrets.getJavaUtilCollectionAccess()
                                     .listFromTrustedArrayNullsAllowed(oldWindow)
                    );
                }
            }

            void finish(Downstream<? super List<TR>> downstream) {
                if (at > 0 && !downstream.isRejecting()) {
                    var lastWindow = new Object[at];
                    System.arraycopy(window, 0, lastWindow, 0, at);
                    window = null;
                    at = 0;
                    downstream.push(
                        SharedSecrets.getJavaUtilCollectionAccess()
                                     .listFromTrustedArrayNullsAllowed(lastWindow)
                    );
                }
            }
        }
        return Gatherer.<TR, FixedWindow, List<TR>>ofSequential(
                // Initializer
                FixedWindow::new,

                // Integrator
                Integrator.<FixedWindow, TR, List<TR>>ofGreedy(FixedWindow::integrate),

                // Finisher
                FixedWindow::finish
        );
    }

    /**
     * Returns a Gatherer that gathers elements into windows --
     * encounter-ordered groups of elements -- of a given size, where each
     * subsequent window includes all elements of the previous window except
     * for the least recent, and adds the next element in the stream.
     * If the stream is empty then no window will be produced. If the size of
     * the stream is smaller than the window size then only one window will
     * be produced, containing all elements in the stream.
     *
     * <p>Example:
     * {@snippet lang = java:
     * // will contain: [[1, 2], [2, 3], [3, 4], [4, 5], [5, 6], [6, 7], [7, 8]]
     * List<List<Integer>> windows2 =
     *     Stream.of(1,2,3,4,5,6,7,8).gather(Gatherers.windowSliding(2)).toList();
     *
     * // will contain: [[1, 2, 3, 4, 5, 6], [2, 3, 4, 5, 6, 7], [3, 4, 5, 6, 7, 8]]
     * List<List<Integer>> windows6 =
     *     Stream.of(1,2,3,4,5,6,7,8).gather(Gatherers.windowSliding(6)).toList();
     * }
     *
     * @implSpec Each window produced is an unmodifiable List; calls to any
     * mutator method will always cause {@code UnsupportedOperationException}
     * to be thrown. There are no guarantees on the implementation type or
     * serializability of the produced Lists.
     *
     * @apiNote For efficiency reasons, windows may be allocated contiguously
     *          and eagerly. This means that choosing large window sizes for
     *          small streams may use excessive memory for the duration of
     *          evaluation of this operation.
     *
     * @param windowSize the size of the windows
     * @param <TR> the type of elements the returned gatherer consumes
     *             and the contents of the windows it produces
     * @return a new gatherer which groups elements into sliding windows
     * @throws IllegalArgumentException when windowSize is less than 1
     */
    public static <TR> Gatherer<TR, ?, List<TR>> windowSliding(int windowSize) {
        if (windowSize < 1)
            throw new IllegalArgumentException("'windowSize' must be greater than zero");

        class SlidingWindow {
            Object[] window;
            int at;
            boolean firstWindow;

            SlidingWindow() {
                firstWindow = true;
                at = 0;
                window = new Object[windowSize];
            }

            boolean integrate(TR element, Downstream<? super List<TR>> downstream) {
                window[at++] = element;
                if (at < windowSize) {
                    return true;
                } else {
                    final var oldWindow = window;
                    final var newWindow = new Object[windowSize];
                    System.arraycopy(oldWindow,1, newWindow, 0, windowSize - 1);
                    window = newWindow;
                    at -= 1;
                    firstWindow = false;
                    return downstream.push(
                        SharedSecrets.getJavaUtilCollectionAccess()
                                     .listFromTrustedArrayNullsAllowed(oldWindow)
                    );
                }
            }

            void finish(Downstream<? super List<TR>> downstream) {
                if (firstWindow && at > 0 && !downstream.isRejecting()) {
                    var lastWindow = new Object[at];
                    System.arraycopy(window, 0, lastWindow, 0, at);
                    window = null;
                    at = 0;
                    downstream.push(
                        SharedSecrets.getJavaUtilCollectionAccess()
                                     .listFromTrustedArrayNullsAllowed(lastWindow)
                    );
                }
            }
        }
        return Gatherer.<TR, SlidingWindow, List<TR>>ofSequential(
                // Initializer
                SlidingWindow::new,

                // Integrator
                Integrator.<SlidingWindow, TR, List<TR>>ofGreedy(SlidingWindow::integrate),

                // Finisher
                SlidingWindow::finish
        );
    }

    /**
     * Returns a Gatherer that performs an ordered, <i>reduction-like</i>,
     * transformation for scenarios where no combiner-function can be
     * implemented, or for reductions which are intrinsically
     * order-dependent.
     *
     * @implSpec If no exceptions are thrown during processing, then this
     * operation only ever produces a single element.
     *
     * <p>Example:
     * {@snippet lang = java:
     * // will contain: Optional["123456789"]
     * Optional<String> numberString =
     *     Stream.of(1,2,3,4,5,6,7,8,9)
     *           .gather(
     *               Gatherers.fold(() -> "", (string, number) -> string + number)
     *            )
     *           .findFirst();
     * }
     *
     * @see java.util.stream.Stream#reduce(Object, BinaryOperator)
     *
     * @param initial the identity value for the fold operation
     * @param folder the folding function
     * @param <T> the type of elements the returned gatherer consumes
     * @param <R> the type of elements the returned gatherer produces
     * @return a new Gatherer
     * @throws NullPointerException if any of the parameters are {@code null}
     */
    public static <T, R> Gatherer<T, ?, R> fold(
            Supplier<R> initial,
            BiFunction<? super R, ? super T, ? extends R> folder) {
        Objects.requireNonNull(initial, "'initial' must not be null");
        Objects.requireNonNull(folder, "'folder' must not be null");

        class State {
            R value = initial.get();
            State() {}
        }

        return Gatherer.ofSequential(
                State::new,
                Integrator.ofGreedy((state, element, downstream) -> {
                    state.value = folder.apply(state.value, element);
                    return true;
                }),
                (state, downstream) -> downstream.push(state.value)
        );
    }

    /**
     * Returns a Gatherer that performs a Prefix Scan -- an incremental
     * accumulation -- using the provided functions.  Starting with an
     * initial value obtained from the {@code Supplier}, each subsequent
     * value is obtained by applying the {@code BiFunction} to the current
     * value and the next input element, after which the resulting value is
     * produced downstream.
     *
     * <p>Example:
     * {@snippet lang = java:
     * // will contain: ["1", "12", "123", "1234", "12345", "123456", "1234567", "12345678", "123456789"]
     * List<String> numberStrings =
     *     Stream.of(1,2,3,4,5,6,7,8,9)
     *           .gather(
     *               Gatherers.scan(() -> "", (string, number) -> string + number)
     *            )
     *           .toList();
     * }
     *
     * @param initial the supplier of the initial value for the scanner
     * @param scanner the function to apply for each element
     * @param <T> the type of element which this gatherer consumes
     * @param <R> the type of element which this gatherer produces
     * @return a new Gatherer which performs a prefix scan
     * @throws NullPointerException if any of the parameters are {@code null}
     */
    public static <T, R> Gatherer<T, ?, R> scan(
            Supplier<R> initial,
            BiFunction<? super R, ? super T, ? extends R> scanner) {
        Objects.requireNonNull(initial, "'initial' must not be null");
        Objects.requireNonNull(scanner, "'scanner' must not be null");

        class State {
            R current = initial.get();
            boolean integrate(T element, Downstream<? super R> downstream) {
                return downstream.push(current = scanner.apply(current, element));
            }
        }

        return Gatherer.ofSequential(State::new,
                Integrator.<State,T, R>ofGreedy(State::integrate));
    }

    /**
     * An operation which executes a function concurrently
     * with a configured level of max concurrency, using
     * <a href="{@docRoot}/java.base/java/lang/Thread.html#virtual-threads">virtual threads</a>.
     * This operation preserves the ordering of the stream.
     *
     * @apiNote In progress tasks will be attempted to be cancelled,
     * on a best-effort basis, in situations where the downstream no longer
     * wants to receive any more elements.
     *
     * @implSpec If a result of the function is to be pushed downstream but
     * instead the function completed exceptionally then the corresponding
     * exception will instead be rethrown by this method as an instance of
     * {@link RuntimeException}, after which any remaining tasks are canceled.
     *
     * @param maxConcurrency the maximum concurrency desired
     * @param mapper a function to be executed concurrently
     * @param <T> the type of input
     * @param <R> the type of output
     * @return a new Gatherer
     * @throws IllegalArgumentException if {@code maxConcurrency} is less than 1
     * @throws NullPointerException if {@code mapper} is {@code null}
     */
    public static <T, R> Gatherer<T,?,R> mapConcurrent(
            final int maxConcurrency,
            final Function<? super T, ? extends R> mapper) {
        if (maxConcurrency < 1)
            throw new IllegalArgumentException(
                    "'maxConcurrency' must be greater than 0");

        Objects.requireNonNull(mapper, "'mapper' must not be null");

        class State {
            // ArrayDeque default initial size is 16
            final ArrayDeque<Future<R>> window =
                    new ArrayDeque<>(Math.min(maxConcurrency, 16));
            final Semaphore windowLock = new Semaphore(maxConcurrency);

            final boolean integrate(T element,
                                    Downstream<? super R> downstream) {
                if (!downstream.isRejecting())
                    createTaskFor(element);
                return flush(0, downstream);
            }

            final void createTaskFor(T element) {
                windowLock.acquireUninterruptibly();

                var task = new FutureTask<R>(() -> {
                    try {
                        return mapper.apply(element);
                    } finally {
                        windowLock.release();
                    }
                });

                var wasAddedToWindow = window.add(task);
                assert wasAddedToWindow;

                Thread.startVirtualThread(task);
            }

            final boolean flush(long atLeastN,
                                Downstream<? super R> downstream) {
                boolean proceed = !downstream.isRejecting();
                boolean interrupted = false;
                try {
                    Future<R> current;
                    while (proceed
                            && (current = window.peek()) != null
                                && (current.isDone() || atLeastN > 0)) {
                        proceed &= downstream.push(current.get());
                        atLeastN -= 1;

                        var correctRemoval = window.pop() == current;
                        assert correctRemoval;
                    }
                } catch(InterruptedException ie) {
                    proceed = false;
                    interrupted = true;
                } catch (ExecutionException e) {
                    proceed = false; // Ensure cleanup
                    final var cause = e.getCause();
                    throw (cause instanceof RuntimeException re)
                              ? re
                              : new RuntimeException(cause == null ? e : cause);
                } finally {
                    // Clean up
                    if (!proceed) {
                        Future<R> next;
                        while ((next = window.pollFirst()) != null) {
                            next.cancel(true);
                        }
                    }
                }

                if (interrupted)
                    Thread.currentThread().interrupt();

                return proceed;
            }
        }

        return Gatherer.ofSequential(
                State::new,
                Integrator.<State, T, R>ofGreedy(State::integrate),
                (state, downstream) -> state.flush(Long.MAX_VALUE, downstream)
        );
    }

    // Implementation details

    /*
     * This enum is used to provide the default functions for the
     * factory methods
     * and for the default methods for when implementing the Gatherer interface.
     *
     * This serves the following purposes:
     * 1. removes the need for using `null` for signalling absence of specified
     *    value and thereby hiding user bugs
     * 2. allows to check against these default values to avoid calling methods
     *    needlessly
     * 3. allows for more efficient composition and evaluation
     */
    @SuppressWarnings("rawtypes")
    enum Value implements Supplier, BinaryOperator, BiConsumer {
        DEFAULT;

        final BinaryOperator<Void> statelessCombiner = new BinaryOperator<>() {
            @Override public Void apply(Void left, Void right) { return null; }
        };

        // BiConsumer
        @Override public void accept(Object state, Object downstream) {}

        // BinaryOperator
        @Override public Object apply(Object left, Object right) {
            throw new UnsupportedOperationException("This combiner cannot be used!");
        }

        // Supplier
        @Override public Object get() { return null; }

        @ForceInline
        @SuppressWarnings("unchecked")
        <A> Supplier<A> initializer() { return (Supplier<A>)this; }

        @ForceInline
        @SuppressWarnings("unchecked")
        <T> BinaryOperator<T> combiner() { return (BinaryOperator<T>) this; }

        @ForceInline
        @SuppressWarnings("unchecked")
        <T, R> BiConsumer<T, Gatherer.Downstream<? super R>> finisher() {
            return (BiConsumer<T, Downstream<? super R>>) this;
        }
    }

    record GathererImpl<T, A, R>(
            @Override Supplier<A> initializer,
            @Override Integrator<A, T, R> integrator,
            @Override BinaryOperator<A> combiner,
            @Override BiConsumer<A, Downstream<? super R>> finisher) implements Gatherer<T, A, R> {

        static <T, A, R> GathererImpl<T, A, R> of(
                Supplier<A> initializer,
                Integrator<A, T, R> integrator,
                BinaryOperator<A> combiner,
                BiConsumer<A, Downstream<? super R>> finisher) {
            return new GathererImpl<>(
                    Objects.requireNonNull(initializer,"initializer"),
                    Objects.requireNonNull(integrator, "integrator"),
                    Objects.requireNonNull(combiner, "combiner"),
                    Objects.requireNonNull(finisher, "finisher")
            );
        }
    }

    static final class Composite<T, A, R, AA, RR> implements Gatherer<T, Object, RR> {
        private final Gatherer<T, A, ? extends R> left;
        private final Gatherer<? super R, AA, ? extends RR> right;
        // FIXME change `impl` to a computed constant when available
        private GathererImpl<T, Object, RR> impl;

        static <T, A, R, AA, RR> Composite<T, A, R, AA, RR> of(
                Gatherer<T, A, ? extends R> left,
                Gatherer<? super R, AA, ? extends RR> right) {
            return new Composite<>(left, right);
        }

        private Composite(Gatherer<T, A, ? extends R> left,
                          Gatherer<? super R, AA, ? extends RR> right) {
            this.left = left;
            this.right = right;
        }

        @SuppressWarnings("unchecked")
        private GathererImpl<T, Object, RR> impl() {
            // ATTENTION: this method currently relies on a "benign" data-race
            // as it should deterministically produce the same result even if
            // initialized concurrently on different threads.
            var i = impl;
            return i != null
                     ? i
                     : (impl = (GathererImpl<T, Object, RR>)impl(left, right));
        }

        @Override public Supplier<Object> initializer() {
            return impl().initializer();
        }

        @Override public Integrator<Object, T, RR> integrator() {
            return impl().integrator();
        }

        @Override public BinaryOperator<Object> combiner() {
            return impl().combiner();
        }

        @Override public BiConsumer<Object, Downstream<? super RR>> finisher() {
            return impl().finisher();
        }

        @Override
        public <RRR> Gatherer<T, ?, RRR> andThen(
                Gatherer<? super RR, ?, ? extends RRR> that) {
            if (that.getClass() == Composite.class) {
                @SuppressWarnings("unchecked")
                final var c =
                    (Composite<? super RR, ?, Object, ?, ? extends RRR>) that;
                return left.andThen(right.andThen(c.left).andThen(c.right));
            } else {
                return left.andThen(right.andThen(that));
            }
        }

        static final <T, A, R, AA, RR> GathererImpl<T, ?, RR> impl(
                Gatherer<T, A, R> left, Gatherer<? super R, AA, RR> right) {
            final var leftInitializer = left.initializer();
            final var leftIntegrator = left.integrator();
            final var leftCombiner = left.combiner();
            final var leftFinisher = left.finisher();

            final var rightInitializer = right.initializer();
            final var rightIntegrator = right.integrator();
            final var rightCombiner = right.combiner();
            final var rightFinisher = right.finisher();

            final var leftStateless = leftInitializer == Gatherer.defaultInitializer();
            final var rightStateless = rightInitializer == Gatherer.defaultInitializer();

            final var leftGreedy = leftIntegrator instanceof Integrator.Greedy;
            final var rightGreedy = rightIntegrator instanceof Integrator.Greedy;

            /*
             * For pairs of stateless and greedy Gatherers, we can optimize
             * evaluation as we do not need to track any state nor any
             * short-circuit signals. This can provide significant
             * performance improvements.
             */
            if (leftStateless && rightStateless && leftGreedy && rightGreedy) {
                return new GathererImpl<>(
                    Gatherer.defaultInitializer(),
                    Gatherer.Integrator.ofGreedy((unused, element, downstream) ->
                        leftIntegrator.integrate(
                                null,
                                element,
                                r -> rightIntegrator.integrate(null, r, downstream))
                    ),
                    (leftCombiner == Gatherer.defaultCombiner()
                    || rightCombiner == Gatherer.defaultCombiner())
                            ? Gatherer.defaultCombiner()
                            : Value.DEFAULT.statelessCombiner
                    ,
                    (leftFinisher == Gatherer.<A,R>defaultFinisher()
                    && rightFinisher == Gatherer.<AA,RR>defaultFinisher())
                            ? Gatherer.defaultFinisher()
                            : (unused, downstream) -> {
                        if (leftFinisher != Gatherer.<A,R>defaultFinisher())
                            leftFinisher.accept(
                                    null,
                                    r -> rightIntegrator.integrate(null, r, downstream));
                        if (rightFinisher != Gatherer.<AA,RR>defaultFinisher())
                            rightFinisher.accept(null, downstream);
                    }
                );
            } else {
                class State {
                    final A leftState;
                    final AA rightState;
                    boolean leftProceed;
                    boolean rightProceed;

                    private State(A leftState, AA rightState,
                                  boolean leftProceed, boolean rightProceed) {
                        this.leftState = leftState;
                        this.rightState = rightState;
                        this.leftProceed = leftProceed;
                        this.rightProceed = rightProceed;
                    }

                    State() {
                        this(leftStateless ? null : leftInitializer.get(),
                             rightStateless ? null : rightInitializer.get(),
                            true, true);
                    }

                    State joinLeft(State right) {
                        return new State(
                                leftStateless ? null : leftCombiner.apply(this.leftState, right.leftState),
                                rightStateless ? null : rightCombiner.apply(this.rightState, right.rightState),
                                this.leftProceed && this.rightProceed,
                                right.leftProceed && right.rightProceed);
                    }

                    boolean integrate(T t, Downstream<? super RR> c) {
                        /*
                         * rightProceed must be checked after integration of
                         * left since that can cause right to short-circuit
                         * We always want to conditionally write leftProceed
                         * here, which means that we only do so if we are
                         * known to be not-greedy.
                         */
                        return (leftIntegrator.integrate(leftState, t, r -> rightIntegrate(r, c))
                                  || leftGreedy
                                  || (leftProceed = false))
                                && (rightGreedy || rightProceed);
                    }

                    void finish(Downstream<? super RR> c) {
                        if (leftFinisher != Gatherer.<A, R>defaultFinisher())
                            leftFinisher.accept(leftState, r -> rightIntegrate(r, c));
                        if (rightFinisher != Gatherer.<AA, RR>defaultFinisher())
                            rightFinisher.accept(rightState, c);
                    }

                    /*
                     * Currently we use the following to ferry elements from
                     * the left Gatherer to the right Gatherer, but we create
                     * the Gatherer.Downstream as a lambda which means that
                     * the default implementation of `isKnownDone()` is used.
                     *
                     * If it is determined that we want to be able to support
                     * the full interface of Gatherer.Downstream then we have
                     *  the following options:
                     *    1. Have State implement Downstream<? super R>
                     *       and store the passed in Downstream<? super RR>
                     *       downstream as an instance field in integrate()
                     *       and read it in push(R r).
                     *    2. Allocate a new Gatherer.Downstream<? super R> for
                     *       each invocation of integrate() which might prove
                     *       costly.
                     */
                    public boolean rightIntegrate(R r, Downstream<? super RR> downstream) {
                        // The following logic is highly performance sensitive
                        return (rightGreedy || rightProceed)
                                && (rightIntegrator.integrate(rightState, r, downstream)
                                || rightGreedy
                                || (rightProceed = false));
                    }
                }

                return new GathererImpl<T, State, RR>(
                        State::new,
                        (leftGreedy && rightGreedy)
                                ? Integrator.<State, T, RR>ofGreedy(State::integrate)
                                : Integrator.<State, T, RR>of(State::integrate),
                        (leftCombiner == Gatherer.defaultCombiner()
                        || rightCombiner == Gatherer.defaultCombiner())
                                ? Gatherer.defaultCombiner()
                                : State::joinLeft,
                        (leftFinisher == Gatherer.<A, R>defaultFinisher()
                        && rightFinisher == Gatherer.<AA, RR>defaultFinisher())
                                ? Gatherer.defaultFinisher()
                                : State::finish
                );
            }
        }
    }
}
