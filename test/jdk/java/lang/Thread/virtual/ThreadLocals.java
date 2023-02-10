/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test Virtual threads using thread locals
 * @library /test/lib
 * @enablePreview
 * @run junit ThreadLocals
 */

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThreadLocals {
    static final ThreadLocal<Object> LOCAL = new ThreadLocal<>();
    static final ThreadLocal<Object> INHERITED_LOCAL = new InheritableThreadLocal<>();

    /**
     * Basic test of thread local set/get.
     */
    @Test
    void testThreadLocal1() throws Exception {
        for (int i = 0; i < 10; i++) {
            VThreadRunner.run(() -> {
                assertNull(LOCAL.get());
                Object obj = new Object();
                LOCAL.set(obj);
                assertTrue(LOCAL.get() == obj);
            });
        }
    }

    /**
     * Test setting thread local before blocking operation.
     */
    @Test
    void testThreadLocal2() throws Exception {
        VThreadRunner.run(() -> {
            assertNull(LOCAL.get());
            Object obj = new Object();
            LOCAL.set(obj);
            try { Thread.sleep(100); } catch (InterruptedException e) { }
            assertTrue(LOCAL.get() == obj);
        });
    }

    /**
     * Test Thread that cannot set values for its copy of thread-locals.
     */
    @Test
    void testThreadLocal3() throws Exception {
        Object INITIAL_VALUE = new Object();
        ThreadLocal<Object> LOCAL2 = new ThreadLocal<>() {
            @Override
            protected Object initialValue() {
                return INITIAL_VALUE;
            }
        };
        ThreadLocal<Object> INHERITED_LOCAL2 = new InheritableThreadLocal<>()  {
            @Override
            protected Object initialValue() {
                return INITIAL_VALUE;
            }
        };

        VThreadRunner.run(VThreadRunner.NO_THREAD_LOCALS, () -> {
            assertThrows(UnsupportedOperationException.class, () -> LOCAL.set(null));
            assertThrows(UnsupportedOperationException.class, () -> LOCAL.set(new Object()));
            assertNull(LOCAL.get());
            LOCAL.remove();  // should not throw

            assertThrows(UnsupportedOperationException.class, () -> LOCAL2.set(null));
            assertThrows(UnsupportedOperationException.class, () -> LOCAL2.set(new Object()));
            assertTrue(LOCAL2.get() == INITIAL_VALUE);
            LOCAL2.remove();  // should not throw

            assertThrows(UnsupportedOperationException.class, () -> INHERITED_LOCAL.set(null));
            assertThrows(UnsupportedOperationException.class, () -> INHERITED_LOCAL.set(new Object()));
            assertNull(INHERITED_LOCAL.get());
            INHERITED_LOCAL.remove();  // should not throw

            assertThrows(UnsupportedOperationException.class, () -> INHERITED_LOCAL2.set(null));
            assertThrows(UnsupportedOperationException.class, () -> INHERITED_LOCAL2.set(new Object()));
            assertTrue(INHERITED_LOCAL2.get() == INITIAL_VALUE);
            INHERITED_LOCAL2.remove();  // should not throw
        });
    }

    /**
     * Basic test of inheritable thread local set/get, no initial value inherited.
     */
    @Test
    void testInheritedThreadLocal1() throws Exception {
        assertNull(INHERITED_LOCAL.get());
        for (int i = 0; i < 10; i++) {
            VThreadRunner.run(() -> {
                assertNull(INHERITED_LOCAL.get());
                Object obj = new Object();
                INHERITED_LOCAL.set(obj);
                assertTrue(INHERITED_LOCAL.get() == obj);
            });
        }
        assertNull(INHERITED_LOCAL.get());
    }

    /**
     * Test inheriting initial value of InheritableThreadLocal from platform thread.
     */
    @Test
    void testInheritedThreadLocal2() throws Exception {
        assertNull(INHERITED_LOCAL.get());
        var obj = new Object();
        INHERITED_LOCAL.set(obj);
        try {
            VThreadRunner.run(() -> {
                assertTrue(INHERITED_LOCAL.get() == obj);
            });
        } finally {
            INHERITED_LOCAL.remove();
        }
    }

    /**
     * Test inheriting initial value of InheritableThreadLocal from virtual thread.
     */
    @Test
    void testInheritedThreadLocal3() throws Exception {
        assertNull(INHERITED_LOCAL.get());
        VThreadRunner.run(() -> {
            var obj = new Object();
            INHERITED_LOCAL.set(obj);
            VThreadRunner.run(() -> {
                assertTrue(INHERITED_LOCAL.get() == obj);
            });
            assertTrue(INHERITED_LOCAL.get() == obj);

        });
        assertNull(INHERITED_LOCAL.get());
    }

    /**
     * Test Thread that does not inherit initial value of InheritableThreadLocals
     * from platform thread.
     */
    @Test
    void testInheritedThreadLocal4() throws Exception {
        assertNull(INHERITED_LOCAL.get());
        var obj = new Object();
        INHERITED_LOCAL.set(obj);
        try {
            int characteristics = VThreadRunner.NO_INHERIT_THREAD_LOCALS;
            VThreadRunner.run(characteristics, () -> {
                assertNull(INHERITED_LOCAL.get());
            });
        } finally {
            INHERITED_LOCAL.remove();
        }
    }

    /**
     * Test Thread that does not inherit initial value of InheritableThreadLocals
     * from virtual thread.
     */
    @Test
    void testInheritedThreadLocal5() throws Exception {
        assertNull(INHERITED_LOCAL.get());
        VThreadRunner.run(() -> {
            var obj = new Object();
            INHERITED_LOCAL.set(obj);
            int characteristics = VThreadRunner.NO_INHERIT_THREAD_LOCALS;
            VThreadRunner.run(characteristics, () -> {
                assertNull(INHERITED_LOCAL.get());
            });
            assertTrue(INHERITED_LOCAL.get() == obj);

        });
        assertNull(INHERITED_LOCAL.get());
    }
}
