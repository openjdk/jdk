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

/* @test
 * @summary Demo of the JEP examples.
 * @compile --enable-preview -source ${jdk.version} JepDemo.java
 * @run junit/othervm --enable-preview JepDemo
 */

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class JepDemo {

    static class Foo {};

    static
    class Init {
        // 1. Declare a monotonic value
        private static final Monotonic<Logger> LOGGER = Monotonic.of();

        static void init() {
            // 2. Bind the monotonic value "later"
            LOGGER.bind(Logger.getLogger("com.foo.Bar"));
        }

        static Logger logger() {
            // 3. Access the monotonic value with as-declared-final performance
            return LOGGER.get();
        }

    }

    static
    class Bar2 {
        // 1. Declare a monotonic
        private static final Monotonic<Logger> LOGGER = Monotonic.of();

        static Logger logger() {
            // 2. Access the monotonic value with as-declared-final performance
            //    (evaluation made before the first access)
            return LOGGER.computeIfAbsent( () -> Logger.getLogger("com.foo.Bar") );
        }
    }

    static
    class Bar3 {
        // 1. Declare a memoized (cached) Supplier backed by a monotonic value
        private static final Supplier<Logger> LOGGER = () -> Monotonic.<Logger>of()
                .computeIfAbsent( () -> Logger.getLogger("com.foo.Bar") );

        static Logger logger() {
            // 2. Access the memoized value with as-declared-final performance
            //    (evaluation made before the first access)
            return LOGGER.get();
        }
    }

    static
    class Bar4 {
        // 1. Declare a memoized (cached) Supplier backed by the monotonic value that
        //    is invoked at most once
        private static final Supplier<Logger> LOGGER = Monotonics.asMemoized(
                        () -> Logger.getLogger("com.foo.Bar"));

        static Logger logger() {
            // 2. Access the memoized value with as-declared-final performance
            //    (evaluation made before the first access)
            return LOGGER.get();
        }
    }

    static
    class Fibonacci {

        private final List<Monotonic<Integer>> numberCache;

        public Fibonacci(int upperBound) {
            numberCache = Monotonic.ofList(upperBound);
        }

        public int number(int n) {
            return (n < 2)
                    ? n
                    : Monotonics.computeIfAbsent(numberCache, n -1 , this::number)
                    + Monotonics.computeIfAbsent(numberCache, n - 2, this::number);
        }

    }

    static
    class Fibonacci2 {

        private final IntFunction<Integer> numCache;

        public Fibonacci2(int upperBound) {
            List<Monotonic<Integer>> monotonicList = Monotonic.ofList(upperBound);
            numCache =
                    i -> Monotonics.computeIfAbsent(monotonicList, i, this::number);
        }

        public int number(int n) {
            return (n < 2)
                    ? n
                    : numCache.apply(n - 1) + numCache.apply(n - 2);
        }

    }

    static
    class Fibonacci3 {

        private final IntFunction<Integer> numCache;

        public Fibonacci3(int upperBound) {
            numCache = Monotonics.asMemoized(upperBound, this::number);
        }

        public int number(int n) {
            return (n < 2)
                    ? n
                    : numCache.apply(n - 1) + numCache.apply(n - 2);
        }

    }


    static
    class MapDemo {

        private static final Map<String, Monotonic<Logger>> LOGGERS =
                Monotonic.ofMap(Set.of("com.foo.Bar", "com.foo.Baz"));

        static Logger logger(String name) {
            return Monotonics.computeIfAbsent(LOGGERS, name, Logger::getLogger);
        }
    }

    static
    class MapDemo2 {

        // 1. Declare a memoized (cached) function backed by a monotonic map
        private static final Function<String, Logger> LOGGERS =
                Monotonics.asMemoized(
                        Set.of("com.foo.Bar", "com.foo.Baz"),
                        Logger::getLogger);

        static Logger logger(String name) {
            // 2. Access the memoized value with as-declared-final performance
            //    (evaluation made before the first access)
            return LOGGERS.apply(name);
        }
    }


    @Test
    void fibDirect() {
        Fibonacci fibonacci = new Fibonacci(20);
        int[] fibs = IntStream.range(0, 10)
                .map(fibonacci::number)
                .toArray(); // { 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 }

        assertArrayEquals(new int[]{ 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 }, fibs);
    }


    @Test
    void fib() {
        Fibonacci2 fibonacci = new Fibonacci2(20);
        int[] fibs = IntStream.range(0, 10)
                .map(fibonacci::number)
                .toArray(); // { 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 }

        assertArrayEquals(new int[]{ 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 }, fibs);
    }

    @Test
    void fib3() {
        Fibonacci3 fibonacci = new Fibonacci3(20);
        int[] fibs = IntStream.range(0, 10)
                .map(fibonacci::number)
                .toArray(); // { 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 }

        assertArrayEquals(new int[]{ 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 }, fibs);
    }

}
