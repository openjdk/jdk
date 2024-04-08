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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class JepDemo {

    static class Foo {};

    static
    class Bar {
        // 1. Declare a Lazy field
        private static final Lazy<Logger> LOGGER = Lazy.of();

        static Logger logger() {

            if (!LOGGER.isSet()) {
                // 2. Set the lazy value _after_ the field was declared
                return LOGGER.setIfUnset(Logger.getLogger("com.foo.Bar"));
            }

            // 3. Access the lazy value with as-declared-final performance
            return LOGGER.orThrow();
        }

    }

    static
    class Bar2 {
        // 1. Declare a Lazy field
        private static final Lazy<Logger> LOGGER = Lazy.of();

        static Logger logger() {
            // 2. Access the lazy value with as-declared-final performance
            //    (single evaluation made before the first access)
            return LOGGER.computeIfUnset( () -> Logger.getLogger("com.foo.Bar") );
        }
    }

    static
    class Bar3 {
        // 1. Declare a backing lazy value
        private static final Lazy<Logger> LAZY = Lazy.of();

        // 2. Declare a memoized (cached) Supplier backed by the lazy value
        private static final Supplier<Logger> LOGGER = () -> LAZY
                .computeIfUnset( () -> Logger.getLogger("com.foo.Bar") );

        static Logger logger() {
            // 2. Access the memoized value with as-declared-final performance
            //    (evaluation made before the first access)
            return LOGGER.get();
        }
    }

/*    static
    class Bar4 {
        // 1. Declare a memoized (cached) Supplier (backed by an
        //    internal lazy value) that is invoked at most once
        private static final Supplier<Logger> LOGGER = Lazy.asSupplier(
                        () -> Logger.getLogger("com.foo.Bar"));

        static Logger logger() {
            // 2. Access the memoized value with as-declared-final performance
            //    (evaluation made before the first access)
            return LOGGER.get();
        }
    }*/

    static
    class Fibonacci {

        private final List<Lazy<Integer>> numCache;
        private final IntFunction<Integer> numFunction;

        public Fibonacci(int upperBound) {
            numCache = Lazy.ofList(upperBound);
            numFunction = i -> Lazy.computeIfUnset(numCache, i, this::number);
        }

        public int number(int n) {
            return (n < 2)
                    ? n
                    : numFunction.apply(n - 1) + numFunction.apply(n - 2);
        }

    }


    static
    class MapDemo {

        // 1. Declare a lazy map of loggers with two allowable keys:
        //    "com.foo.Bar" and "com.foo.Baz"
        static final Map<String, Lazy<Logger>> LOGGERS =
                Lazy.ofMap(Set.of("com.foo.Bar", "com.foo.Baz"));

        // 2. Access the memoized map with as-declared-final performance
        //    (evaluation made before the first access)
        static Logger logger(String name) {
            return Lazy.computeIfUnset(LOGGERS, name, Logger::getLogger);
        }
    }


    static
    class SetDemo {

        static final Map<String, Lazy<Boolean>> INFO_LOGGABLE =
                Lazy.ofMap(Set.of("com.foo.Bar", "com.foo.Baz"));

        static boolean isInfoLoggable(String name) {
            return Lazy.computeIfUnset(INFO_LOGGABLE, name, n -> MapDemo.logger(n).isLoggable(Level.INFO));
        }

        private static final String NAME = "com.foo.Bar";

        public static void main(String[] args) {
            if (isInfoLoggable(NAME)) {
                MapDemo.LOGGERS.get(NAME)
                        .orThrow()
                        .log(Level.INFO, "This is fast...");
            }
        }

    }

    static
    class Memo {

        static
        class Memoized {

            // 1. Declare a lazily computed map
            private static final Map<String, Lazy<Logger>> MAP =
                    Lazy.ofMap(Set.of("com.foo.Bar", "com.foo.Baz"));

            // 1. Declare a memoized (cached) function backed by a lazily computed map
            private static final Function<String, Logger> LOGGERS =
                    n -> Lazy.computeIfUnset(MAP, n, Logger::getLogger);

            static Logger logger(String name) {
                // 2. Access the memoized value with as-declared-final performance
                //    (evaluation made before the first access)
                return LOGGERS.apply(name);
            }
        }
    }

    static
    class ErrorMessages {

        private static final int SIZE = 8;

        // 1. Declare an array of error pages to serve up
        private static final String[] MESSAGES = new String[SIZE];

        // 2. Define a function that is to be called the first
        //    time a particular message number is referenced
        private static String readFromFile(int messageNumber) {
            try {
                return Files.readString(Path.of("message-" + messageNumber + ".html"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        static synchronized String message(int messageNumber) {
            // 3. Access the memoized array element under synchronization
            //    and compute-and-store if absent.
            String page = MESSAGES[messageNumber];
            if (page == null) {
                page = readFromFile(messageNumber);
                MESSAGES[messageNumber] = page;
            }
            return page;
        }

    }

    static class Holder {

        static
        class ErrorMessages {

            private static final int SIZE = 8;

            // 1. Declare a lazy list of default error pages to serve up
            private static final List<Lazy<String>> MESSAGES = Lazy.ofList(SIZE);

            // 2. Define a function that is to be called the first
            //    time a particular message number is referenced
            private static String readFromFile(int messageNumber) {
                try {
                    return Files.readString(Path.of("message-" + messageNumber + ".html"));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            static String errorPage(int messageNumber) {
                // 3. Access the memoized list element with as-declared-final performance
                //    (evaluation made before the first access)
                return Lazy.computeIfUnset(MESSAGES, messageNumber, ErrorMessages::readFromFile);
            }

            public static void main(String[] args) {
                String errorPage = ErrorMessages.errorPage(2);

                // <!DOCTYPE html>
                // <html lang="en">
                //   <head><meta charset="utf-8"></head>
                //   <body>Payment was denied: Insufficient funds.</body>
                // </html>
            }

        }
    }

    static
    class ListDemo2 {

        private static final int MIN = 500;
        private static final int SIZE = 511 - MIN;


        // 1. Declare a lazy list of default error pages to serve up
        private static final List<Lazy<String>> ERROR_PAGES =
                Lazy.ofList(SIZE);

        // 1. Declare a lazy list of default error pages to serve up
        private static final IntFunction<String> ERROR_FUNCTION =
                i -> Lazy.computeIfUnset(ERROR_PAGES, i, ListDemo2::readFromFile);

        // 2. Define a function that is to be called for the first
        //    time a particular index is referenced
        private static String readFromFile(int errorCode) {
            try {
                return Files.lines(Path.of("error-page-" + errorCode + ".html"))
                        .collect(Collectors.joining());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        static String errorPage(int errorCode) {
            // 3. Access the memoized list element with as-declared-final performance
            //    (evaluation made before the first access)
            return ERROR_FUNCTION.apply(errorCode);
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


/*
    @Test
    void fib() {
        Fibonacci2 fibonacci = new Fibonacci2(20);
        int[] fibs = IntStream.range(0, 10)
                .map(fibonacci::number)
                .toArray(); // { 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 }

        assertArrayEquals(new int[]{ 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 }, fibs);
    }
*/

    @Test
    void fib3() {
        Fibonacci fibonacci = new Fibonacci(20);
        int[] fibs = IntStream.range(0, 10)
                .map(fibonacci::number)
                .toArray(); // { 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 }

        assertArrayEquals(new int[]{ 0, 1, 1, 2, 3, 5, 8, 13, 21, 34 }, fibs);
    }

}
