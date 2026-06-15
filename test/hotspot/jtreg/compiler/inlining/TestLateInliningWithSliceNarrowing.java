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
import jdk.internal.misc.Unsafe;
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8374783
 * @summary Test that address type refinements after an incremental inlining
 *          step are propagated by IGVN before the next step. Failing to
 *          propagate such refinements could lead to slice mismatches between
 *          field-derived and IGVN-recorded address types when parsing bytecode
 *          in subsequent inlining steps.
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch
                     -XX:CompileCommand=compileonly,${test.main.class}::test*
                     -XX:CompileCommand=dontinline,${test.main.class}::notInlined*
                     -XX:CompileCommand=delayinline,${test.main.class}::late*
                     ${test.main.class}
 */

class A {
    int f;
}

public class TestLateInliningWithSliceNarrowing {

    private static Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long F_OFFSET;
    private static final long INT_ARRAY_OFFSET;

    static {
        try {
            Field fField = A.class.getDeclaredField("f");
            F_OFFSET = UNSAFE.objectFieldOffset(fField);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        INT_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(int[].class);
    }

    static A notInlinedId(A a) {
        return a;
    }

    static long lateOffset() {
        return F_OFFSET;
    }

    static long lateOffsetMinusFour() {
        return F_OFFSET - 4;
    }

    static long lateOffsetDividedByTwo() {
        return F_OFFSET / 2;
    }

    static long lateArrayOffset() {
        return INT_ARRAY_OFFSET;
    }

    static void lateStore(A a) {
        a.f = 42;
    }

    static void lateArrayStore(int[] a) {
        a[0] = 42;
    }

    static int lateLoad(A a) {
        return a.f;
    }

    static Object lateBase(A a) {
        return a;
    }

    // Test that when lateStore() is inlined, the IGVN-recorded type of the
    // accessed memory address (captured by an AddP) has been updated to reflect
    // the compiler-known offset discovered by inlining lateOffset(). Failure to
    // do so leads to a slice mismatch when parsing the inlined store.
    static int testLoadFromLateDiscoveredOffsetThenStoreAtConstOffset(A a) {
        long o = lateOffset();
        int val = UNSAFE.getInt(a, o);
        lateStore(a);
        return val;
    }

    // Test that when lateLoad() is inlined, the IGVN-recorded type of the
    // accessed memory address (captured by an AddP) has been updated to reflect
    // the compiler-known offset discovered by inlining lateOffset(). Failure to
    // do so leads to a slice mismatch when parsing the inlined load.
    static int testLoadFromLateDiscoveredOffsetThenLoadFromConstOffset(A a) {
        long o = lateOffset();
        int val = UNSAFE.getInt(a, o);
        lateLoad(a);
        return val;
    }

    // Test a variation of the above where lateOffsetMinusFour() is not used
    // directly by an AddP node. This test does not require updating the
    // IGVN-recorded type of the accessed memory address for correctness,
    // because lateStore() does not reuse the corresponding AddP node.
    static int testLoadFromLateDiscoveredOffsetPlusFourThenStoreAtConstOffset(A a) {
        long o = lateOffsetMinusFour();
        int val = UNSAFE.getInt(a, o + 4);
        lateStore(a);
        return val;
    }

    // Test a variation of the above using a different arithmetic operation,
    // with the same expectations.
    static int testLoadFromLateDiscoveredOffsetTimesTwoThenStoreAtConstOffset(A a) {
        long o = lateOffsetDividedByTwo();
        int val = UNSAFE.getInt(a, o * 2);
        lateStore(a);
        return val;
    }

    // Test a variation of the first test where failing to update the
    // IGVN-recorded type of the accessed memory address would result in a slice
    // mismatch that will lead to an incorrect memory graph (the memory input of
    // the last load would bypass the memory output of the store).
    static int testLoadFromLateDiscoveredOffsetThenStoreAtConstOffsetThenReloadFromConstOffset(A a) {
        A a2 = notInlinedId(a);
        long o = lateOffset();
        int val = UNSAFE.getInt(a, o);
        lateStore(a);
        return a2.f + val;
    }

    // Test a variation of the first test where the offset is compiler-known
    // from the beginning, but the unsafe base address is only discovered by
    // inlining lateBase(). This variation does not require a cleanup between
    // the late inlining of lateBase() and lateLoad() for correctness: a slice
    // mismatch cannot occur because the memory access within lateLoad() does
    // not reuse the same address node (AddP) as the unsafe load. The unsafe
    // load address node is not reusable by the lateLoad() access because it is
    // obscured by casts by the time lateLoad() is late inlined. Making the
    // address node reusable by both loads would require a cleanup round, which
    // would prevent the mismatch from happening in the first place.
    static int testLoadFromLateDiscoveredBaseThenLoadFromKnownBase(A a) {
        Object obj = lateBase(a);
        int val = UNSAFE.getInt(obj, F_OFFSET);
        lateLoad(a);
        return val;
    }

    // Test a variation of the first test using an array instead of a class
    // instance. No slice mismatch occurs because the address types for both
    // memory accesses lead to the same slice, regardless of whether the offset
    // is compiler-known.
    static int testArrayLoadFromLateDiscoveredOffsetThenStoreAtConstOffset(int[] a) {
        long o = lateArrayOffset();
        int val = UNSAFE.getInt(a, o);
        lateArrayStore(a);
        return val;
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
                int result = testLoadFromLateDiscoveredOffsetThenLoadFromConstOffset(a);
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
            {
                A a = new A();
                int result = testLoadFromLateDiscoveredBaseThenLoadFromKnownBase(a);
                Asserts.assertEquals(0, result);
            }
            {
                int[] a = new int[1];
                int result = testArrayLoadFromLateDiscoveredOffsetThenStoreAtConstOffset(a);
                Asserts.assertEquals(0, result);
            }
        }
    }
}
