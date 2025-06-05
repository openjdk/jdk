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
 * @summary Test ThreadFlock with scoped values
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 * @run junit WithScopedValue
 */

import jdk.internal.misc.ThreadFlock;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.StructureViolationException;
import java.util.concurrent.atomic.AtomicReference;
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
     * Test inheritance of a scoped value.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testInheritsScopedValue(ThreadFactory factory) throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        String value = ScopedValue.where(name, "duke").call(() -> {
            var result = new AtomicReference<String>();
            try (var flock = ThreadFlock.open(null)) {
                Thread thread = factory.newThread(() -> {
                    // child
                    result.set(name.get());
                });
                flock.start(thread);
            }
            return result.get();
        });
        assertEquals("duke", value);
    }

    /**
     * Test exiting a dynamic scope with open thread flocks.
     */
    @Test
    void testStructureViolation1() {
        ScopedValue<String> name = ScopedValue.newInstance();
        class Box {
            ThreadFlock flock1;
            ThreadFlock flock2;
        }
        var box = new Box();
        try {
            ScopedValue.where(name, "x1").run(() -> {
                box.flock1 = ThreadFlock.open(null);
                box.flock2 = ThreadFlock.open(null);
            });
            fail();
        } catch (StructureViolationException expected) { }
        assertTrue(box.flock1.isClosed());
        assertTrue(box.flock2.isClosed());
    }

    /**
     * Test closing a thread flock while in a dynamic scope and with enclosing thread
     * flocks. This test closes enclosing flock1.
     */
    @Test
    void testStructureViolation2() {
        ScopedValue<String> name = ScopedValue.newInstance();
        try (var flock1 = ThreadFlock.open("flock1")) {
            ScopedValue.where(name, "x1").run(() -> {
                try (var flock2 = ThreadFlock.open("flock2")) {
                    ScopedValue.where(name, "x2").run(() -> {
                        try (var flock3 = ThreadFlock.open("flock3")) {
                            ScopedValue.where(name, "x3").run(() -> {
                                var flock4 = ThreadFlock.open("flock4");

                                try {
                                    flock1.close();
                                    fail();
                                } catch (StructureViolationException expected) { }

                                assertTrue(flock1.isClosed());
                                assertTrue(flock2.isClosed());
                                assertTrue(flock3.isClosed());
                                assertTrue(flock4.isClosed());
                            });
                        }
                    });
                }
            });
        }
    }

    /**
     * Test closing a thread flock while in a dynamic scope and with enclosing thread
     * flocks. This test closes enclosing flock2.
     */
    @Test
    void testStructureViolation3() {
        ScopedValue<String> name = ScopedValue.newInstance();
        try (var flock1 = ThreadFlock.open("flock1")) {
            ScopedValue.where(name, "x1").run(() -> {
                try (var flock2 = ThreadFlock.open("flock2")) {
                    ScopedValue.where(name, "x2").run(() -> {
                        try (var flock3 = ThreadFlock.open("flock3")) {
                            ScopedValue.where(name, "x3").run(() -> {
                                var flock4 = ThreadFlock.open("flock4");

                                try {
                                    flock2.close();
                                    fail();
                                } catch (StructureViolationException expected) { }

                                assertFalse(flock1.isClosed());
                                assertTrue(flock2.isClosed());
                                assertTrue(flock3.isClosed());
                                assertTrue(flock4.isClosed());
                            });
                        }
                    });
                }
            });
        }
    }

    /**
     * Test closing a thread flock while in a dynamic scope and with enclosing thread
     * flocks. This test closes enclosing flock3.
     */
    @Test
    void testStructureViolation4() {
        ScopedValue<String> name = ScopedValue.newInstance();
        try (var flock1 = ThreadFlock.open("flock1")) {
            ScopedValue.where(name, "x1").run(() -> {
                try (var flock2 = ThreadFlock.open("flock2")) {
                    ScopedValue.where(name, "x2").run(() -> {
                        try (var flock3 = ThreadFlock.open("flock3")) {
                            ScopedValue.where(name, "x3").run(() -> {
                                var flock4 = ThreadFlock.open("flock4");

                                try {
                                    flock3.close();
                                    fail();
                                } catch (StructureViolationException expected) { }

                                assertFalse(flock1.isClosed());
                                assertFalse(flock2.isClosed());
                                assertTrue(flock3.isClosed());
                                assertTrue(flock4.isClosed());
                            });
                        }
                    });
                }
            });
        }
    }

    /**
     * Test start when a scoped value is bound after a thread flock is created.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testStructureViolation5(ThreadFactory factory) throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        try (var flock = ThreadFlock.open(null)) {
            ScopedValue.where(name, "duke").run(() -> {
                Thread thread = factory.newThread(() -> { });
                assertThrows(StructureViolationException.class, () -> flock.start(thread));
            });
        }
    }

    /**
     * Test start when a scoped value is re-bound after a thread flock is created.
     */
    @ParameterizedTest
    @MethodSource("factories")
    void testStructureViolation6(ThreadFactory factory) throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        ScopedValue.where(name, "duke").run(() -> {
            try (var flock = ThreadFlock.open(null)) {
                ScopedValue.where(name, "duchess").run(() -> {
                    Thread thread = factory.newThread(() -> { });
                    assertThrows(StructureViolationException.class, () -> flock.start(thread));
                });
            }
        });
    }
}
