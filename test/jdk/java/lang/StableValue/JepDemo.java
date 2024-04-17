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
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} JepDemo.java
 * @run junit/othervm --enable-preview JepDemo
 */

import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class JepDemo {

    static class Foo {};



    static
    class BarBefore {
        // ordinary static initialization
        private static final Logger LOGGER = Logger.getLogger("com.foo.Bar");

        public void handleRequest() {
            LOGGER.log(Level.INFO, "Hello");
        }

    }

    static
    class BarBefore2 {
        // Initialization-on-demand holder idiom
        Logger logger() {
            class Holder {
                static final Logger LOGGER = Logger.getLogger("com.foo.Bar");
            }
            return Holder.LOGGER;
        }

        public void handleRequest() {
            logger().log(Level.INFO, "Hello");
        }

    }

    static
    class Bar {
        // 1. Declare a stable value field
        private static final StableValue<Logger> LOGGER = StableValue.of();

        static Logger logger() {

            if (!LOGGER.isSet()) {
                // 2. Set the stable value _after_ the field was declared
                return LOGGER.setIfUnset(Logger.getLogger("com.foo.Bar"));
            }

            // 3. Access the stable value with as-declared-final performance
            return LOGGER.orThrow();
        }

    }

    static
    class Bar2 {
        // 1. Declare a stable value field
        private static final StableValue<Logger> LOGGER = StableValue.of();

        static Logger logger() {
            // 2. Access the stable value with as-declared-final performance
            //    (single evaluation made before the first access)
            return LOGGER.computeIfUnset( () -> Logger.getLogger("com.foo.Bar") );
        }
    }

    static
    class Bar3 {
        // 1. Declare a backing stable value
        private static final StableValue<Logger> STABLE = StableValue.of();

        // 2. Declare a memoized (cached) Supplier backed by the stable value
        private static final Supplier<Logger> LOGGER = () -> STABLE
                .computeIfUnset( () -> Logger.getLogger("com.foo.Bar") );

        static Logger logger() {
            // 3. Access the memoized suppler with as-declared-final performance
            //    (evaluation made before the first access)
            return LOGGER.get();
        }
    }

    static
    class Bar4 {

        // 1. Declare a memoized (cached) Supplier backed by a stable value
        private static final Supplier<Logger> LOGGER =
                StableValue.ofSupplier( () -> Logger.getLogger("com.foo.Bar") );

        static Logger logger() {
            // 2. Access the memoized suppler with as-declared-final performance
            //    (evaluation made before the first access)
            return LOGGER.get();
        }
    }

    // In user-land and if we want constant folding when using
    // the holder statically:

    record LoggerHolder(StableValue<Logger> loggerHolder) {

        public LoggerHolder() {
            this(StableValue.of());
        }

        Logger logger() {
            // 1. Access the logger holder with as-declared-final performance
            //    (evaluation made before the first access)
            return loggerHolder.computeIfUnset(() -> Logger.getLogger("com.foo.Bar") );
        }
    }

    static
    class LoggerHolder2 {

        // 1. Declare an instance field that holds the logger
        //    This field gets special VM treatment as it is of type StableValue
        //    The field is resistant to updates via reflection and sun.misc.Unsafe
        private final StableValue<Logger> logger = StableValue.of();

        Logger logger() {
            // 2. Access the logger with as-declared-final performance
            //    (evaluation made before the first access)
            return logger.computeIfUnset(() -> Logger.getLogger("com.foo.Bar") );
        }
    }

    // Lists and arrays

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

    static class Hide {

        static
        class ErrorMessages {

            private static final int SIZE = 8;

            // 1. Declare a stable list of default error pages to serve up
            private static final List<StableValue<String>> MESSAGES = StableValue.ofList(SIZE);

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
                return StableValue.computeIfUnset(MESSAGES, messageNumber, ErrorMessages::readFromFile);
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

        private static final int SIZE = 8;

        // 1. Declare a stable list of default error pages to serve up
        private static final List<StableValue<String>> ERROR_PAGES =
                StableValue.ofList(SIZE);

        // 2. Declare a memoized IntFunction backed by the stable list
        private static final IntFunction<String> ERROR_FUNCTION =
                i -> StableValue.computeIfUnset(ERROR_PAGES, i, ListDemo2::readFromFile);

        // 3. Define a function that is to be called the first
        //    time a particular message number is referenced
        private static String readFromFile(int messageNumber) {
            try {
                return Files.readString(Path.of("message-" + messageNumber + ".html"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }


        public static void main(String[] args) {
            // 4. Access the memoized list element with as-declared-final performance
            //    (evaluation made before the first access)
            String msg =  ERROR_FUNCTION.apply(2);
        }

    }

    static
    class ListDemo3 {

        private static final int SIZE = 8;

        // 1. Declare a memoized IntFunction backed by the stable list
        private static final IntFunction<String> ERROR_FUNCTION =
                StableValue.ofIntFunction(SIZE, ListDemo3::readFromFile);

        // 2. Define a function that is to be called the first
        //    time a particular message number is referenced
        private static String readFromFile(int messageNumber) {
            try {
                return Files.readString(Path.of("message-" + messageNumber + ".html"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public static void main(String[] args) {
            // 3. Access the memoized list element with as-declared-final performance
            //    (evaluation made before the first access)
            String msg =  ERROR_FUNCTION.apply(2);
        }

    }

    // Instance fields

    static
    class Fibonacci {

        private final IntFunction<Integer> numFunction;

        public Fibonacci(int upperBound) {
            numFunction = StableValue.ofIntFunction(upperBound, this::number);
        }

        public int number(int n) {
            return (n < 2)
                    ? n
                    : numFunction.apply(n - 1) + numFunction.apply(n - 2);
        }

    }

    /* Does not work as we cannot reference "this" before the constructor returns

    record Fibonacci2(int upperBound,
                      IntFunction<Integer> numFunction) {

        public Fibonacci2(int upperBound) {
            this(upperBound, StableValue.ofIntFunction(upperBound, this::number));
        }

        public int number(int n) {
            return (n < 2)
                    ? n
                    : numFunction.apply(n - 1) + numFunction.apply(n - 2);
        }

    }
    */

    record Fibonacci2(int upperBound,
                      StableValue<IntFunction<Integer>> numFunction) {

        public Fibonacci2(int upperBound) {
            this(upperBound, StableValue.of());
            numFunction.setOrThrow(this::number);
        }

        public int number(int n) {
            return (n < 2)
                    ? n
                    : numFunction.orThrow().apply(n - 1) +
                      numFunction.orThrow().apply(n - 2);
        }

    }


    static
    class MapDemo {

        // 1. Declare a stable map of loggers with two allowable keys:
        //    "com.foo.Bar" and "com.foo.Baz"
        static final Map<String, StableValue<Logger>> LOGGERS =
                StableValue.ofMap(Set.of("com.foo.Bar", "com.foo.Baz"));

        // 2. Access the memoized map with as-declared-final performance
        //    (evaluation made before the first access)
        static Logger logger(String name) {
            return StableValue.computeIfUnset(LOGGERS, name, Logger::getLogger);
        }
    }

    static
    class SetDemo {

        static final Map<String, StableValue<Boolean>> INFO_LOGGABLE =
                StableValue.ofMap(Set.of("com.foo.Bar", "com.foo.Baz"));

        static boolean isInfoLoggable(String name) {
            return StableValue.computeIfUnset(INFO_LOGGABLE, name, n -> MapDemo.logger(n).isLoggable(Level.INFO));
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
            private static final Map<String, StableValue<Logger>> MAP =
                    StableValue.ofMap(Set.of("com.foo.Bar", "com.foo.Baz"));

            // 2. Declare a memoized (cached) function backed by the lazily computed map
            private static final Function<String, Logger> LOGGERS =
                    n -> StableValue.computeIfUnset(MAP, n, Logger::getLogger);

            private static final String NAME = "com.foo.Baz";

            public static void main(String[] args) {
                // 3. Access the memoized value via the function with as-declared-final
                //    performance (evaluation made before the first access)
                Logger logger = LOGGERS.apply(NAME);
            }

        }


        static
        class Memoized2 {

            // 1. Declare a memoized (cached) function backed by a lazily computed map
            private static final Function<String, Logger> MAPPER =
                    StableValue.ofFunction(
                            Set.of("com.foo.Bar", "com.foo.Baz"),
                            Logger::getLogger);

            private static final String NAME = "com.foo.Baz";

            public static void main(String[] args) {
                // 2. Access the memoized value via the function with as-declared-final
                //    performance (evaluation made before the first access)
                Logger logger = MAPPER.apply(NAME);
            }

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

}
