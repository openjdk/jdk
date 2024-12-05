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
 * @summary Basic tests for JepTest implementations
 * @compile --enable-preview -source ${jdk.version} JepTest.java
 * @run junit/othervm --enable-preview JepTest
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class JepTest {

    class Progression0 {

        static final
        public class Cache {

            private Logger logger;

            public Logger logger() {
                Logger v = logger;
                if (v == null) {
                    logger = v = Logger.getLogger("com.company.Foo");
                }
                return v;
            }
        }
    }

    class Progression1 {

        static final
        public class Cache {

            private Logger logger;

            public synchronized Logger logger() {
                Logger v = logger;
                if (v == null) {
                    logger = v = Logger.getLogger("com.company.Foo");
                }
                return v;
            }
        }
    }

    class Progression2 {

        static final
        public class Cache {

            private volatile Logger logger;

            public Logger logger() {
                Logger v = logger;
                if (v == null) {
                    synchronized (this) {
                        v = logger;
                        if (v == null) {
                            logger = v = Logger.getLogger("com.company.Foo");
                        }
                    }
                }
                return v;
            }
        }
    }

    class Progression3 {

        static final
        public class Cache {

            private static final VarHandle ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(Logger[].class);
            private static final int SIZE = 10;
            private final Logger[] loggers = new Logger[SIZE];

            public Logger logger(int i) {
                Logger v = (Logger) ARRAY_HANDLE.getVolatile(loggers, i);
                if (v == null) {
                    synchronized (this) {
                        v = loggers[i];
                        if (v == null) {
                            ARRAY_HANDLE.setVolatile(loggers, i, v = Logger.getLogger("com.company.Foo" + i));
                        }
                    }
                }
                return v;
            }
        }
    }


    class Foo {
        // 1. Declare a Stable field
        private static final StableValue<Logger> LOGGER = StableValue.unset();

        static Logger logger() {

            if (!LOGGER.isSet()) {
                // 2. Set the stable value _after_ the field was declared
                LOGGER.trySet(Logger.getLogger("com.company.Foo"));
            }

            // 3. Access the stable value with as-declared-final performance
            return LOGGER.orElseThrow();
        }
    }

    class Foo2 {

        // 1. Centrally declare a caching supplier and define how it should be computed
        private static final Supplier<Logger> LOGGER =
                StableValue.ofSupplier( () -> Logger.getLogger("com.company.Foo") );


        static Logger logger() {
            // 2. Access the cached value with as-declared-final performance
            //    (single evaluation made before the first access)
            return LOGGER.get();
        }
    }

    class MapDemo {

        // 1. Declare a lazy stable map of loggers with two allowable keys:
        //    "com.company.Bar" and "com.company.Baz"
        static final Map<String, Logger> LOGGERS =
                StableValue.ofMap(Set.of("com.company.Foo", "com.company.Bar"), Logger::getLogger);

        // 2. Access the lazy map with as-declared-final performance
        //    (evaluation made before the first access)
        static Logger logger(String name) {
            return LOGGERS.get(name);
        }
    }

    static
    class CachedNum {
        // 1. Centrally declare a cached IntFunction backed by a list of StableValue elements
        private static final IntFunction<Logger> LOGGERS =
                StableValue.ofIntFunction(2, CachedNum::fromNumber);

        // 2. Define a function that is to be called the first
        //    time a particular message number is referenced
        //    The given loggerNumber is manually mapped to loggers
        private static Logger fromNumber(int loggerNumber) {
            return switch (loggerNumber) {
                case 0 -> Logger.getLogger("com.company.Foo");
                case 1 -> Logger.getLogger("com.company.Bar");
                default -> throw new IllegalArgumentException();
            };
        }

        // 3. Access the cached element with as-declared-final performance
        //    (evaluation made before the first access)
        Logger logger = LOGGERS.apply(0);
    }

    class Cached {

        // 1. Centrally declare a cached function backed by a map of stable values
        private static final Function<String, Logger> LOGGERS =
                StableValue.ofFunction(Set.of("com.company.Foo", "com.company.Bar"),
                Logger::getLogger);

        private static final String NAME = "com.company.Foo";

        // 2. Access the cached value via the function with as-declared-final
        //    performance (evaluation made before the first access)
        Logger logger = LOGGERS.apply(NAME);
    }

    class A {

        static
        class ErrorMessages {
            private static final int SIZE = 8;

            // 1. Centrally declare a cached IntFunction backed by a list of StableValue elements
            private static final IntFunction<String> ERROR_FUNCTION =
                    StableValue.ofIntFunction(SIZE, ErrorMessages::readFromFile);

            // 2. Define a function that is to be called the first
            //    time a particular message number is referenced
            private static String readFromFile(int messageNumber) {
                try {
                    return Files.readString(Path.of("message-" + messageNumber + ".html"));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            // 3. Access the cached element with as-declared-final performance
            //    (evaluation made before the first access)
            String msg = ERROR_FUNCTION.apply(2);
        }
    }

    record CachingPredicate<T>(Map<? extends T, StableValue<Boolean>> delegate,
                               Function<T, Boolean> original) implements Predicate<T> {

        public CachingPredicate(Set<? extends T> inputs, Predicate<T> original) {
            this(inputs.stream()
                    .collect(Collectors.toMap(Function.identity(), _ -> StableValue.unset())),
                    original::test
            );
        }

        @Override
        public boolean test(T t) {
            final StableValue<Boolean> stable = delegate.get(t);
            if (stable == null) {
                throw new IllegalArgumentException(t.toString());

            }
            return stable.computeIfUnset(() -> original.apply(t));
        }
    }

    record CachingPredicate2<T>(Map<? extends T, Boolean> delegate) implements Predicate<T> {

        public CachingPredicate2(Set<? extends T> inputs, Predicate<T> original) {
            this(StableValue.ofMap(inputs, original::test));
        }

        @Override
        public boolean test(T t) {
            return delegate.get(t);
        }
    }

    public static void main(String[] args) {
        Predicate<Integer> even = i -> i % 2 == 0;
        Predicate<Integer> cachingPredicate = StableValue.ofMap(Set.of(1, 2), even::test)::get;
    }

    record Pair<L, R>(L left, R right){}

    record CachingBiFunction<T, U, R>(Map<Pair<T, U>, StableValue<R>> delegate,
                                      Function<Pair<T, U>, R> original) implements BiFunction<T, U, R> {

        @Override
        public R apply(T t, U u) {
            final var key = new Pair<>(t, u);
            final StableValue<R> stable = delegate.get(key);
            if (stable == null) {
                throw new IllegalArgumentException(t + ", " + u);
            }
            return stable.computeIfUnset(() -> original.apply(key));
        }

        static <T, U, R> CachingBiFunction<T, U, R> of(Set<Pair<T, U>> inputs, BiFunction<T, U, R> original) {

            Map<Pair<T, U>, StableValue<R>> map = inputs.stream()
                    .collect(Collectors.toMap(Function.identity(), _ -> StableValue.unset()));

            return new CachingBiFunction<>(map, pair -> original.apply(pair.left(), pair.right()));
        }

    }

    record CachingBiFunction2<T, U, R>(Map<Pair<T, U>, StableValue<R>> delegate,
                                      BiFunction<T, U, R> original) implements BiFunction<T, U, R> {

        @Override
        public R apply(T t, U u) {
            final var key = new Pair<>(t, u);
            final StableValue<R> stable = delegate.get(key);
            if (stable == null) {
                throw new IllegalArgumentException(t + ", " + u);
            }
            return stable.computeIfUnset(() -> original.apply(key.left(), key.right()));
        }

        static <T, U, R> BiFunction<T, U, R> of(Set<Pair<T, U>> inputs, BiFunction<T, U, R> original) {

            Map<Pair<T, U>, StableValue<R>> map = inputs.stream()
                    .collect(Collectors.toMap(Function.identity(), _ -> StableValue.unset()));

            return new CachingBiFunction2<>(map, original);
        }

    }

    static
    class Application {
        private final StableValue<Logger> LOGGER = StableValue.unset();

        public Logger getLogger() {
            return LOGGER.computeIfUnset(() -> Logger.getLogger("com.company.Foo"));
        }
    }

    record CachingIntPredicate(List<StableValue<Boolean>> outputs,
                               IntPredicate resultFunction) implements IntPredicate {

        CachingIntPredicate(int size, IntPredicate resultFunction) {
            this(Stream.generate(StableValue::<Boolean>unset).limit(size).toList(), resultFunction);
        }

        @Override
        public boolean test(int value) {
            return outputs.get(value).computeIfUnset(() -> resultFunction.test(value));
        }

    }


    static
    class FixedStableList<E> extends AbstractList<E> {
        private final StableValue<E>[] elements;

        FixedStableList(int size) {
            this.elements = Stream.generate(StableValue::<E>unset)
                    .limit(size)
                    .toArray(StableValue[]::new);
        }

        @Override
        public E get(int index) {
            return elements[index].orElseThrow();
        }



        @Override
        public E set(int index, E element) {
            elements[index].setOrThrow(element);
            return null;
        }



        @Override
        public int size() {
            return elements.length;
        }
    }

    static
    class Dependency {

        public static class Foo {
            // ...
        }

        public static class Bar {
            public Bar(Foo foo) {
                // ...
            }
        }

        private static final Supplier<Foo> FOO = StableValue.ofSupplier(Foo::new);
        private static final Supplier<Bar> BAR = StableValue.ofSupplier(() -> new Bar(FOO.get()));

        public static Foo foo() {
            return FOO.get();
        }

        public static Bar bar() {
            return BAR.get();
        }

    }

    static
    class Fibonacci {

        private static final int MAX_SIZE_INT = 46;

        private static final IntFunction<Integer> FIB =
                StableValue.ofIntFunction(MAX_SIZE_INT, Fibonacci::fib);

        public static int fib(int n) {
            return n < 2
                    ? n
                    : FIB.apply(n - 1) + FIB.apply(n - 2);
        }

    }


}
