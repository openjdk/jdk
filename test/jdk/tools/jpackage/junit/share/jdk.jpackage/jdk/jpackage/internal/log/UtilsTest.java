/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.log;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UtilsTest {

    @ParameterizedTest
    @ValueSource(classes = {TestLogger.class, AlwaysEnabledLogger.class})
    void test_discardingLogger(Class<? extends TestLogger> type) {
        assertDiscardingLogger(Utils.discardingLogger(type));
    }

    @Test
    void test_discardLogMessagesLogger_negative() {

        var logger = Utils.discardingLogger(MalformedLogger.class);

        assertFalse(logger.enabled());

        assertThrows(AssertionError.class, () -> {
            logger.enabled(new Object[0]);
        });

        assertThrows(AssertionError.class, () -> {
            logger.foo();
        });

        assertThrows(AssertionError.class, () -> {
            logger.bar();
        });
    }

    @Test
    void test_teeLogger() {

        var buf = new StringBuilder();

        var a = new TestLogger() {

            @Override
            public void foo() {
                buf.append("a");
            }

            @Override
            public void foo(Consumer<String> sink) {
                sink.accept("A");
            }

        };

        var b = new AlwaysEnabledLogger() {

            @Override
            public void foo() {
                buf.append("b");
            }

            @Override
            public void foo(Consumer<String> sink) {
                sink.accept("B");
            }

        };

        for (var logger : List.of(a, b)) {
            assertSame(logger, Utils.teeLogger(TestLogger.class, List.of(logger)));
        }

        var logger = Utils.teeLogger(TestLogger.class, List.of(a, b));

        assertTrue(logger.enabled());

        logger.foo();
        logger.foo(buf::append);
        logger.fooIfEnabled(buf);

        assertEquals("abABxy", buf.toString());
    }

    @Test
    void test_teeLogger_empty() {
        assertDiscardingLogger(Utils.teeLogger(TestLogger.class, List.of()));
    }

    @Test
    void test_teeLogger_disabled() {

        var disabled = new TestLogger() {

            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public void foo() {
                throw new AssertionError();
            }

            @Override
            public void foo(Consumer<String> sink) {
                throw new AssertionError();
            }

        };

        var buf = new StringBuilder();

        var a = new TestLogger() {

            @Override
            public void foo() {
                buf.append("a");
            }

            @Override
            public void foo(Consumer<String> sink) {
                sink.accept("A");
            }

        };

        assertDiscardingLogger(Utils.teeLogger(TestLogger.class, List.of(disabled)));
        assertDiscardingLogger(Utils.teeLogger(TestLogger.class, List.of(disabled, disabled)));
        assertSame(a, Utils.teeLogger(TestLogger.class, List.of(disabled, a, disabled)));

        var logger = Utils.teeLogger(TestLogger.class, List.of(disabled, a, disabled, a, a));

        logger.foo();
        logger.foo(buf::append);
        logger.fooIfEnabled(buf);

        assertEquals("aaaAAAxxx", buf.toString());
    }

    @Test
    void test_teeLogger_negative() {

        var loggers = new ArrayList<Logger>();
        loggers.add(null);
        loggers.add(new Logger() {});

        assertThrows(NullPointerException.class, () -> {
            Utils.teeLogger(Logger.class, loggers);
        });

        var a = new Logger() {
        };

        assertThrows(IllegalArgumentException.class, () -> {
            Utils.teeLogger(a.getClass(), List.of());
        });
    }

    private interface TestLogger extends Logger {

        void foo();

        void foo(Consumer<String> sink);

        default void foo(StringBuilder sb) {
            sb.append("x");
        }

        default void fooIfEnabled(StringBuilder sb) {
            foo(sb);
        }
    }

    private interface AlwaysEnabledLogger extends TestLogger {

        @Override
        default boolean enabled() {
            return true;
        }

        @Override
        default void foo(StringBuilder sb) {
            if (enabled()) {
                sb.append("y");
            } else {
                TestLogger.super.foo(sb);
            }
        }
    }

    private interface MalformedLogger extends Logger {

        int foo();

        boolean enabled(Object...args);

        default int bar() {
            throw new AssertionError();
        }
    }

    private void assertDiscardingLogger(TestLogger logger) {

        assertFalse(logger.enabled());

        logger.foo();

        do {
            var buf = new StringWriter();
            logger.foo(buf::append);
            assertTrue(buf.toString().isEmpty());
        } while(false);

        do {
            var sb = new StringBuilder();
            logger.foo(sb);
            assertTrue(sb.isEmpty());
        } while(false);

        assertDoesNotThrow(logger::hashCode);
        assertDoesNotThrow(logger::toString);
    }
}
