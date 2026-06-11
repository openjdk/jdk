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

package compiler.inlining;

import java.lang.reflect.Field;
import java.util.Objects;
import jdk.internal.misc.Unsafe;
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8374783
 * @summary TBD
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:CompileOnly=${test.main.class}::test*
                     -XX:CompileCommand=dontinline,${test.main.class}::id
                     -XX:CompileCommand=delayinline,${test.main.class}::offset*
                     -XX:CompileCommand=delayinline,${test.main.class}::store
                     ${test.main.class}
 */

class A {
    int f;
}

public class TestLateInliningWithSliceNarrowing {

    private static Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long F_OFFSET;

    static {
        try {
            Field fField = A.class.getDeclaredField("f");
            F_OFFSET = UNSAFE.objectFieldOffset(fField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Not inlined.
    static A id(A a) {
        return a;
    }

    // Inlined late.
    static long offset() {
        return F_OFFSET;
    }

    // Inlined late.
    static long offsetMinusFour() {
        return F_OFFSET - 4;
    }

    // Inlined late.
    static long offsetDividedByTwo() {
        return F_OFFSET / 2;
    }

    // Inlined late.
    static void store(A a) {
        a.f = 42;
    }

    static int testLoadFromLateDiscoveredOffsetThenStoreAtConstOffset(A a) {
        long o = offset();
        int val = UNSAFE.getInt(a, o);
        store(a);
        return val;
    }

    static int testLoadFromLateDiscoveredOffsetPlusFourThenStoreAtConstOffset(A a) {
        long o = offsetMinusFour();
        int val = UNSAFE.getInt(a, o + 4);
        store(a);
        return val;
    }

    static int testLoadFromLateDiscoveredOffsetTimesTwoThenStoreAtConstOffset(A a) {
        long o = offsetDividedByTwo();
        int val = UNSAFE.getInt(a, o * 2);
        store(a);
        return val;
    }

    static int testLoadFromLateDiscoveredOffsetThenStoreAtConstOffsetThenReloadFromConstOffset(A a) {
        A a2 = id(a);
        long o = offset();
        int val = UNSAFE.getInt(a, o);
        store(a);
        return a2.f + val;
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            {
                A a = new A();
                int result = testLoadFromLateDiscoveredOffsetThenStoreAtConstOffset(a);
                Asserts.assertEquals(0, result);
            }
            {
                A a = new A();
                int result = testLoadFromLateDiscoveredOffsetPlusFourThenStoreAtConstOffset(a);
                Asserts.assertEquals(0, result);
            }
            {
                A a = new A();
                int result = testLoadFromLateDiscoveredOffsetTimesTwoThenStoreAtConstOffset(a);
                Asserts.assertEquals(0, result);
            }
            {
                A a = new A();
                int result = testLoadFromLateDiscoveredOffsetThenStoreAtConstOffsetThenReloadFromConstOffset(a);
                Asserts.assertEquals(42, result);
            }
        }
    }
}
