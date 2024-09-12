/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Test ScopedValue API
 * @enablePreview
 * @run junit ScopedValueAPI
 */

import java.lang.ScopedValue.CallableOp;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class ScopedValueAPI {

    private static Stream<ThreadFactory> factories() {
        return Stream.of(Thread.ofPlatform().factory(), Thread.ofVirtual().factory());
    }

    /**
     * Test that runWhere invokes the Runnable's run method.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testRunWhere(ThreadFactory factory) throws Exception {
        test(factory, () -> {
            class Box { static boolean executed; }
            ScopedValue<String> name = ScopedValue.newInstance();
            ScopedValue.runWhere(name, "duke", () -> { Box.executed = true; });
            assertTrue(Box.executed);
        });
    }

    /**
     * Test runWhere when the run method throws an exception.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testRunWhereThrows(ThreadFactory factory) throws Exception {
        test(factory, () -> {
            class FooException extends RuntimeException {  }
            ScopedValue<String> name = ScopedValue.newInstance();
            Runnable op = () -> { throw new FooException(); };
            assertThrows(FooException.class, () -> ScopedValue.runWhere(name, "duke", op));
            assertFalse(name.isBound());
        });
    }

    /**
     * Test that callWhere invokes the CallableOp's call method.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testCallWhere(ThreadFactory factory) throws Exception {
        test(factory, () -> {
            ScopedValue<String> name = ScopedValue.newInstance();
            String result = ScopedValue.callWhere(name, "duke", name::get);
            assertEquals("duke", result);
        });
    }

    /**
     * Test callWhere when the call method throws an exception.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testCallWhereThrows(ThreadFactory factory) throws Exception {
        test(factory, () -> {
            class FooException extends RuntimeException {  }
            ScopedValue<String> name = ScopedValue.newInstance();
            CallableOp<Void, RuntimeException> op = () -> { throw new FooException(); };
            assertThrows(FooException.class, () -> ScopedValue.callWhere(name, "duke", op));
            assertFalse(name.isBound());
        });
    }

    /**
     * Test get method.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testGet(ThreadFactory factory) throws Exception {
        test(factory, () -> {
            ScopedValue<String> name1 = ScopedValue.newInstance();
            ScopedValue<String> name2 = ScopedValue.newInstance();
            assertThrows(NoSuchElementException.class, name1::get);
            assertThrows(NoSuchElementException.class, name2::get);

            // runWhere
            ScopedValue.runWhere(name1, "duke", () -> {
                assertEquals("duke", name1.get());
                assertThrows(NoSuchElementException.class, name2::get);

            });
            assertThrows(NoSuchElementException.class, name1::get);
            assertThrows(NoSuchElementException.class, name2::get);

            // callWhere
            ScopedValue.callWhere(name1, "duke", () -> {
                assertEquals("duke", name1.get());
                assertThrows(NoSuchElementException.class, name2::get);
                return null;
            });
            assertThrows(NoSuchElementException.class, name1::get);
            assertThrows(NoSuchElementException.class, name2::get);
        });
    }

    /**
     * Test isBound method.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testIsBound(ThreadFactory factory) throws Exception {
        test(factory, () -> {
            ScopedValue<String> name1 = ScopedValue.newInstance();
            ScopedValue<String> name2 = ScopedValue.newInstance();
            assertFalse(name1.isBound());
            assertFalse(name2.isBound());

            // runWhere
            ScopedValue.runWhere(name1, "duke", () -> {
                assertTrue(name1.isBound());
                assertFalse(name2.isBound());
            });
            assertFalse(name1.isBound());
            assertFalse(name2.isBound());

            // callWhere
            ScopedValue.callWhere(name1, "duke", () -> {
                assertTrue(name1.isBound());
                assertFalse(name2.isBound());
                return null;
            });
            assertFalse(name1.isBound());
            assertFalse(name2.isBound());
        });
    }

    /**
     * Test orElse method.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testOrElse(ThreadFactory factory) throws Exception {
        test(factory, () -> {
            ScopedValue<String> name = ScopedValue.newInstance();
            assertNull(name.orElse(null));
            assertEquals("default", name.orElse("default"));

            // runWhere
            ScopedValue.runWhere(name, "duke", () -> {
                assertEquals("duke", name.orElse(null));
                assertEquals("duke", name.orElse("default"));
            });

            // callWhere
            ScopedValue.callWhere(name, "duke", () -> {
                assertEquals("duke", name.orElse(null));
                assertEquals("duke", name.orElse("default"));
                return null;
            });
        });
    }

    /**
     * Test orElseThrow method.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testOrElseThrow(ThreadFactory factory) throws Exception {
        test(factory, () -> {
            class FooException extends RuntimeException { }
            ScopedValue<String> name = ScopedValue.newInstance();
            assertThrows(FooException.class, () -> name.orElseThrow(FooException::new));

            // runWhere
            ScopedValue.runWhere(name, "duke", () -> {
                assertEquals("duke", name.orElseThrow(FooException::new));
            });

            // callWhere
            ScopedValue.callWhere(name, "duke", () -> {
                assertEquals("duke", name.orElseThrow(FooException::new));
                return null;
            });
        });
    }

    /**
     * Test two bindings.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testTwoBindings(ThreadFactory factory) throws Exception {
        test(factory, () -> {
            ScopedValue<String> name = ScopedValue.newInstance();
            ScopedValue<Integer> age = ScopedValue.newInstance();

            // Carrier.run
            ScopedValue.where(name, "duke").where(age, 100).run(() -> {
                assertTrue(name.isBound());
                assertTrue(age.isBound());
                assertEquals("duke", name.get());
                assertEquals(100, (int) age.get());
            });
            assertFalse(name.isBound());
            assertFalse(age.isBound());

            // Carrier.call
            ScopedValue.where(name, "duke").where(age, 100).call(() -> {
                assertTrue(name.isBound());
                assertTrue(age.isBound());
                assertEquals("duke", name.get());
                assertEquals(100, (int) age.get());
                return null;
            });
            assertFalse(name.isBound());
            assertFalse(age.isBound());
        });
    }

    /**
     * Test rebinding.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testRebinding(ThreadFactory factory) throws Exception {
        test(factory, () -> {
            ScopedValue<String> name = ScopedValue.newInstance();

            // runWhere
            ScopedValue.runWhere(name, "duke", () -> {
                assertTrue(name.isBound());
                assertEquals("duke", name.get());

                ScopedValue.runWhere(name, "duchess", () -> {
                    assertTrue(name.isBound());
                    assertEquals("duchess", name.get());
                });

                assertTrue(name.isBound());
                assertEquals("duke", name.get());
            });
            assertFalse(name.isBound());

            // callWhere
            ScopedValue.callWhere(name, "duke", () -> {
                assertTrue(name.isBound());
                assertEquals("duke", name.get());

                ScopedValue.callWhere(name, "duchess", () -> {
                    assertTrue(name.isBound());
                    assertEquals("duchess", name.get());
                    return null;
                });

                assertTrue(name.isBound());
                assertEquals("duke", name.get());
                return null;
            });
            assertFalse(name.isBound());
        });
    }

    /**
     * Test rebinding from null vaue to another value.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testRebindingFromNull(ThreadFactory factory) throws Exception {
        test(factory, () -> {
            ScopedValue<String> name = ScopedValue.newInstance();

            // runWhere
            ScopedValue.runWhere(name, null, () -> {
                assertTrue(name.isBound());
                assertNull(name.get());

                ScopedValue.runWhere(name, "duchess", () -> {
                    assertTrue(name.isBound());
                    assertTrue("duchess".equals(name.get()));
                });

                assertTrue(name.isBound());
                assertNull(name.get());
            });
            assertFalse(name.isBound());

            // callWhere
            ScopedValue.callWhere(name, null, () -> {
                assertTrue(name.isBound());
                assertNull(name.get());

                ScopedValue.callWhere(name, "duchess", () -> {
                    assertTrue(name.isBound());
                    assertTrue("duchess".equals(name.get()));
                    return null;
                });

                assertTrue(name.isBound());
                assertNull(name.get());
                return null;
            });
            assertFalse(name.isBound());
        });
    }

    /**
     * Test rebinding to null value.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testRebindingToNull(ThreadFactory factory) throws Exception {
        test(factory, () -> {
            ScopedValue<String> name = ScopedValue.newInstance();

            // runWhere
            ScopedValue.runWhere(name, "duke", () -> {
                assertTrue(name.isBound());
                assertEquals("duke", name.get());

                ScopedValue.runWhere(name, null, () -> {
                    assertTrue(name.isBound());
                    assertNull(name.get());
                });

                assertTrue(name.isBound());
                assertEquals("duke", name.get());
            });
            assertFalse(name.isBound());

            // callWhere
            ScopedValue.callWhere(name, "duke", () -> {
                assertTrue(name.isBound());
                assertEquals("duke", name.get());

                ScopedValue.callWhere(name, null, () -> {
                    assertTrue(name.isBound());
                    assertNull(name.get());
                    return null;
                });

                assertTrue(name.isBound());
                assertEquals("duke", name.get());
                return null;
            });
            assertFalse(name.isBound());
        });
    }

    /**
     * Test Carrier.get.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testCarrierGet(ThreadFactory factory) throws Exception {
        test(factory, () -> {
            ScopedValue<String> name = ScopedValue.newInstance();
            ScopedValue<Integer> age = ScopedValue.newInstance();

            // one scoped value
            var carrier1 = ScopedValue.where(name, "duke");
            assertEquals("duke", carrier1.get(name));
            assertThrows(NoSuchElementException.class, () -> carrier1.get(age));

            // two scoped values
            var carrier2 = carrier1.where(age, 20);
            assertEquals("duke", carrier2.get(name));
            assertEquals(20, (int) carrier2.get(age));
        });
    }

    /**
     * Test NullPointerException.
     */
    @Test
    void testNullPointerException() {
        ScopedValue<String> name = ScopedValue.newInstance();

        assertThrows(NullPointerException.class, () -> ScopedValue.where(null, "duke"));

        assertThrows(NullPointerException.class, () -> ScopedValue.runWhere(null, "duke", () -> { }));
        assertThrows(NullPointerException.class, () -> ScopedValue.runWhere(name, "duke", null));

        assertThrows(NullPointerException.class, () -> ScopedValue.callWhere(null, "duke", () -> ""));
        assertThrows(NullPointerException.class, () -> ScopedValue.callWhere(name, "duke", null));

        assertThrows(NullPointerException.class, () -> name.orElseThrow(null));

        var carrier = ScopedValue.where(name, "duke");
        assertThrows(NullPointerException.class, () -> carrier.where(null, "duke"));
        assertThrows(NullPointerException.class, () -> carrier.get((ScopedValue<?>)null));
        assertThrows(NullPointerException.class, () -> carrier.run(null));
        assertThrows(NullPointerException.class, () -> carrier.call(null));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Run the given task in a thread created with the given thread factory.
     * @throws Exception if the task throws an exception
     */
    private static void test(ThreadFactory factory, ThrowingRunnable task) throws Exception {
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            var future = executor.submit(() -> {
                task.run();
                return null;
            });
            try {
                future.get();
            } catch (ExecutionException ee) {
                Throwable cause = ee.getCause();
                if (cause instanceof Exception e)
                    throw e;
                if (cause instanceof Error e)
                    throw e;
                throw new RuntimeException(cause);
            }
        }
    }
}
