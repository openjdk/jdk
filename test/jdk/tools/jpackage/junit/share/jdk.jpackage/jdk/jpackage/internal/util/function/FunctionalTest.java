/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.util.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Supplier;
import jdk.jpackage.internal.util.Slot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class FunctionalTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_toRunnable(boolean error) {
        var reply = Slot.<Integer>createEmpty();

        var runnable = ThrowingRunnable.toRunnable(() -> {
            if (error) {
                throw new Exception();
            } else {
                reply.set(135);
            }
        });

        if (error) {
            assertThrowsExactly(ExceptionBox.class, runnable::run);
            assertTrue(reply.find().isEmpty());
        } else {
            runnable.run();
            assertEquals(135, reply.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_toSupplier(boolean error) {
        var supplier = ThrowingSupplier.<Integer>toSupplier(() -> {
            if (error) {
                throw new Exception();
            } else {
                return 135;
            }
        });

        if (error) {
            assertThrowsExactly(ExceptionBox.class, supplier::get);
        } else {
            assertEquals(135, supplier.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_toConsumer(boolean error) {
        var reply = Slot.<Integer>createEmpty();

        Runnable runnable = () -> {
            ThrowingConsumer.<Integer>toConsumer(v -> {
                if (error) {
                    throw new Exception();
                } else {
                    reply.set(v);
                }
            }).accept(135);
        };

        if (error) {
            assertThrowsExactly(ExceptionBox.class, runnable::run);
            assertTrue(reply.find().isEmpty());
        } else {
            runnable.run();
            assertEquals(135, reply.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_toBiConsumer(boolean error) {
        var reply = Slot.<Integer>createEmpty();
        var reply2 = Slot.<String>createEmpty();

        Runnable runnable = () -> {
            ThrowingBiConsumer.<Integer, String>toBiConsumer((x, y) -> {
                if (error) {
                    throw new Exception();
                } else {
                    reply.set(x);
                    reply2.set(y);
                }
            }).accept(456, "Hello");
        };

        if (error) {
            assertThrowsExactly(ExceptionBox.class, runnable::run);
            assertTrue(reply.find().isEmpty());
            assertTrue(reply2.find().isEmpty());
        } else {
            runnable.run();
            assertEquals(456, reply.get());
            assertEquals("Hello", reply2.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_toFunction(boolean error) {
        Supplier<String> supplier = () -> {
            return ThrowingFunction.<Integer, String>toFunction(v -> {
                if (error) {
                    throw new Exception();
                } else {
                    return String.valueOf(v);
                }
            }).apply(765);
        };

        if (error) {
            assertThrowsExactly(ExceptionBox.class, supplier::get);
        } else {
            assertEquals("765", supplier.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_toBiFunction(boolean error) {
        Supplier< Map.Entry<String, Integer>> supplier = () -> {
            return ThrowingBiFunction.<Integer, String, Map.Entry<String, Integer>>toBiFunction((x, y) -> {
                if (error) {
                    throw new Exception();
                } else {
                    return Map.entry(y, x + 23);
                }
            }).apply(400, "foo");
        };

        if (error) {
            assertThrowsExactly(ExceptionBox.class, supplier::get);
        } else {
            assertEquals(Map.entry("foo", 423), supplier.get());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_toUnaryOperator(boolean error) {
        Supplier<Integer> supplier = () -> {
            return ThrowingUnaryOperator.<Integer>toUnaryOperator(v -> {
                if (error) {
                    throw new Exception();
                } else {
                    return v - 222;
                }
            }).apply(777);
        };

        if (error) {
            assertThrowsExactly(ExceptionBox.class, supplier::get);
        } else {
            assertEquals(555, supplier.get());
        }
    }
}
