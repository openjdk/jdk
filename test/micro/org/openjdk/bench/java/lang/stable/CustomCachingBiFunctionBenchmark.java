/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.openjdk.bench.java.lang.stable;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Benchmark measuring custom stable value types
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgsAppend = {
        "--enable-preview",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+PrintInlining"
        // Prevent the use of uncommon traps
        , "-XX:PerMethodTrapLimit=0"
})
@Threads(Threads.MAX)   // Benchmark under contention
@OperationsPerInvocation(2)
public class CustomCachingBiFunctionBenchmark {

    private static final Set<Pair<? extends Integer, ? extends Integer>> SET = Set.of(
            new Pair<>(1, 4),
            new Pair<>(1, 6),
            new Pair<>(1, 9),
            new Pair<>(42, 13),
            new Pair<>(13, 42),
            new Pair<>(99, 1)
    );

    private static final Integer VALUE = 42;
    private static final Integer VALUE2 = 13;
    private static final BiFunction<Integer, Integer, Integer> ORIGINAL = (l, r) -> (Integer) (l * 2 + r);

    private static final BiFunction<Integer, Integer, Integer> FUNCTION = new CachingBiFunction4<>(SET, ORIGINAL);
    private static final BiFunction<Integer, Integer, Integer> FUNCTION2 = new CachingBiFunction4<>(SET, ORIGINAL);

    private static final BiFunction<Integer, Integer, Integer> function = new CachingBiFunction4<>(SET, ORIGINAL);;
    private static final BiFunction<Integer, Integer, Integer> function2 = new CachingBiFunction4<>(SET, ORIGINAL);;

    private static final StableValue<Integer> STABLE_VALUE = StableValue.newInstance();
    private static final StableValue<Integer> STABLE_VALUE2 = StableValue.newInstance();

    static {
        STABLE_VALUE.trySet(ORIGINAL.apply(VALUE, VALUE2));
        STABLE_VALUE2.trySet(ORIGINAL.apply(VALUE2, VALUE));
    }

    @Benchmark
    public int function() {
        return function.apply(VALUE, VALUE2);
    }

    @Benchmark
    public int staticFunction() {
        return FUNCTION.apply(VALUE, VALUE2) + FUNCTION2.apply(VALUE2, VALUE);
    }

    @Benchmark
    public int staticStableValue() {
        return STABLE_VALUE.orElseThrow();
    }

    //Benchmark                                           Mode  Cnt  Score   Error  Units
    //CustomCachingBiFunctionBenchmark.function           avgt   10  0.353 ? 0.030  ns/op
    //CustomCachingBiFunctionBenchmark.staticFunction     avgt   10  0.344 ? 0.003  ns/op
    //CustomCachingBiFunctionBenchmark.staticStableValue  avgt   10  0.370 ? 0.062  ns/op
    static <T, U, R> BiFunction<T, U, R> cachingBiFunction(Set<Pair<? extends T, ? extends U>> inputs, BiFunction<T, U, R> original) {
        Function<Pair<? extends T, ? extends U>, R> delegate = (Pair<? extends T, ? extends U> p) -> original.apply(p.left, p.right);
        return (T t, U u) -> delegate.apply(new Pair<>(t, u));
    }

    //
    static <T, U, R> BiFunction<T, U, R> cachingBiFunction2(Set<Pair<? extends T, ? extends U>> inputs, BiFunction<T, U, R> original) {
        Function<Pair<? extends T, ? extends U>, R> delegate = StableValue.newCachingFunction(inputs, (Pair<? extends T, ? extends U> p) -> original.apply(p.left, p.right), null);
        return (T t, U u) -> delegate.apply(new Pair<>(t, u));
    }

    //Benchmark                                           Mode  Cnt    Score     Error  Units
    //CustomCachingBiFunctionBenchmark.function           avgt   10  577.307 ?  57.591  ns/op
    //CustomCachingBiFunctionBenchmark.staticFunction     avgt   10  488.900 ? 126.583  ns/op
    //CustomCachingBiFunctionBenchmark.staticStableValue  avgt   10    0.343 ?   0.014  ns/op
    record CachingBiFunction2<T, U, R>(Function<Pair<? extends T, ? extends U>, R> delegate) implements BiFunction<T, U, R> {

        public CachingBiFunction2(Set<Pair<? extends T, ? extends U>> inputs, BiFunction<T, U, R> original) {
            this(StableValue.newCachingFunction(inputs, (Pair<? extends T, ? extends U> p) -> original.apply(p.left, p.right), null));
        }

        @Override
        public R apply(T t, U u) {
            return delegate.apply(new Pair<>(t, u));
        }
    }

    record Pair<T, U>(T left, U right){}

    //Benchmark                                           Mode  Cnt    Score     Error  Units
    //CustomCachingBiFunctionBenchmark.function           avgt   10  471.650 ? 119.590  ns/op
    //CustomCachingBiFunctionBenchmark.staticFunction     avgt   10  560.667 ? 178.996  ns/op
    //CustomCachingBiFunctionBenchmark.staticStableValue  avgt   10    0.428 ?   0.070  ns/op

    // Crucially, use Map.copyOf to get an immutable Map
    record CachingBiFunction<T, U, R>(
            Map<Pair<? extends T, ? extends U>, StableValue<R>> delegate,
            BiFunction<T, U, R> original) implements BiFunction<T, U, R> {

        public CachingBiFunction(Set<Pair<? extends T, ? extends U>> inputs, BiFunction<T, U, R> original) {
            this(Map.copyOf(inputs.stream()
                            .collect(Collectors.toMap(Function.identity(), _ -> StableValue.newInstance()))),
                    original
            );
        }

        @Override
        public R apply(T t, U u) {
            final StableValue<R> stable = delegate.get(new Pair<>(t, u));
            if (stable == null) {
                throw new IllegalArgumentException(t.toString());

            }
            if (stable.isSet()) {
                return stable.orElseThrow();
            }
            synchronized (this) {
                if (stable.isSet()) {
                    return stable.orElseThrow();
                }
                final R r = original.apply(t, u);
                stable.setOrThrow(r);
                return r;
            }
        }
    }

    //Benchmark                                           Mode  Cnt  Score   Error  Units
    //CustomCachingBiFunctionBenchmark.function           avgt   10  8.438 ? 0.683  ns/op
    //CustomCachingBiFunctionBenchmark.staticFunction     avgt   10  8.312 ? 0.394  ns/op
    //CustomCachingBiFunctionBenchmark.staticStableValue  avgt   10  0.352 ? 0.021  ns/op
    // Map-in-map
    record CachingBiFunction3<T, U, R>(
            Map<T, Map<U, StableValue<R>>> delegate,
            BiFunction<T, U, R> original) implements BiFunction<T, U, R> {

        public CachingBiFunction3(Set<Pair<? extends T, ? extends U>> inputs, BiFunction<T, U, R> original) {
            this(delegate(inputs), original);
        }

        static <T, U, R> Map<T, Map<U, StableValue<R>>> delegate(Set<Pair<? extends T, ? extends U>> inputs) {
            Map<T, Map<U, StableValue<R>>> map = inputs.stream()
                    .collect(Collectors.groupingBy(p -> p.left,
                            Collectors.groupingBy(p -> p.right,
                                    Collectors.mapping((Function<? super Pair<? extends T, ? extends U>, ? extends StableValue<R>>) _ -> StableValue.newInstance(),
                                            Collectors.reducing(StableValue.newInstance(), _ -> StableValue.newInstance(), (StableValue<R> a, StableValue<R> b) -> a)))));

            @SuppressWarnings("unchecked")
            Map<T, Map<U, StableValue<R>>> copy = Map.ofEntries(map.entrySet().stream()
                    .map(e -> Map.entry(e.getKey(), Map.copyOf(e.getValue())))
                    .toArray(Map.Entry[]::new));

            return copy;
        }

        @Override
        public R apply(T t, U u) {
            final StableValue<R> stable;
            try {
                stable = Objects.requireNonNull(delegate.get(t).get(u));
            } catch (NullPointerException _) {
                throw new IllegalArgumentException(t.toString() + ", " + u.toString());
            }
            if (stable.isSet()) {
                return stable.orElseThrow();
            }
            synchronized (this) {
                if (stable.isSet()) {
                    return stable.orElseThrow();
                }
                final R r = original.apply(t, u);
                stable.setOrThrow(r);
                return r;
            }
        }
    }


    //Benchmark                                           Mode  Cnt  Score   Error  Units
    //CustomCachingBiFunctionBenchmark.function           avgt   10  8.123 ? 0.113  ns/op
    //CustomCachingBiFunctionBenchmark.staticFunction     avgt   10  8.407 ? 0.559  ns/op
    //CustomCachingBiFunctionBenchmark.staticStableValue  avgt   10  0.354 ? 0.019  ns/op

    // CachingFunction-in-map
    record CachingBiFunction4<T, U, R>(
            Map<T, Function<U, R>> delegate) implements BiFunction<T, U, R> {

        public CachingBiFunction4(Set<Pair<? extends T, ? extends U>> inputs, BiFunction<T, U, R> original) {
            this(delegate(inputs, original));
        }

        static <T, U, R> Map<T, Function<U, R>> delegate(Set<Pair<? extends T, ? extends U>> inputs, BiFunction<T, U, R> original) {
            Map<T, Map<U, StableValue<R>>> map = inputs.stream()
                    .collect(Collectors.groupingBy(p -> p.left,
                            Collectors.groupingBy(p -> p.right,
                                    Collectors.mapping((Function<? super Pair<? extends T, ? extends U>, ? extends StableValue<R>>) _ -> StableValue.newInstance(),
                                            Collectors.reducing(StableValue.newInstance(), _ -> StableValue.newInstance(), (StableValue<R> a, StableValue<R> b) -> a)))));

            @SuppressWarnings("unchecked")
            Map<T, Function<U, R>> copy = Map.ofEntries(map.entrySet().stream()
                    .map(e -> Map.entry(e.getKey(), StableValue.newCachingFunction( e.getValue().keySet(), (U u) -> original.apply(e.getKey(), u) ,null)))
                    .toArray(Map.Entry[]::new));

            return copy;
        }

        @Override
        public R apply(T t, U u) {
            return delegate.get(t)
                    .apply(u);


            // This prevents constant folding   -----V

/*            Function<U, R> function = delegate.get(t);
            if (function == null) {
                throw new IllegalArgumentException(t.toString() + ", " + u.toString());
            }
            return function.apply(u);*/

/*
            try {
                return delegate.get(t)
                        .apply(u);
            } catch (NullPointerException _) {
                throw new IllegalArgumentException(t.toString() + ", " + u.toString());
            }*/
        }
    }

}
