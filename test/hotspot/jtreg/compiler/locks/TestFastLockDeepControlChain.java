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

/*
 * @test
 * @summary C2 must use integer flags when branching on a fast lock with deep control chain
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
package compiler.locks;

import compiler.lib.ir_framework.*;

import java.lang.ref.Reference;

public class TestFastLockDeepControlChain {
    static final int EXPECTED = 123;

    static class MyClass1 {
        int value = EXPECTED;
    }

    static class MyClass2 {
        final MyClass1 obj = new MyClass1();

        synchronized MyClass1 getSynchronized() {
            return obj;
        }
    }

    static class Holder {
        final Object p0 = new Object();
        final Object p1 = new Object();
        final Object p2 = new Object();
        final Object p3 = new Object();
        final Object p4 = new Object();
        final Object p5 = new Object();
        final Object p6 = new Object();
        final Object p7 = new Object();
        final Object p8 = new Object();
        final Object p9 = new Object();
    }

    @Test
    // Signed and unsigned EQ/NE branches behave identically on AArch64, so the
    // runtime checks alone cannot detect a branchConU consuming fast-lock flags.
    // The lock and unlock must each use branchCon; the null checks use CBZ/CBNZ.
    @IR(phase = CompilePhase.MATCHING, applyIfPlatform = {"aarch64", "true"},
        counts = {IRNode.CMP_FAST_LOCK,      "1",
                  IRNode.CMP_FAST_UNLOCK,    "1",
                  IRNode.AARCH64_BRANCH_CON, "2"},
        failOn = {IRNode.AARCH64_BRANCH_CON_U})
    // On x86, fast-lock flags must not be consumed by floating-point branches,
    // including the unordered EQ/NE fixup and APX variants. Check before branch
    // shortening, while all jumps still have their original instruction names.
    @IR(phase = CompilePhase.MATCHING, applyIfPlatform = {"x64", "true"},
        counts = {IRNode.CMP_FAST_LOCK,   "1",
                  IRNode.CMP_FAST_UNLOCK, "1"},
        failOn = {IRNode.X86_JMP_CON_FP})
    static int testFastLockCmp(MyClass2 obj, Holder holder) {
        if (obj == null || holder == null) {
            throw new AssertionError();
        }

        // Load each referent before the allocation safepoint.
        int missing = 0;
        if (holder.p0 == null) { missing++; }
        if (holder.p1 == null) { missing++; }
        if (holder.p2 == null) { missing++; }
        if (holder.p3 == null) { missing++; }
        if (holder.p4 == null) { missing++; }
        if (holder.p5 == null) { missing++; }
        if (holder.p6 == null) { missing++; }
        if (holder.p7 == null) { missing++; }
        if (holder.p8 == null) { missing++; }
        if (holder.p9 == null) { missing++; }

        // With allocation elimination and TLABs disabled, this unused allocation
        // is a runtime call. The explicit fences keep the ten referents live at
        // that safepoint and are emitted immediately before the fast lock.
        new Object();

        Reference.reachabilityFence(holder.p0);
        Reference.reachabilityFence(holder.p1);
        Reference.reachabilityFence(holder.p2);
        Reference.reachabilityFence(holder.p3);
        Reference.reachabilityFence(holder.p4);
        Reference.reachabilityFence(holder.p5);
        Reference.reachabilityFence(holder.p6);
        Reference.reachabilityFence(holder.p7);
        Reference.reachabilityFence(holder.p8);
        Reference.reachabilityFence(holder.p9);

        return obj.getSynchronized().value + missing;
    }

    @Run(test = "testFastLockCmp", mode = RunMode.STANDALONE)
    public static void runTest() {
        MyClass2 obj = new MyClass2();
        Holder holder = new Holder();

        for (int i = 0; i < 20_000; i++) {
            int result = testFastLockCmp(obj, holder);
            if (result != EXPECTED) {
                throw new AssertionError("Unexpected result: " + result + " != " + EXPECTED);
            }
            if (Thread.holdsLock(obj)) {
                throw new AssertionError("Mismatch: lock was acquired twice, but released only once");
            }
        }
        System.out.println("TEST PASSED");
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("-Xbatch", "-XX:-UseTLAB", "-XX:-EliminateAllocations");
    }
}
