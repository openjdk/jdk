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
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} JepTest.java
 * @run junit/othervm --enable-preview JepTest
 */

import jdk.internal.lang.StableValue;

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
import java.util.logging.Logger;

final class JepTest {

    class Foo {
        // 1. Declare a Stable field
        private static final StableValue<Logger> LOGGER = StableValue.newInstance();

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
                StableValue.newCachingSupplier( () -> Logger.getLogger("com.company.Foo"), null );


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
                StableValue.lazyMap(Set.of("com.company.Foo", "com.company.Bar"), Logger::getLogger);

        // 2. Access the lazy map with as-declared-final performance
        //    (evaluation made before the first access)
        static Logger logger(String name) {
            return LOGGERS.get(name);
        }
    }


    class Cached {

        // 1. Centrally declare a cached function backed by a map of stable values
        private static final Function<String, Logger> LOGGERS =
                StableValue.newCachingFunction(Set.of("com.company.Foo", "com.company.Bar"),
                Logger::getLogger, null);

        private static final String NAME = "com.company.Foo";

        // 2. Access the cached value via the function with as-declared-final
        //    performance (evaluation made before the first access)
        Logger logger = LOGGERS.apply(NAME);
    }

    class CachedBackground {

        // 1. Centrally declare a cached function backed by a map of stable values
        //    computed in the background by two distinct virtual threads.
        private static final Function<String, Logger> LOGGERS =
                StableValue.newCachingFunction(Set.of("com.company.Foo", "com.company.Bar"),
                Logger::getLogger,
                // Create cheap virtual threads for background computation
                Thread.ofVirtual().factory());

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
                    StableValue.newCachingIntFunction(SIZE, ErrorMessages::readFromFile, null);

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
}
