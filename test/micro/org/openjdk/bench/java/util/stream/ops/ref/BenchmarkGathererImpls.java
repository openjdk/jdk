package org.openjdk.bench.java.util.stream.ops.ref;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Gatherer;
import java.util.stream.Stream;

// Utility Gatherer and Collector implementations used by Gatherer micro-benchmarks
public final class BenchmarkGathererImpls {

    public static <TR> Gatherer<TR, ?, TR> filter(Predicate<? super TR> predicate) {
        return new FilteringGatherer<>(predicate);
    }

    public final static <T,R> Gatherer<T, ?, R> map(Function<? super T, ? extends R> mapper) {
        return new MappingGatherer<>(Objects.requireNonNull(mapper));
    }

    public static <TR> Gatherer<TR,?, TR> reduce(BinaryOperator<TR> reduce) {
        Objects.requireNonNull(reduce);
        return new ReducingGatherer<>(reduce);
    }

    public final static <TR> Gatherer<TR, ?, TR> takeWhile(Predicate<? super TR> predicate) {
        return new TakeWhileGatherer<>(Objects.requireNonNull(predicate));
    }

    @SuppressWarnings("unchecked")
    public final static <T> Collector<T,?,Optional<T>> findFirst() {
        return (Collector<T,?,Optional<T>>)FIND_FIRST;
    }

    @SuppressWarnings("unchecked")
    public final static <T> Collector<T,?,Optional<T>> findLast() {
        return (Collector<T,?,Optional<T>>)FIND_LAST;
    }

    @SuppressWarnings("rawtypes")
    private final static Collector FIND_FIRST =
            Collector.<Object,Box<Object>,Optional<Object>>of(
                    () -> new Box<>(),
                    (b,e) -> {
                        if (!b.hasValue) {
                            b.value = e;
                            b.hasValue = true;
                        }
                    },
                    (l,r) -> l.hasValue ? l : r,
                    b -> b.hasValue ? Optional.of(b.value) : Optional.empty()
            );

    @SuppressWarnings("rawtypes")
    private final static Collector FIND_LAST =
            Collector.<Object,Box<Object>,Optional<Object>>of(
                         () -> new Box<>(),
                         (b,e) -> {
                            b.value = e;
                            if (!b.hasValue)
                                b.hasValue = true;
                         },
                         (l,r) -> r.hasValue ? r : l,
                         b -> b.hasValue ? Optional.of(b.value) : Optional.empty()
            );

    public final static <T, R> Gatherer<T, ?, R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        Objects.requireNonNull(mapper);

        class FlatMappingGatherer implements Gatherer<T,Void,R>, Gatherer.Integrator<Void,T,R>, BinaryOperator<Void> {
            @Override public Integrator<Void, T, R> integrator() { return this; }

            // Ideal encoding, but performance-wise suboptimal due to cost of allMatch--about factor-10 worse.
            /*@Override public boolean integrate(Void state, T element, Gatherer.Downstream<? super R> downstream) {
                try(Stream<? extends R> s = mapper.apply(element)) {
                    return s != null ? s.sequential().allMatch(downstream::flush) : true;
                }
            }*/

            //The version below performs better, but is not nice to maintain or explain.

            private final static RuntimeException SHORT_CIRCUIT = new RuntimeException() {
                @Override public synchronized Throwable fillInStackTrace() { return this; }
            };

            @Override public boolean integrate(Void state, T element, Gatherer.Downstream<? super R> downstream) {
                try (Stream<? extends R> s = mapper.apply(element)) {
                    if (s != null) {
                        s.sequential().spliterator().forEachRemaining(e -> {
                            if (!downstream.push(e)) throw SHORT_CIRCUIT;
                        });
                    }
                    return true;
                } catch (RuntimeException e) {
                    if (e == SHORT_CIRCUIT)
                        return false;

                    throw e; // Rethrow anything else
                }
            }

            @Override public BinaryOperator<Void> combiner() { return this; }
            @Override public Void apply(Void unused, Void unused2) { return unused; }
        }

        return new FlatMappingGatherer();
    }

    final static class MappingGatherer<T, R> implements Gatherer<T, Void, R>, Gatherer.Integrator.Greedy<Void, T, R>, BinaryOperator<Void> {
        final Function<? super T, ? extends R> mapper;

        MappingGatherer(Function<? super T, ? extends R> mapper) { this.mapper = mapper; }

        @Override public Integrator<Void, T, R> integrator() { return this; }
        @Override public BinaryOperator<Void> combiner() { return this; }
        @Override public Void apply(Void left, Void right) { return left; }

        @Override
        public <RR> Gatherer<T, ?, RR> andThen(Gatherer<? super R, ?, ?
                extends RR> that) {
            if (that.getClass() == MappingGatherer.class) { // Implicit null-check of that
                @SuppressWarnings("unchecked")
                var thatMapper = ((MappingGatherer<R,RR>)that).mapper;
                return new MappingGatherer<>(this.mapper.andThen(thatMapper));
            } else
                return Gatherer.super.andThen(that);
        }

        @Override
        public boolean integrate(Void state, T element, Gatherer.Downstream<? super R> downstream) {
            return downstream.push(mapper.apply(element));
        }
    }


    final static class FilteringGatherer<TR> implements Gatherer<TR, Void, TR>, Gatherer.Integrator.Greedy<Void, TR, TR>, BinaryOperator<Void> {
        final Predicate<? super TR> predicate;

        protected FilteringGatherer(Predicate<? super TR> predicate) { this.predicate = predicate; }

        @Override public Integrator<Void, TR, TR> integrator() { return this; }
        @Override public BinaryOperator<Void> combiner() { return this; }

        @Override public Void apply(Void left, Void right) { return left; }

        @Override
        public boolean integrate(Void state, TR element, Gatherer.Downstream<? super TR> downstream) {
            return predicate.test(element) ? downstream.push(element) : true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <RR> Gatherer<TR, ?, RR> andThen(Gatherer<? super TR, ?, ?
                extends RR> that) {
            if (that.getClass() == FilteringGatherer.class) {
                var first = predicate;
                var second = ((FilteringGatherer<TR>) that).predicate;
                return (Gatherer<TR, ?, RR>) new FilteringGatherer<TR>(e -> first.test(e) && second.test(e));
            } else if (that.getClass() == MappingGatherer.class) {
                final var thatMapper = (MappingGatherer<TR, RR>)that;
                return new FilteringMappingGatherer<>(predicate, thatMapper.mapper);
            } else if (that.getClass() == FilteringMappingGatherer.class) {
                var first = predicate;
                var thatFilterMapper = ((FilteringMappingGatherer<TR, RR>) that);
                var second = thatFilterMapper.predicate;
                return new FilteringMappingGatherer<>(e -> first.test(e) && second.test(e), thatFilterMapper.mapper);
            } else
                return Gatherer.super.andThen(that);
        }
    }

    final static class FilteringMappingGatherer<T, R> implements Gatherer<T, Void, R>, Gatherer.Integrator.Greedy<Void, T, R>, BinaryOperator<Void> {
        final Predicate<? super T> predicate;
        final Function<? super T, ? extends R> mapper;

        FilteringMappingGatherer(Predicate<? super T> predicate, Function<? super T, ? extends R> mapper) {
            this.predicate = predicate;
            this.mapper = mapper;
        }

        @Override public Integrator<Void, T, R> integrator() { return this; }
        @Override public BinaryOperator<Void> combiner() { return this; }
        @Override public Void apply(Void left, Void right) { return left; }

        @Override
        public <RR> Gatherer<T, ?, RR> andThen(Gatherer<? super R, ?, ?
                extends RR> that) {
            if (that.getClass() == MappingGatherer.class) { // Implicit null-check of that
                @SuppressWarnings("unchecked")
                var thatMapper = ((MappingGatherer<R, RR>)that).mapper;
                return new FilteringMappingGatherer<>(this.predicate, this.mapper.andThen(thatMapper));
            } else
                return Gatherer.super.andThen(that);
        }

        @Override
        public boolean integrate(Void state, T element, Gatherer.Downstream<? super R> downstream) {
            return !predicate.test(element) || downstream.push(mapper.apply(element));
        }
    }

    final static class ReducingGatherer<TR> implements Gatherer<TR, Box<TR>, TR>,
            Supplier<Box<TR>>,
            Gatherer.Integrator.Greedy<Box<TR>, TR, TR>,
            BinaryOperator<Box<TR>>,
            BiConsumer<Box<TR>, Gatherer.Downstream<? super TR>> {
        private final BinaryOperator<TR> reduce;
        ReducingGatherer(BinaryOperator<TR> reduce) { this.reduce = reduce; }

        @Override public Box<TR> get() { return new Box<>(); }

        @Override
        public boolean integrate(Box<TR> state, TR m, Gatherer.Downstream<? super TR> downstream) {
            state.value = state.hasValue || !(state.hasValue = true) ? reduce.apply(state.value, m) : m;
            return true;
        }

        @Override public Box<TR> apply(Box<TR> left, Box<TR> right) {
            if (right.hasValue)
                integrate(left, right.value, null);
            return left;
        }

        @Override public void accept(Box<TR> box, Gatherer.Downstream<? super TR> downstream) {
            if (box.hasValue)
                downstream.push(box.value);
        }

        @Override public Supplier<Box<TR>> initializer() { return this; }
        @Override public Integrator<Box<TR>, TR, TR> integrator() { return this; }
        @Override public BinaryOperator<Box<TR>> combiner() { return this; }
        @Override public BiConsumer<Box<TR>, Gatherer.Downstream<? super TR>> finisher() { return this; }
    }

    final static class TakeWhileGatherer<TR> implements Gatherer<TR, Void, TR>, Gatherer.Integrator<Void, TR, TR>, BinaryOperator<Void> {
        final Predicate<? super TR> predicate;
        TakeWhileGatherer(Predicate<? super TR> predicate) { this.predicate = predicate; }

        @Override public Integrator<Void, TR, TR> integrator() { return this; }
        @Override public BinaryOperator<Void> combiner() { return this; }

        @Override public Void apply(Void left, Void right) { return left; }

        @Override public boolean integrate(Void state, TR element, Gatherer.Downstream<? super TR> downstream) {
            return predicate.test(element) && downstream.push(element);
        }

        @Override
        @SuppressWarnings("unchecked")
        public final <RR> Gatherer<TR, ?, RR> andThen(Gatherer<? super TR, ?,
                ? extends RR> that) {
            if (that.getClass() == TakeWhileGatherer.class) {
                final var thisPredicate = predicate;
                final var thatPredicate = ((TakeWhileGatherer<TR>)that).predicate;
                return (Gatherer<TR, ?, RR>)new TakeWhileGatherer<TR>(e -> thisPredicate.test(e) && thatPredicate.test(e));
            }
            else
                return Gatherer.super.andThen(that);
        }
    }

    final static class Box<T> {
        T value;
        boolean hasValue;

        Box() {}
    }
}