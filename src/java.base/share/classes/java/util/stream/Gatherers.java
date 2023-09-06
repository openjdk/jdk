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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Gatherer.Integrator;
import java.util.stream.Gatherer.Sink;

/**
 * Implementations of {@link Gatherer} that implement various useful intermediate
 * operations, such as windowing functions, folding functions, transforming elements
 * concurrently, etc.
*/
public final class Gatherers {
    private Gatherers() { }

    /*
     * Important constants
     */
    final static BinaryOperator<Object> combinerNotPossible = new BinaryOperator<Object>() {
        @Override
        public Object apply(Object o, Object o2) {
            throw new UnsupportedOperationException("Gatherer.combinerNotPossible() is not intended to be called!");
        }
    };

    final static Supplier<Object> initializerNotNeeded = new Supplier<Object>() {
        @Override public Object get() { return null; }
    };

    @SuppressWarnings("rawtypes")
    final static BiConsumer finisherNotNeeded = new BiConsumer() {
        @Override public void accept(Object l, Object r) {}
    };

    @SuppressWarnings({"unchecked"})
    static <A> Supplier<A> initializerNotNeeded() {
        return (Supplier<A>) initializerNotNeeded;
    }

    @SuppressWarnings({"unchecked", "cast"})
    static <T, R> BiConsumer<T, Sink<? super R>> finisherNotNeeded() {
        return (BiConsumer<T, Sink<? super R>>) finisherNotNeeded;
    }

    @SuppressWarnings("unchecked")
    static <T> BinaryOperator<T> combinerNotPossible() {
        return (BinaryOperator<T>) combinerNotPossible;
    }

    record ThenCollectorImpl<T,A,R,AA,RR,AAA>(Gatherer<T,A,R> gatherer,
                                              Collector<R, AA, RR> collector,
                                              Supplier<AAA> supplier,
                                              BiConsumer<AAA, T> accumulator,
                                              BinaryOperator<AAA> combiner,
                                              Function<AAA, RR> finisher,
                                              Set<Characteristics> characteristics
    ) implements Gatherer.ThenCollector<T,A,R,AA,RR,AAA> {

        static <T,A,R,AA,RR> Gatherer.ThenCollector<T, A, R, AA, RR, ?> of(Gatherer<T,A,R> gatherer, Collector<R, AA, RR> collector) {
            Objects.requireNonNull(gatherer);
            Objects.requireNonNull(collector);

            if (collector instanceof Gatherer.ThenCollector) {
                @SuppressWarnings("unchecked")
                var thenCollector = (Gatherer.ThenCollector<R,Object,Object,Object,RR,AA>)collector;
                @SuppressWarnings("unchecked")
                var result = (Gatherer.ThenCollector<T, A, R, AA, RR, ?>)of(gatherer.andThen(thenCollector.gatherer()), thenCollector.collector());
                return result;
            }

            final var gathererInitializer = gatherer.initializer();
            final var gathererIntegrator = gatherer.integrator();
            final var gathererCombiner = gatherer.combiner();
            final var gathererFinisher = gatherer.finisher();
            final var collectorSupplier = collector.supplier();
            final var collectorAccumulator = collector.accumulator();
            final var collectorCombiner = collector.combiner();
            final var collectorFinisher = collector.finisher();
            final var collectorCharacteristics = collector.characteristics();

            final var stateless = gathererInitializer == initializerNotNeeded;
            final var greedy = gathererIntegrator instanceof Integrator.Greedy;

            class GathererCollectorState implements Gatherer.Sink<R> {
                final A gathererState;
                final AA collectorState;
                boolean proceed;
                private GathererCollectorState(A gathererState, AA collectorState) {
                    this.gathererState = gathererState;
                    this.collectorState = collectorState;
                    this.proceed = true;
                }

                GathererCollectorState() { this(stateless ? null : gathererInitializer.get(), collectorSupplier.get()); }

                public void accept(T t) {
                    var ignored = (greedy || proceed) && (gathererIntegrator.integrate(gathererState, t, this) || (greedy || !(proceed = false)));
                }

                public GathererCollectorState combine(GathererCollectorState right) {
                    return new GathererCollectorState(
                            gathererCombiner.apply(this.gathererState, right.gathererState),
                            collectorCombiner.apply(this.collectorState, right.collectorState)
                    );
                }

                @Override
                public boolean flush(R r) {
                    collectorAccumulator.accept(collectorState, r);
                    return true;
                }

                @SuppressWarnings("unchecked")
                public RR finish() {
                    if (gathererFinisher != finisherNotNeeded)
                        gathererFinisher.accept(this.gathererState, this);

                    return collectorCharacteristics.contains(Characteristics.IDENTITY_FINISH)
                                ? (RR)this.collectorState
                                : collectorFinisher.apply(this.collectorState);
                }
            }

            return new ThenCollectorImpl<>(gatherer,
                    collector,
                    GathererCollectorState::new,
                    GathererCollectorState::accept,
                    gathererCombiner != combinerNotPossible ? GathererCollectorState::combine : combinerNotPossible(),
                    GathererCollectorState::finish,
                    Set.of());
        }
    }

    record GathererImpl<T, A, R>(@Override Supplier<A> initializer,
                                 @Override Integrator<A, T, R> integrator,
                                 @Override BinaryOperator<A> combiner,
                                 @Override BiConsumer<A, Sink<? super R>> finisher) implements Gatherer<T, A, R> {

        static <T,A,R> GathererImpl<T,A,R> of(Supplier<A> initializer,
                                      Integrator<A, T, R> integrator,
                                      BinaryOperator<A> combiner,
                                      BiConsumer<A, Sink<? super R>> finisher) {
            return new GathererImpl<>(
                    Objects.requireNonNull(initializer),
                    Objects.requireNonNull(integrator),
                    Objects.requireNonNull(combiner),
                    Objects.requireNonNull(finisher)
            );
        }
    }

    final static class Composite<T, A, R, AA, RR> implements Gatherer<T, Object, RR> {
        private final Gatherer<T, A, R> left;
        private final Gatherer<R, AA, RR> right;
        // FIXME change to a computed constant when available
        private GathererImpl<T, ? extends Object, RR> impl;

        static <T, A, R, AA, RR> Composite<T, A, R, AA, RR> of(Gatherer<T, A, R> left, Gatherer<R, AA, RR> right) {
            return new Composite<>(left, right);
        }

        private Composite(Gatherer<T,A,R> left, Gatherer<R,AA,RR> right) {
            this.left = left;
            this.right = right;
        }

        @SuppressWarnings("unchecked")
        private GathererImpl<T, Object, RR> impl() {
            // ATTENTION: this method currently relies on a "benign" data-race
            // as it should deterministically produce the same result even if
            // initialized concurrently on different threads.
            var i = impl;
            return (GathererImpl<T,Object,RR>)(i != null ? i : (impl = impl(left, right)));
        }

        @Override public Supplier<Object> initializer() { return impl().initializer(); }
        @Override public Integrator<Object, T, RR> integrator() { return impl().integrator(); }
        @Override public BinaryOperator<Object> combiner() { return impl().combiner(); }
        @Override public BiConsumer<Object, Sink<? super RR>> finisher() { return impl().finisher(); }

        public <OO, XX1> Gatherer<T, ?, XX1> andThen(Gatherer<RR, OO, XX1> that) {
            if (that.getClass() == Composite.class) { // Implicit null-check of `that`
                @SuppressWarnings("unchecked")
                var composedThat = (Composite<RR,?,Object,?,XX1>)that;
                return left.andThen(right.andThen(composedThat.left).andThen(composedThat.right)); // This order tends to perform better
            } else return left.andThen(right.andThen(that));
        }

        @SuppressWarnings("unchecked")
        static final <T, A, R, AA, RR> GathererImpl<T,? extends Object,RR> impl(Gatherer<T, A, R> left, Gatherer<R, AA, RR> right) {
            final var leftInitializer = left.initializer();
            final var leftIntegrator = left.integrator();
            final var leftCombiner = left.combiner();
            final var leftFinisher = left.finisher();

            final var rightInitializer = right.initializer();
            final var rightIntegrator = right.integrator();
            final var rightCombiner = right.combiner();
            final var rightFinisher = right.finisher();

            final var leftStateless = leftInitializer == initializerNotNeeded;
            final var rightStateless = rightInitializer == initializerNotNeeded;

            final var leftGreedy = leftIntegrator instanceof Integrator.Greedy<A, T, R>;
            final var rightGreedy = rightIntegrator instanceof Integrator.Greedy<AA, R, RR>;

            if (leftStateless && rightStateless && leftGreedy && rightGreedy) { // Fast-path for stateless+greedy composites
                return new GathererImpl<>(
                        initializerNotNeeded(),
                        Gatherer.Integrator.ofGreedy((unused, element, downstream) ->
                            leftIntegrator.integrate(null, element, r -> rightIntegrator.integrate(null, r, downstream))
                        ),
                        (leftCombiner == combinerNotPossible || rightCombiner == combinerNotPossible)
                                ? combinerNotPossible()
                                : (l,r) -> {
                                        leftCombiner.apply(null, null);
                                        rightCombiner.apply(null, null);
                                        return null;
                        },
                        (leftFinisher == finisherNotNeeded && rightFinisher == finisherNotNeeded)
                                ? finisherNotNeeded()
                                : (unused, downstream) -> {
                            if (leftFinisher != finisherNotNeeded)
                                leftFinisher.accept(null, r -> rightIntegrator.integrate(null, r, downstream));
                            if (rightFinisher != finisherNotNeeded)
                                rightFinisher.accept(null, downstream);
                        }
                );
            } else {
                class State {
                    final A leftState;
                    final AA rightState;
                    boolean leftProceed;
                    boolean rightProceed;

                    private State(A leftState, AA rightState, boolean leftProceed, boolean rightProceed) {
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

                    boolean integrate(T t, Sink<? super RR> c) {
                        return (leftIntegrator.integrate(leftState, t, r -> rightIntegrate(r, c)) || leftGreedy || (leftProceed = false)) && (rightGreedy || rightProceed);
                        // rightProceed must be checked after integration of left since that can cause right to short-circuit
                    }

                    void finish(Sink<? super RR> c) {
                        if (leftFinisher != finisherNotNeeded)
                            leftFinisher.accept(leftState, r -> rightIntegrate(r, c));
                        if (rightFinisher != finisherNotNeeded)
                            rightFinisher.accept(rightState, c);
                    }

                    public boolean rightIntegrate(R r, Sink<? super RR> downstream) {
                        return (rightGreedy || rightProceed) && (rightIntegrator.integrate(rightState, r, downstream) || rightGreedy || (rightProceed = false));
                    }

                /*
                FIXME might need to have State implement Sink<R>
                //Sink<? super RR> downstream;

                @Override
                public boolean flush(R r) {
                    return (rightGreedy || rightProceed) && (rightIntegrator.integrate(rightState, r, null) || rightGreedy || (rightProceed = false));
                }
                @Override
                public boolean isKnownDone() {
                    return !rightGreedy && !rightProceed;
                }*/
                }

                return new GathererImpl<T, State, RR>(
                        State::new,
                        (leftGreedy && rightGreedy)
                                ? Integrator.<State, T, RR>ofGreedy(State::integrate)
                                : Integrator.<State, T, RR>of(State::integrate),
                        (leftCombiner == combinerNotPossible || rightCombiner == combinerNotPossible)
                                ? combinerNotPossible()
                                : State::joinLeft,
                        (leftFinisher == finisherNotNeeded && rightFinisher == finisherNotNeeded)
                                ? finisherNotNeeded()
                                : State::finish
                );
            }
        }
    }

    // Public built-in Gatherers and factory methods for them

    /**
     * Gathers elements into fixed-size groups. The last group may contain
     * fewer elements than the supplied group size.
     *
     * <p>Example:
     * {@snippet lang = java:
     * // will contain: [[1, 2, 3], [4, 5, 6], [7, 8]]
     * List<List<Integer>> groups =
     *     Stream.of(1,2,3,4,5,6,7,8).gather(Gatherers.grouped(3)).toList();
     * }
     *
     * @param groupSize the size of the groups
     * @param <TR> the type of elements the returned gatherer consumes and produces
     * @return a new gatherer which groups elements into fixed-size groups
     * @throws IllegalArgumentException when groupSize is less than 1
     */
    public static <TR> Gatherer<TR, ?, List<TR>> grouped(int groupSize) {
        if (groupSize < 1)
            throw new IllegalArgumentException("'groupSize' must be greater than zero");

        return Gatherer.ofSequential(
                // Initializer
                () -> new ArrayList<>(groupSize),

                // Integrator
                Integrator.<ArrayList<TR>,TR,List<TR>>ofGreedy((acc, e, sink) -> {
                    acc.add(e);
                    if (acc.size() < groupSize) {
                        return true;
                    } else {
                        var group = List.copyOf(acc);
                        acc.clear();
                        return sink.flush(group);
                    }
                }),

                // Finisher
                (acc, sink) -> {
                    if(!sink.isKnownDone() && !acc.isEmpty())
                        sink.flush(List.copyOf(acc));
                    acc.clear();
                }
        );
    }

    /**
     * Gathers elements into fixed-size groups, sliding out the last element
     * and sliding in a new element for each group. The last group may contain
     * fewer elements than the supplied group size.
     *
     * <p>Example:
     * {@snippet lang = java:
     * // will contain: [[1, 2], [2, 3], [3, 4], [4, 5], [5, 6], [6, 7], [7, 8], [8]]
     * List<List<Integer>> groups =
     *     Stream.of(1,2,3,4,5,6,7,8).gather(Gatherers.sliding(2)).toList();
     * }
     *
     * @param groupSize the size of the groups
     * @param <TR> the type of elements the returned gatherer consumes
     *             and produces
     * @return a new gatherer which groups elements into fixed-size groups
     *         with sliding window semantics
     * @throws IllegalArgumentException when groupSize is less than 1
     */
    public static <TR> Gatherer<TR, ?, List<TR>> sliding(int groupSize) {
        if (groupSize < 1)
            throw new IllegalArgumentException("'groupSize' must be greater than zero");

        return Gatherer.ofSequential(
                // Initializer
                () -> new ArrayDeque<>(groupSize),

                // Integrator
                Integrator.<ArrayDeque<TR>,TR,List<TR>>ofGreedy((acc, e, consumer) -> {
                    acc.addLast(e);
                    if (acc.size() < groupSize) {
                        return true;
                    } else {
                        var group = List.copyOf(acc);
                        acc.removeFirst();
                        return consumer.flush(group);
                    }
                }),

                // Finisher
                (acc, sink) -> {
                    if(!sink.isKnownDone() && !acc.isEmpty())
                        sink.flush(List.copyOf(acc));
                    acc.clear();
                }
        );
    }

    /**
     * An operation which performs an ordered, <i>reduction-like</i>,
     * transformation for scenarios where no combiner-function can be
     * implemented, or for reductions which are intrinsically
     * order-dependent.
     *
     * <p>This operation always emits a single resulting element.
     *
     * @see java.util.stream.Stream#reduce(Object, BinaryOperator)
     *
     * @param initial the identity value for the fold operation
     * @param folder the folding function
     * @param <T> the type of elements the returned gatherer consumes
     * @param <R> the type of elements the returned gatherer produces
     * @return a new Gatherer
     * @throws NullPointerException if any of the parameters are null
     */
    public static <T, R> Gatherer<T, ?, R> fold(Supplier<R> initial,
                                                    BiFunction<? super R, ? super T, ? extends R> folder) {
        Objects.requireNonNull(initial, "'initial' must not be null");
        Objects.requireNonNull(folder, "'folder' must not be null");

        class State {
            R value = initial.get();
            State() {}
        }

        return Gatherer.ofSequential(
                State::new,
                Integrator.ofGreedy((state, element, sink) -> {
                    state.value = folder.apply(state.value, element);
                    return true;
                }),
                (state, sink) -> sink.flush(state.value)
        );
    }

    /**
     * Runs an effect for each element which passes through this gatherer,
     * in the order in which they appear in the stream.
     *
     * <p>This operation produces the same elements it consumes,
     * and preserves ordering.
     *
     * @see #peek(Consumer)
     *
     * @param effect the effect to execute with the current element
     * @param <TR> the type of elements the returned gatherer consumes and produces
     * @return a new gatherer which executes an effect, in order, for each element which passes through it
     * @throws NullPointerException if the provided effect is null
     */
    public static <TR> Gatherer<TR,?, TR> peekOrdered(final Consumer<? super TR> effect) {
        Objects.requireNonNull(effect, "'effect' must not be null");

        class PeekOrdered implements Gatherer<TR,Void,TR>, Integrator.Greedy<Void,TR,TR> {
            // Integrator
            @Override public Integrator<Void, TR, TR> integrator() { return this; }
            // Integrator implementation
            @Override
            public boolean integrate(Void state, TR element, Sink<? super TR> sink) {
                effect.accept(element);
                return sink.flush(element);
            }
        }

        return new PeekOrdered();
    }

    /**
     * Runs an effect for each element which passes through this gatherer,
     * in the order in which they are processed -- which in the case of parallel
     * evaluation can be out-of-sequence compared to the sequential encounter order
     * of the stream.
     * 
     * @see #peekOrdered(Consumer) 
     * 
     * @param effect the effect to execute with the current element
     * @param <TR> the type of elements the returned gatherer consumes and produces
     * @return a new gatherer which executes an effect for each element which passes through it
     */
    public static <TR> Gatherer<TR,?, TR> peek(final Consumer<? super TR> effect) {
        Objects.requireNonNull(effect, "'effect' must not be null");

        class Peek implements Gatherer<TR, Void, TR>, Integrator.Greedy<Void, TR, TR>, BinaryOperator<Void> {
            // Integrator
            @Override public Integrator<Void, TR, TR> integrator() { return this; }
            // Integrator implementation
            @Override
            public boolean integrate(Void state, TR element, Sink<? super TR> sink) {
                effect.accept(element);
                return sink.flush(element);
            }

            // Combiner
            @Override public BinaryOperator<Void> combiner() { return this; }
            // Combiner implementation
            @Override public Void apply(Void unused, Void unused2) { return unused; }
        }

        return new Peek();
    }

    /**
     * Performs a prefix scan -- an incremental accumulation, using the
     * provided functions.
     *
     * @param initial the supplier of the initial value for the scanner
     * @param scanner the function to apply for each element
     * @param <T> the type of element which this gatherer consumes
     * @param <R> the type of element which this gatherer produces
     * @return a new Gatherer which performs a prefix scan
     * @throws NullPointerException if any of the parameters are null
     */
    @SuppressWarnings("unchecked")
    public static <T,R> Gatherer<T,?,R> scan(Supplier<R> initial, BiFunction<? super R, ? super T, ? extends R> scanner) {
        Objects.requireNonNull(initial, "'initial' must not be null");
        Objects.requireNonNull(scanner, "'scanner' must not be null");

        class State {
            R current = initial.get();
            boolean integrate(T element, Sink<? super R> sink) {
                return sink.flush(current = scanner.apply(current, element));
            }
        }

        return Gatherer.ofSequential(State::new, Integrator.<State,T, R>ofGreedy(State::integrate));
    }

    /**
     * An operation which executes operations concurrently
     * with a fixed window of max concurrency, using VirtualThreads.
     * This operation preserves the ordering of the stream.
     *
     * <p>In progress tasks will be attempted to be cancelled,
     * on a best-effort basis, in situations where the downstream no longer
     * wants to receive any more elements.
     *
     * @param maxConcurrency the maximum concurrency desired
     * @param mapper a function to be executed concurrently
     * @param <T> the type of input
     * @param <R> the type of output
     * @return a new Gatherer
     * @throws IllegalArgumentException if maxConcurrency is less than 1
     * @throws NullPointerException if mapper is null
     */
    public static <T, R> Gatherer<T,?,R> mapConcurrent(final int maxConcurrency, final Function<? super T, ? extends R> mapper) {
        if (maxConcurrency <= 0)
            throw new IllegalArgumentException("'maxConcurrency' needs to be greater than 0");

        Objects.requireNonNull(mapper, "'mapper' must not be null");

        class State {
            final ArrayDeque<Future<R>> window = new ArrayDeque<>(maxConcurrency);
            final Semaphore windowLock = new Semaphore(maxConcurrency);

            final boolean integrate(T t, Sink<? super R> sink) {
                windowLock.acquireUninterruptibly();

                var task = CompletableFuture.<R>supplyAsync(() -> {
                    try {
                        return mapper.apply(t);
                    } finally {
                        windowLock.release();
                    }
                }, Thread::startVirtualThread);

                if (!window.add(task))
                    throw new IllegalStateException("Unable to add task even though cleared to do so");
                else
                    return flush(0, sink);
            }

            final boolean flush(long atLeastN, Sink<? super R> sink) {
                Future<R> current;
                boolean proceed = !sink.isKnownDone();

                while(proceed && (current = window.peek()) != null && (current.isDone() || atLeastN > 0)) {
                    try {
                        var result = current.get(); // When we flush, we are prepared to block here

                        proceed &= sink.flush(result);
                        --atLeastN;

                        if (window.pop() != current)
                            throw new IllegalStateException("current isn't the head of the queue");

                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    }
                }

                // Attempt to cancel submitted tasks if we cannot proceed
                if (!proceed) {
                    Future<R> next;
                    while((next = window.pollFirst()) != null) {
                        next.cancel(true);
                    }
                }

                return proceed;
            }
        }

        return Gatherer.ofSequential(
                    State::new,
                    Integrator.<State,T,R>ofGreedy(State::integrate),
                    (state, sink) -> state.flush(Long.MAX_VALUE, sink)
        );
    }
}