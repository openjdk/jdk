/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8284199 8296779 8306647
 * @summary Basic tests for StructuredTaskScope with scoped values
 * @enablePreview
 * @run junit WithScopedValue
 */

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructureViolationException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class WithScopedValue {

    private static Stream<ThreadFactory> factories() {
        return Stream.of(Thread.ofPlatform().factory(), Thread.ofVirtual().factory());
    }

    /**
     * Test that fork inherits a scoped value into a child thread.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkInheritsScopedValue1(ThreadFactory factory) throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        String value = ScopedValue.where(name, "x").call(() -> {
            try (var scope = StructuredTaskScope.open(Joiner.awaitAll(),
                                                      cf -> cf.withThreadFactory(factory))) {
                Subtask<String> subtask = scope.fork(() -> {
                    return name.get(); // child should read "x"
                });
                scope.join();
                return subtask.get();
            }
        });
        assertEquals(value, "x");
    }

    /**
     * Test that fork inherits a scoped value into a grandchild thread.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkInheritsScopedValue2(ThreadFactory factory) throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        String value = ScopedValue.where(name, "x").call(() -> {
            try (var scope1 = StructuredTaskScope.open(Joiner.awaitAll(),
                                                       cf -> cf.withThreadFactory(factory))) {
                Subtask<String> subtask1 = scope1.fork(() -> {
                    try (var scope2 = StructuredTaskScope.open(Joiner.awaitAll(),
                                                               cf -> cf.withThreadFactory(factory))) {
                        Subtask<String> subtask2 = scope2.fork(() -> {
                            return name.get(); // grandchild should read "x"
                        });
                        scope2.join();
                        return subtask2.get();
                    }
                });
                scope1.join();
                return subtask1.get();
            }
        });
        assertEquals(value, "x");
    }

    /**
     * Test that fork inherits a rebound scoped value into a grandchild thread.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testForkInheritsScopedValue3(ThreadFactory factory) throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        String value = ScopedValue.where(name, "x").call(() -> {
            try (var scope1 = StructuredTaskScope.open(Joiner.awaitAll(),
                                                       cf -> cf.withThreadFactory(factory))) {
                Subtask<String> subtask1 = scope1.fork(() -> {
                    assertEquals(name.get(), "x");  // child should read "x"

                    // rebind name to "y"
                    String grandchildValue = ScopedValue.where(name, "y").call(() -> {
                        try (var scope2 = StructuredTaskScope.open(Joiner.awaitAll(),
                                                                   cf -> cf.withThreadFactory(factory))) {
                            Subtask<String> subtask2 = scope2.fork(() -> {
                                return name.get(); // grandchild should read "y"
                            });
                            scope2.join();
                            return subtask2.get();
                        }
                    });

                    assertEquals(name.get(), "x");  // child should read "x"
                    return grandchildValue;
                });
                scope1.join();
                return subtask1.get();
            }
        });
        assertEquals(value, "y");
    }

    /**
     * Test exiting a dynamic scope with an open task scope.
     */
    @Test
    void testStructureViolation1() throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        class Box {
            StructuredTaskScope<Object, Void> scope;
        }
        var box = new Box();
        try {
            try {
                ScopedValue.where(name, "x").run(() -> {
                    box.scope = StructuredTaskScope.open(Joiner.awaitAll());
                });
                fail();
            } catch (StructureViolationException expected) { }

            // underlying flock should be closed and fork should fail to start a thread
            StructuredTaskScope<Object, Void> scope = box.scope;
            AtomicBoolean ran = new AtomicBoolean();
            Subtask<Object> subtask = scope.fork(() -> {
                ran.set(true);
                return null;
            });
            scope.join();
            assertEquals(Subtask.State.UNAVAILABLE, subtask.state());
            assertFalse(ran.get());
        } finally {
            StructuredTaskScope<Object, Void> scope = box.scope;
            if (scope != null) {
                scope.close();
            }
        }
    }

    /**
     * Test closing a StructuredTaskScope while executing in a dynamic scope.
     */
    @Test
    void testStructureViolation2() throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
            ScopedValue.where(name, "x").run(() -> {
                assertThrows(StructureViolationException.class, scope::close);
            });
        }
    }

    /**
     * Test fork when a scoped value is bound after a StructuredTaskScope is created.
     */
    @Test
    void testStructureViolation3() throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
            ScopedValue.where(name, "x").run(() -> {
                assertThrows(StructureViolationException.class,
                        () -> scope.fork(() -> "foo"));
            });
        }
    }

    /**
     * Test fork when a scoped value is re-bound after a StructuredTaskScope is created.
     */
    @Test
    void testStructureViolation4() throws Exception {
        ScopedValue<String> name1 = ScopedValue.newInstance();
        ScopedValue<String> name2 = ScopedValue.newInstance();

        // rebind
        ScopedValue.where(name1, "x").run(() -> {
            try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
                ScopedValue.where(name1, "y").run(() -> {
                    assertThrows(StructureViolationException.class,
                            () -> scope.fork(() -> "foo"));
                });
            }
        });

        // new binding
        ScopedValue.where(name1, "x").run(() -> {
            try (var scope = StructuredTaskScope.open(Joiner.awaitAll())) {
                ScopedValue.where(name2, "y").run(() -> {
                    assertThrows(StructureViolationException.class,
                            () -> scope.fork(() -> "foo"));
                });
            }
        });
    }
}
