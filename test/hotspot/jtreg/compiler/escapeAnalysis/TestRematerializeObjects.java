/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=yEA
 * @bug 8370405
 * @summary Test elimination of array allocation, and the rematerialization.
 * @library /test/lib /
 * @run driver compiler.escapeAnalysis.TestRematerializeObjects yEA
 */

/*
 * @test id=nEA
 * @library /test/lib /
 * @run driver compiler.escapeAnalysis.TestRematerializeObjects nEA
 */

package compiler.escapeAnalysis;

import jdk.test.lib.Utils;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;

public class TestRematerializeObjects {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestRematerializeObjects.class);
        switch (args[0]) {
            case "yEA" -> { framework.addFlags("-XX:+EliminateAllocations"); }
            case "nEA" -> { framework.addFlags("-XX:-EliminateAllocations"); }
            default -> { throw new RuntimeException("Test argument not recognized: " + args[0]); }
        };
        framework.start();
    }

    @DontInline
    static void dontinline() {}

    @Run(test = "test1", mode = RunMode.STANDALONE)
    public void runTest1() {
        // Capture interpreter result.
        int gold = test1(false);
        // Repeat until we get compilation.
        for (int i = 0; i < 10_000; i++) {
            test1(false);
        }
        // Capture compiled results.
        int res0 = test1(false);
        int res1 = test1(true);
        if (res0 != gold || res1 != gold) {
            throw new RuntimeException("Unexpected result: " + Integer.toHexString(res0) + " and " +
                                       Integer.toHexString(res1) + ", should be: " + Integer.toHexString(gold));
        }
    }

    @Test
    @IR(counts = {IRNode.ALLOC_ARRAY, "1",
                  IRNode.UNSTABLE_IF_TRAP, "1",
                  IRNode.STORE_L_OF_CLASS, "int\\[int:4\\]", "1",
                  IRNode.SAFEPOINT_SCALAROBJECT_OF, "fields@\\[0..3\\]", "0"},
        applyIf = {"EliminateAllocations", "false"})
    @IR(counts = {IRNode.ALLOC_ARRAY, "0",
                  IRNode.UNSTABLE_IF_TRAP, "1",
                  IRNode.STORE_L_OF_CLASS, "int\\[int:4\\]", "0",
                  IRNode.SAFEPOINT_SCALAROBJECT_OF, "fields@\\[0..3\\]", "2"},
        applyIf = {"EliminateAllocations", "true"})
    static int test1(boolean flag) {
        int[] arr = new int[4];
        arr[0] = 0x0001_0000; // these slip into Initialize
        arr[1] = 0x0010_0000;
        arr[2] = 0x0000_0100;
        arr[3] = 0x0100_0000;
        dontinline();
        arr[0] = 0x0000_0001; // MergeStores -> StoreL
        arr[1] = 0x0000_0010;
        if (flag) {
            // unstable if -> deopt -> rematerialized array (if was eliminated)
            System.out.println("unstable if: " + arr.length);
        }
        arr[3] = 0x0000_1000;
        return 1 * arr[0] + 2 * arr[1] + 3 * arr[2] + 4 * arr[3];
    }

    @Run(test = "test2", mode = RunMode.STANDALONE)
    public void runTest2() {
        // Capture interpreter result.
        int gold = test2(false);
        // Repeat until we get compilation.
        for (int i = 0; i < 10_000; i++) {
            test2(false);
        }
        // Capture compiled results.
        int res0 = test2(false);
        int res1 = test2(true);
        if (res0 != gold || res1 != gold) {
            throw new RuntimeException("Unexpected result: " + Integer.toHexString(res0) + " and " +
                                       Integer.toHexString(res1) + ", should be: " + Integer.toHexString(gold));
        }
    }

    @Test
    @IR(counts = {IRNode.ALLOC_ARRAY, "1",
                  IRNode.UNSTABLE_IF_TRAP, "1",
                  IRNode.STORE_I_OF_CLASS, "short\\[int:4\\]", "1",
                  IRNode.SAFEPOINT_SCALAROBJECT_OF, "fields@\\[0..3\\]", "0"},
        applyIf = {"EliminateAllocations", "false"})
    @IR(counts = {IRNode.ALLOC_ARRAY, "0",
                  IRNode.UNSTABLE_IF_TRAP, "1",
                  IRNode.STORE_I_OF_CLASS, "short\\[int:4\\]", "0",
                  IRNode.SAFEPOINT_SCALAROBJECT_OF, "fields@\\[0..3\\]", "2"},
        applyIf = {"EliminateAllocations", "true"})
    static int test2(boolean flag) {
        short[] arr = new short[4];
        arr[0] = 1;
        arr[1] = 2;
        arr[2] = 4;
        arr[3] = 8;
        dontinline();
        // Seems we detect that this is a short value passed into the short field.
        arr[0] = 16;
        arr[1] = 32;
        if (flag) {
            // unstable if -> deopt -> rematerialized array (if was eliminated)
            System.out.println("unstable if: " + arr.length);
        }
        arr[3] = 64;
        return 0x1 * arr[0] + 0x100 * arr[1] + 0x1_0000 * arr[2] + 0x100_0000 * arr[3];
    }

    @Run(test = "test3", mode = RunMode.STANDALONE)
    public void runTest3() {
        // Capture interpreter result.
        int gold = test3(false, 42);
        // Repeat until we get compilation.
        for (int i = 0; i < 10_000; i++) {
            test3(false, 42);
        }
        // Capture compiled results.
        int res0 = test3(false, 42);
        int res1 = test3(true, 42);
        if (res0 != gold || res1 != gold) {
            throw new RuntimeException("Unexpected result: " + Integer.toHexString(res0) + " and " +
                                       Integer.toHexString(res1) + ", should be: " + Integer.toHexString(gold));
        }
    }

    @Test
    @IR(counts = {IRNode.ALLOC_ARRAY, "1",
                  IRNode.UNSTABLE_IF_TRAP, "1",
                  IRNode.SAFEPOINT_SCALAROBJECT_OF, "fields@\\[0..3\\]", "0"},
        applyIf = {"EliminateAllocations", "false"})
    @IR(counts = {IRNode.ALLOC_ARRAY, "0",
                  IRNode.UNSTABLE_IF_TRAP, "1",
                  IRNode.SAFEPOINT_SCALAROBJECT_OF, "fields@\\[0..3\\]", "2"},
        applyIf = {"EliminateAllocations", "true"})
    static int test3(boolean flag, int x) {
        short[] arr = new short[4];
        arr[0] = 1;
        arr[1] = 2;
        arr[2] = 4;
        arr[3] = 8;
        dontinline();
        // Here, we don't get ConI, but instead AddI, which means we are
        // serializing an int value, for a short slot.
        arr[0] = (short)(x + 1);
        arr[1] = (short)(x + 2);
        if (flag) {
            // unstable if -> deopt -> rematerialized array (if was eliminated)
            System.out.println("unstable if: " + arr.length);
        }
        arr[3] = 64;
        return 0x1 * arr[0] + 0x100 * arr[1] + 0x1_0000 * arr[2] + 0x100_0000 * arr[3];
    }
}
