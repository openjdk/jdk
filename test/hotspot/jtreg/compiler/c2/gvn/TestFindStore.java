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
package compiler.c2.gvn;

import compiler.lib.ir_framework.*;
import jdk.internal.misc.Unsafe;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8376220
 * @summary Tests that memory accesses can be elided when the compiler can see the value at the
 *          accessed memory location by walking the memory graph.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestFindStore {
    private static final Unsafe U = Unsafe.getUnsafe();

    static class P {
        int v;
        int u;
    }

    static final long V_OFFSET = U.objectFieldOffset(P.class, "v");
    static final long U_OFFSET = U.objectFieldOffset(P.class, "u");

    static class C1 extends P {}
    static class C2 extends P {}

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");
    }

    @Run(test = {"testLoad", "testStore", "testLoadDependent1", "testLoadDependent2", "testLoadArray",
                 "testLoadArrayOverlap", "testLoadIndependentAliasClasses", "testLoadMismatched",
                 "testLoadArrayCopy", "testLoadArrayCopyUnknownLength"})
    public void run() {
        C1 c1 = new C1();
        C2 c2 = new C2();
        int[] a1 = new int[1000];
        int[] a2 = new int[1000];

        Asserts.assertEQ(0, testLoad(c1, c2, 0, 1));
        Asserts.assertEQ(0, testStore(c2, 0).v);
        Asserts.assertEQ(0, testLoadDependent1(c1, c1, 0, 1));
        Asserts.assertEQ(1, testLoadDependent1(c2, c1, 0, 1));
        Asserts.assertEQ(1, testLoadDependent2(c1, c1, 0, 1));
        Asserts.assertEQ(0, testLoadDependent2(c2, c1, 0, 1));

        Asserts.assertEQ(0, testLoadArray(a1, a2, 0, 1));
        Asserts.assertEQ(0, testLoadArrayOverlap(a1, a2, 2, 0, 1));
        Asserts.assertEQ(0, testLoadArrayOverlap(a1, a1, 0, 0, 1));
        Asserts.assertEQ(1, testLoadArrayOverlap(a1, a1, 2, 0, 1));
        Asserts.assertEQ(0, testLoadIndependentAliasClasses(c1, 0, 1));
        Asserts.assertNE(0, testLoadMismatched(c1, 0, -1));
        Asserts.assertEQ(1, testLoadArrayCopy(a1, a2, 1));

        a1[2] = 0;
        Asserts.assertEQ(1, testLoadArrayCopyUnknownLength(a1, a2, 100, 1));
        a1[2] = 0;
        Asserts.assertEQ(0, testLoadArrayCopyUnknownLength(a1, a2, 2, 1));
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    static int testLoad(C1 c1, C2 c2, int v1, int v2) {
        // c1 and c2 are provably independent
        c1.v = v1;
        c2.v = v2;
        return c1.v;
    }

    @Test
    @IR(counts = {IRNode.STORE, "1"}, phase = CompilePhase.BEFORE_MACRO_EXPANSION)
    static C1 testStore(C2 c2, int v2) {
        C1 c1 = new C1();
        c2.v = v2;
        c1.v = 0;
        return c1;
    }

    @Test
    @IR(counts = {IRNode.LOAD, "1"})
    static int testLoadDependent1(P p, C1 c1, int v, int v1) {
        // It cannot be proved that p and c1 are independent
        c1.v = v1;
        p.v = v;
        return c1.v;
    }

    @Test
    @IR(counts = {IRNode.LOAD, "1"})
    static int testLoadDependent2(P p, C1 c1, int v, int v1) {
        // It cannot be proved that p and c1 are independent
        p.v = v;
        c1.v = v1;
        return p.v;
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    static int testLoadArray(int[] a1, int[] a2, int v1, int v2) {
        // a1[2] and a2[1] are provably independent
        a1[2] = v1;
        a2[1] = v2;
        return a1[2];
    }

    @Test
    @IR(counts = {IRNode.LOAD, "1"})
    static int testLoadArrayOverlap(int[] a1, int[] a2, int idx, int v1, int v2) {
        // Cannot prove that a1[2] and a2[idx] are independent
        a1[2] = v1;
        a2[idx] = v2;
        return a1[2];
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    static int testLoadIndependentAliasClasses(P p, int v, int u) {
        p.v = v;
        p.u = u;
        return p.v;
    }

    @Test
    @IR(counts = {IRNode.LOAD, "1"})
    static int testLoadMismatched(P p, int v1, int v2) {
        p.v = v1;
        U.putIntUnaligned(p, (V_OFFSET + U_OFFSET) / 2, v2);
        return p.v;
    }

    @Test
    @IR(failOn = IRNode.LOAD, applyIf = {"ArrayCopyLoadStoreMaxElem", "<100"})
    static int testLoadArrayCopy(int[] a1, int[] a2, int v) {
        a2[2] = v;
        // Should be large so the compiler does not just transform it into a couple of loads and stores
        System.arraycopy(a2, 0, a1, 0, 100);
        return a1[2];
    }

    @Test
    @IR(counts = {IRNode.LOAD, "1"})
    static int testLoadArrayCopyUnknownLength(int[] a1, int[] a2, int len, int v) {
        a2[2] = v;
        // Cannot determine if this overwrites a1[2]
        System.arraycopy(a2, 0, a1, 0, len);
        return a1[2];
    }
}
