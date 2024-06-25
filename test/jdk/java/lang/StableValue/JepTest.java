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

    class Bar {
        // 1. Declare a Stable field
        private static final StableValue<Logger> LOGGER = StableValue.newInstance();

        static Logger logger() {

            if (!LOGGER.isSet()) {
                // 2. Set the stable value _after_ the field was declared
                LOGGER.trySet(Logger.getLogger("com.foo.Bar"));
            }

            // 3. Access the stable value with as-declared-final performance
            return LOGGER.orElseThrow();
        }
    }

    class Bar2 {

        // 1. Declare a caching supplier
        private static final Supplier<Logger> LOGGER =
                StableValue.newCachingSupplier( () -> Logger.getLogger("com.foo.Bar"), null );


        static Logger logger() {
            // 2. Access the stable value with as-declared-final performance
            //    (single evaluation made before the first access)
            return LOGGER.get();
        }
    }


    class Memoized {

        // 1. Declare a memoized (cached) function backed by a stable map
        private static final Function<String, Logger> LOGGERS =
                StableValue.newCachingFunction(Set.of("com.foo.Bar", "com.foo.Baz"),
                Logger::getLogger, null);

        private static final String NAME = "com.foo.Baz";

        // 2. Access the memoized value via the function with as-declared-final
        //    performance (evaluation made before the first access)
        Logger logger = LOGGERS.apply(NAME);
    }

    class A {

        static
        class ErrorMessages {
            private static final int SIZE = 8;

            // 1. Declare a memoized IntFunction backed by a stable list
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

            // 3. Access the memoized list element with as-declared-final performance
            //    (evaluation made before the first access)
            String msg = ERROR_FUNCTION.apply(2);
        }
    }
}
