/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.jdk9.test.ea;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.graalvm.compiler.core.test.ea.EATestBase;

import org.junit.Test;

import jdk.vm.ci.meta.JavaConstant;

public class AtomicVirtualizationTests extends EATestBase {
    private static final TestObject OBJ1 = new TestObject(1);
    private static final TestObject OBJ2 = new TestObject(2);
    private static TestObject obj6 = new TestObject(6);
    private static TestObject obj7 = new TestObject(7);
    private static TestObject obj8 = new TestObject(8);

    private static final class TestObject {
        final int id;

        private TestObject(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TestObject that = (TestObject) o;
            return id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "TestObject{id=" + id + '}';
        }
    }

    // c = constant (heap-allocated); v = virtual; u = unknown (heap-allocated)

    public static boolean cvvNoMatchCAS() {
        AtomicReference<TestObject> a = new AtomicReference<>(null);
        return a.compareAndSet(new TestObject(3), new TestObject(4));
    }

    @Test
    public void testcvvNoMatchCAS() {
        testAtomicVirtualization("cvvNoMatchCAS", JavaConstant.INT_0);
    }

    public static Object cvvNoMatchCAE() {
        AtomicReference<TestObject> a = new AtomicReference<>(null);
        return a.compareAndExchange(new TestObject(3), new TestObject(4));
    }

    @Test
    public void testcvvNoMatchCAE() {
        testAtomicVirtualization("cvvNoMatchCAE", JavaConstant.NULL_POINTER);
    }

    public static boolean ccvNoMatchCAS() {
        AtomicReference<TestObject> a = new AtomicReference<>(null);
        return a.compareAndSet(OBJ1, new TestObject(3));
    }

    @Test
    public void testccvNoMatchCAS() {
        testAtomicVirtualization("ccvNoMatchCAS", JavaConstant.INT_0);
    }

    public static Object ccvNoMatchCAE() {
        AtomicReference<TestObject> a = new AtomicReference<>(null);
        return a.compareAndExchange(OBJ1, new TestObject(3));
    }

    @Test
    public void testccvNoMatchCAE() {
        testAtomicVirtualization("ccvNoMatchCAE", JavaConstant.NULL_POINTER);
    }

    public static boolean cccNoMatchCAS() {
        AtomicReference<TestObject> a = new AtomicReference<>(null);
        return a.compareAndSet(OBJ1, OBJ2);
    }

    @Test
    public void testcccNoMatchCAS() {
        testAtomicVirtualization("cccNoMatchCAS", JavaConstant.INT_0);
    }

    public static Object cccNoMatchCAE() {
        AtomicReference<TestObject> a = new AtomicReference<>(null);
        return a.compareAndExchange(OBJ1, OBJ2);
    }

    @Test
    public void testcccNoMatchCAE() {
        testAtomicVirtualization("cccNoMatchCAE", JavaConstant.NULL_POINTER);
    }

    public static boolean ccvCAS() {
        AtomicReference<TestObject> a = new AtomicReference<>(null);
        return a.compareAndSet(null, new TestObject(3));
    }

    @Test
    public void testccvCAS() {
        testAtomicVirtualization("ccvCAS", JavaConstant.INT_1);
    }

    public static Object ccvCAE() {
        AtomicReference<TestObject> a = new AtomicReference<>(null);
        return a.compareAndExchange(null, new TestObject(3));
    }

    @Test
    public void testccvCAE() {
        testAtomicVirtualization("ccvCAE", null, 0);
    }

    public static boolean cccCAS() {
        AtomicReference<TestObject> a = new AtomicReference<>(null);
        return a.compareAndSet(null, OBJ2);
    }

    @Test
    public void testcccCAS() {
        testAtomicVirtualization("cccCAS", JavaConstant.INT_1);
    }

    public static Object cccCAE() {
        AtomicReference<TestObject> a = new AtomicReference<>(null);
        return a.compareAndExchange(null, OBJ2);
    }

    @Test
    public void testcccCAE() {
        testAtomicVirtualization("cccCAE", null);
    }

    public static boolean vvvNoMatchCAS() {
        AtomicReference<TestObject> a = new AtomicReference<>(new TestObject(3));
        return a.compareAndSet(new TestObject(4), new TestObject(5));
    }

    @Test
    public void testvvvNoMatchCAS() {
        testAtomicVirtualization("vvvNoMatchCAS", JavaConstant.INT_0);
    }

    public static Object vvvNoMatchCAE() {
        AtomicReference<TestObject> a = new AtomicReference<>(new TestObject(3));
        return a.compareAndExchange(new TestObject(4), new TestObject(5));
    }

    @Test
    public void testvvvNoMatchCAE() {
        testAtomicVirtualization("vvvNoMatchCAE", null, 1);
    }

    public static boolean vcvNoMatchCAS() {
        AtomicReference<TestObject> a = new AtomicReference<>(new TestObject(3));
        return a.compareAndSet(OBJ1, new TestObject(4));
    }

    @Test
    public void testvcvNoMatchCAS() {
        testAtomicVirtualization("vcvNoMatchCAS", JavaConstant.INT_0);
    }

    public static Object vcvNoMatchCAE() {
        AtomicReference<TestObject> a = new AtomicReference<>(new TestObject(3));
        return a.compareAndExchange(OBJ1, new TestObject(4));
    }

    @Test
    public void testvcvNoMatchCAE() {
        testAtomicVirtualization("vcvNoMatchCAE", null, 1);
    }

    public static boolean vccNoMatchCAS() {
        AtomicReference<TestObject> a = new AtomicReference<>(new TestObject(3));
        return a.compareAndSet(OBJ1, OBJ2);
    }

    @Test
    public void testvccNoMatchCAS() {
        testAtomicVirtualization("vccNoMatchCAS", JavaConstant.INT_0);
    }

    public static Object vccNoMatchCAE() {
        AtomicReference<TestObject> a = new AtomicReference<>(new TestObject(3));
        return a.compareAndExchange(OBJ1, OBJ2);
    }

    @Test
    public void testvccNoMatchCAE() {
        testAtomicVirtualization("vccNoMatchCAE", null, 1);
    }

    public static boolean uvvCAS() {
        AtomicReference<TestObject> a = new AtomicReference<>(obj6);
        return a.compareAndSet(new TestObject(3), new TestObject(4));
    }

    @Test
    public void testuvvCAS() {
        testAtomicVirtualization("uvvCAS", JavaConstant.INT_0);
    }

    public static Object uvvCAE() {
        AtomicReference<TestObject> a = new AtomicReference<>(obj6);
        return a.compareAndExchange(new TestObject(3), new TestObject(4));
    }

    @Test
    public void testuvvCAE() {
        testAtomicVirtualization("uvvCAE", null);
    }

    public static boolean uuvCAS() {
        AtomicReference<TestObject> a = new AtomicReference<>(obj6);
        return a.compareAndSet(obj7, new TestObject(3));
    }

    @Test
    public void testuuvCAS() {
        testAtomicVirtualization("uuvCAS", null, 2);
    }

    public static Object uuvCAE() {
        AtomicReference<TestObject> a = new AtomicReference<>(obj6);
        return a.compareAndExchange(obj7, new TestObject(3));
    }

    @Test
    public void testuuvCAE() {
        testAtomicVirtualization("uuvCAE", null, 2);
    }

    public static boolean uuuCAS() {
        AtomicReference<TestObject> a = new AtomicReference<>(obj6);
        return a.compareAndSet(obj7, obj8);
    }

    @Test
    public void testuuuCAS() {
        testAtomicVirtualization("uuuCAS", null);
    }

    public static Object uuuCAE() {
        AtomicReference<TestObject> a = new AtomicReference<>(obj6);
        return a.compareAndExchange(obj7, obj8);
    }

    @Test
    public void testuuuCAE() {
        testAtomicVirtualization("uuuCAE", null);
    }

    public static boolean vuvCAS() {
        AtomicReference<TestObject> a = new AtomicReference<>(new TestObject(3));
        return a.compareAndSet(obj6, new TestObject(4));
    }

    @Test
    public void testvuvCAS() {
        testAtomicVirtualization("vuvCAS", JavaConstant.INT_0);
    }

    public static Object vuvCAE() {
        AtomicReference<TestObject> a = new AtomicReference<>(new TestObject(3));
        return a.compareAndExchange(obj6, new TestObject(4));
    }

    @Test
    public void testvuvCAE() {
        testAtomicVirtualization("vuvCAE", null, 1);
    }

    public static boolean vuuCAS() {
        AtomicReference<TestObject> a = new AtomicReference<>(new TestObject(3));
        return a.compareAndSet(obj6, obj7);
    }

    @Test
    public void testvuuCAS() {
        testAtomicVirtualization("vuuCAS", JavaConstant.INT_0);
    }

    public static Object vuuCAE() {
        AtomicReference<TestObject> a = new AtomicReference<>(new TestObject(3));
        return a.compareAndExchange(obj6, obj7);
    }

    @Test
    public void testvuuCAE() {
        testAtomicVirtualization("vuuCAE", null, 1);
    }

    private void testAtomicVirtualization(String snippet, JavaConstant expectedValue) {
        testAtomicVirtualization(snippet, expectedValue, 0);
    }

    protected void testAtomicVirtualization(String snippet, JavaConstant expectedValue, int expectedAllocations) {
        testEscapeAnalysis(snippet, expectedValue, false, expectedAllocations);
        test(snippet);
    }
}
