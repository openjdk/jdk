/*
 * Copyright (c) 2022, Red Hat, Inc. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8282045
 * @summary When loop strip mining fails, safepoints are removed from loop anyway
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.irTests.TestStripMiningDropsSafepoint
 */

public class TestStripMiningDropsSafepoint {
    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+UseCountedLoopSafepoints", "-XX:LoopStripMiningIter=1000", "-XX:LoopMaxUnroll=1", "-XX:-RangeCheckElimination");
        TestFramework.runWithFlags("-XX:+UseCountedLoopSafepoints", "-XX:LoopStripMiningIter=1000", "-XX:LoopMaxUnroll=1", "-XX:-RangeCheckElimination", "-XX:-PartialPeelLoop");
    }

    @Test
    @IR(applyIf = { "PartialPeelLoop", "true" }, counts = { IRNode.COUNTEDLOOP, "1",  IRNode.OUTERSTRIPMINEDLOOP, "1", IRNode.SAFEPOINT, "1" })
    private static void test1(int[] dst, int[] src) {
        // Partial peel is applied. No side effect between exit and
        // safepoint.
        for (int i = 0; ; ) {
            // prevent ciTypeFlow from cloning head
            synchronized (new Object()) {}
            i++;
            if (i >= src.length) {
                break;
            }
            dst[i] = src[i];
            if (i / 2 >= 2000) {
                break;
            }
        }
    }

    @Run(test = "test1")
    private static void test1_runner() {
        int[] array1 = new int[1000];
        int[] array2 = new int[10000];
        test1(array1, array1);
        test1(array2, array2);
    }

    @Test
    @IR(applyIf = { "PartialPeelLoop", "true" }, counts = { IRNode.COUNTEDLOOP, "1", IRNode.SAFEPOINT, "1" })
    @IR(applyIf = { "PartialPeelLoop", "true" }, failOn = { IRNode.OUTERSTRIPMINEDLOOP })
    private static void test2(int[] dst, int[] src) {
        // Partial peel is applied. Some side effect between exit and
        // safepoint.
        int v = src[0];
        for (int i = 0; ; ) {
            synchronized (new Object()) {}
            dst[i] = v;
            i++;
            if (i >= src.length) {
                break;
            }
            v = src[i];
            if (i / 2 >= 2000) {
                break;
            }
        }
    }

    @Run(test = "test2")
    private static void test2_runner() {
        int[] array1 = new int[1000];
        int[] array2 = new int[10000];
        test2(array1, array1);
        test2(array2, array2);
    }

    @Test
    @IR(applyIf = { "PartialPeelLoop", "false" }, counts = { IRNode.COUNTEDLOOP, "1",  IRNode.OUTERSTRIPMINEDLOOP, "1", IRNode.SAFEPOINT, "1" })
    private static void test3(int[] dst, int[] src) {
        int v = src[0];
        for (int i = 0; ; ) {
            synchronized (new Object()) {}
            dst[i] = v;
            int inc = test3_helper(2);
            v = src[i];
            i += (inc / 2);
            if (i >= src.length) {
                break;
            }
            for (int j = 0; j < 10; j++) {
            }
            // safepoint on backedge
        }
    }

    private static int test3_helper(int stop) {
        int i = 1;
        do {
            synchronized (new Object()) {}
            i *= 2;
        } while (i < stop);
        return i;
    }

    @Run(test = "test3")
    private static void test3_runner() {
        int[] array1 = new int[1000];
        test3(array1, array1);
        test3_helper(10);
    }
}
