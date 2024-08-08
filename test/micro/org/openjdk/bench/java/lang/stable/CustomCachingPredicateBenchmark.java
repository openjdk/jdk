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
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.openjdk.bench.java.lang.stable.CustomCachingFunctions.cachingPredicate;

/**
 * Benchmark measuring custom stable value types
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark) // Share the same state instance (for contention)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgsAppend = {
        "--enable-preview"
        // Prevent the use of uncommon traps
//        ,"-XX:PerMethodTrapLimit=0"
})
@Threads(Threads.MAX)   // Benchmark under contention
//@OperationsPerInvocation(2)
public class CustomCachingPredicateBenchmark {

    private static final Set<Integer> SET = IntStream.range(0, 64).boxed().collect(Collectors.toSet());
    private static final Predicate<Integer> EVEN = i -> {
        // Slow down the original
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        return i % 2 == 0;
    };

    private static final Integer VALUE = 42;

    private static final Predicate<Integer> PREDICATE = cachingPredicate(SET, EVEN);
    private final Predicate<Integer> predicate = cachingPredicate(SET, EVEN);

    @Benchmark
    public boolean predicate() {
        return predicate.test(VALUE);
    }

    @Benchmark
    public boolean staticPredicate() {
        return PREDICATE.test(VALUE);
    }


    //Benchmark                                        Mode  Cnt  Score   Error  Units
    //CustomCachingPredicateBenchmark.predicate        avgt   10  3.054 ? 0.155  ns/op
    //CustomCachingPredicateBenchmark.staticPredicate  avgt   10  2.205 ? 0.361  ns/op

    // This is not constant foldable
    record CachingPredicate<T>(Map<? extends T, StableValue<Boolean>> delegate,
                               Predicate<T> original) implements Predicate<T> {

        public CachingPredicate(Set<? extends T> inputs, Predicate<T> original) {
            this(inputs.stream()
                            .collect(Collectors.toUnmodifiableMap(Function.identity(), _ -> StableValue.newInstance())),
                    original
            );
        }

        @Override
        public boolean test(T t) {
            final StableValue<Boolean> stable = delegate.get(t);
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
                final boolean r = original.test(t);
                stable.setOrThrow(r);
                return r;
            }
        }
    }

}
