/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests for StructuredTaskScope with scoped values
 * @enablePreview
 * @modules jdk.incubator.concurrent
 * @run testng WithScopedValue
 */

import jdk.incubator.concurrent.ScopedValue;
import jdk.incubator.concurrent.StructuredTaskScope;
import jdk.incubator.concurrent.StructureViolationException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class WithScopedValue {

    @DataProvider
    public Object[][] factories() {
        return new Object[][] {
                { Thread.ofPlatform().factory() },
                { Thread.ofVirtual().factory() },
        };
    }

    /**
     * Test that fork inherits a scoped value into a child thread.
     */
    @Test(dataProvider = "factories")
    public void testForkInheritsScopedValue1(ThreadFactory factory) throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        String value = ScopedValue.where(name, "x", () -> {
            try (var scope = new StructuredTaskScope<String>(null, factory)) {
                Future<String> future = scope.fork(() -> {
                    return name.get(); // child should read "x"
                });
                scope.join();
                return future.resultNow();
            }
        });
        assertEquals(value, "x");
    }

    /**
     * Test that fork inherits a scoped value into a grandchild thread.
     */
    @Test(dataProvider = "factories")
    public void testForkInheritsScopedValue2(ThreadFactory factory) throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        String value = ScopedValue.where(name, "x", () -> {
            try (var scope1 = new StructuredTaskScope<String>(null, factory)) {
                Future<String> future1 = scope1.fork(() -> {
                    try (var scope2 = new StructuredTaskScope<String>(null, factory)) {
                        Future<String> future2 = scope2.fork(() -> {
                            return name.get(); // grandchild should read "x"
                        });
                        scope2.join();
                        return future2.resultNow();
                    }
                });
                scope1.join();
                return future1.resultNow();
            }
        });
        assertEquals(value, "x");
    }

    /**
     * Test that fork inherits a rebound scoped value into a grandchild thread.
     */
    @Test(dataProvider = "factories")
    public void testForkInheritsScopedValue3(ThreadFactory factory) throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        String value = ScopedValue.where(name, "x", () -> {
            try (var scope1 = new StructuredTaskScope<String>(null, factory)) {
                Future<String> future1 = scope1.fork(() -> {
                    assertEquals(name.get(), "x");  // child should read "x"

                    // rebind name to "y"
                    String grandchildValue = ScopedValue.where(name, "y", () -> {
                        try (var scope2 = new StructuredTaskScope<String>(null, factory)) {
                            Future<String> future2 = scope2.fork(() -> {
                                return name.get(); // grandchild should read "y"
                            });
                            scope2.join();
                            return future2.resultNow();
                        }
                    });

                    assertEquals(name.get(), "x");  // child should read "x"
                    return grandchildValue;
                });
                scope1.join();
                return future1.resultNow();
            }
        });
        assertEquals(value, "y");
    }

    /**
     * Test exiting a dynamic scope with an open task scope.
     */
    public void testStructureViolation1() throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        class Box {
            StructuredTaskScope<Object> scope;
        }
        var box = new Box();
        try {
            try {
                ScopedValue.where(name, "x", () -> {
                    box.scope = new StructuredTaskScope<Object>();
                });
                fail();
            } catch (StructureViolationException expected) { }

            // underlying flock should be closed, fork should return a cancelled task
            StructuredTaskScope<Object> scope = box.scope;
            AtomicBoolean ran = new AtomicBoolean();
            Future<Object> future = scope.fork(() -> {
                ran.set(true);
                return null;
            });
            assertTrue(future.isCancelled());
            scope.join();
            assertFalse(ran.get());

        } finally {
            StructuredTaskScope<Object> scope = box.scope;
            if (scope != null) {
                scope.close();
            }
        }
    }

    /**
     * Test closing a StructuredTaskScope while executing in a dynamic scope.
     */
    public void testStructureViolation2() throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        try (var scope = new StructuredTaskScope<String>()) {
            ScopedValue.where(name, "x", () -> {
                assertThrows(StructureViolationException.class, scope::close);
            });
        }
    }

    /**
     * Test fork when a scoped value is bound after a StructuredTaskScope is created.
     */
    public void testStructureViolation3() throws Exception {
        ScopedValue<String> name = ScopedValue.newInstance();
        try (var scope = new StructuredTaskScope<String>()) {
            ScopedValue.where(name, "x", () -> {
                assertThrows(StructureViolationException.class,
                        () -> scope.fork(() -> "foo"));
            });
        }
    }

    /**
     * Test fork when a scoped value is re-bound after a StructuredTaskScope is created.
     */
    public void testStructureViolation4() throws Exception {
        ScopedValue<String> name1 = ScopedValue.newInstance();
        ScopedValue<String> name2 = ScopedValue.newInstance();

        // rebind
        ScopedValue.where(name1, "x", () -> {
            try (var scope = new StructuredTaskScope<String>()) {
                ScopedValue.where(name1, "y", () -> {
                    assertThrows(StructureViolationException.class,
                            () -> scope.fork(() -> "foo"));
                });
            }
        });

        // new binding
        ScopedValue.where(name1, "x", () -> {
            try (var scope = new StructuredTaskScope<String>()) {
                ScopedValue.where(name2, "y", () -> {
                    assertThrows(StructureViolationException.class,
                            () -> scope.fork(() -> "foo"));
                });
            }
        });
    }
}
