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

package compiler.c2.irTests.scalarReplacement;

import java.util.Random;
import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8281429 8393490
 * @summary Tests that C2 can correctly process non escaping object allocation with exception handler.
 * @library /test/lib /
 * @requires vm.flagless & vm.bits == 64 & vm.compiler2.enabled & vm.opt.final.EliminateAllocations
 * @run driver ${test.main.class}
 */

public class ScalarReplacementWithHandlerTests {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();

        Scenario scenario0 = new Scenario(0, "-XX:+UnlockDiagnosticVMOptions",
                                             "-XX:-ReduceAllocationMerges",
                                             "-XX:CompileCommand=inline,*::charAt*",
                                             "-XX:CompileCommand=inline,*Point::*",
                                             "-XX:CompileCommand=exclude,*::deoptPoint*");

        Scenario scenario1 = new Scenario(1, "-XX:+UnlockDiagnosticVMOptions",
                                             "-XX:+ReduceAllocationMerges",
                                             "-XX:CompileCommand=inline,*::charAt*",
                                             "-XX:CompileCommand=inline,*Point::*",
                                             "-XX:CompileCommand=exclude,*::deoptPoint*");

        framework.addScenarios(scenario0, scenario1).start();
    }

    @Run(test = {"testAllocationWithOOMEHandler_C2",
                 "testAllocationWithVMEHandler_C2",
                 "testArrayWithThrowable_C2",
                 "testAllocationWithoutHandler_C2",
                 "testArrayWithoutHandler_C2",
                 "testAllocationWithoutDeopt_C2",
                 "testArrayWithoutDeopt_C2"
                })
    public void runner(RunInfo info) {

        Random random = info.getRandom();
        int x = random.nextInt();
        int y = random.nextInt();

        Asserts.assertEQ(testAllocationWithOOMEHandler_Interp(x, y), testAllocationWithOOMEHandler_C2(x, y));
        Asserts.assertEQ(testAllocationWithVMEHandler_Interp(x, y),  testAllocationWithVMEHandler_C2(x, y));
        Asserts.assertEQ(testArrayWithThrowable_Interp(x, y),        testArrayWithThrowable_C2(x, y));
        Asserts.assertEQ(testAllocationWithoutHandler_Interp(x, y),  testAllocationWithoutHandler_C2(x, y));
        Asserts.assertEQ(testArrayWithoutHandler_Interp(x, y),       testArrayWithoutHandler_C2(x, y));
        Asserts.assertEQ(testAllocationWithoutDeopt_Interp(x, y),    testAllocationWithoutDeopt_C2(x, y));
        Asserts.assertEQ(testArrayWithoutDeopt_Interp(x, y),         testArrayWithoutDeopt_C2(x, y));

    }

    // -------------------------------------------------------------------------

    @ForceInline
    int testAllocationWithOOMEHandler(int x, int y) {
        Point p;
        try {
            p = new Point(x, y);
        } catch (OutOfMemoryError ignore) {
            return -1;
        }
        deoptPoint();
        return p.x + p.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "1" })
    int testAllocationWithOOMEHandler_C2(int x, int y) {
        return testAllocationWithOOMEHandler(x, y);
    }
    @DontCompile
    int testAllocationWithOOMEHandler_Interp(int x, int y) {
        return testAllocationWithOOMEHandler(x, y);
    }

    // -------------------------------------------------------------------------

    @ForceInline
    Point testAllocationWithVMEHandler_callee(int x, int y) {
        return new Point(x, y);
    }

    @ForceInline
    int testAllocationWithVMEHandler(int x, int y) {
        Point p;
        try {
            p = testAllocationWithVMEHandler_callee(x, y);
        } catch (VirtualMachineError ignore) {
            return -1;
        }
        deoptPoint();
        return p.x + p.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "1" })
    int testAllocationWithVMEHandler_C2(int x, int y) {
        return testAllocationWithVMEHandler(x, y);
    }
    @DontCompile
    int testAllocationWithVMEHandler_Interp(int x, int y) {
        return testAllocationWithVMEHandler(x, y);
    }

    // -------------------------------------------------------------------------

    @ForceInline
    int testArrayWithThrowable(int x, int y) {
        int[] p;
        try {
            p = new int[] {x, y};
        } catch (Throwable ignore) {
            return -1;
        }
        deoptPoint();
        return p[0] + p[1];
    }

    @Test
    @IR(counts = { IRNode.ALLOC_ARRAY, "1" })
    int testArrayWithThrowable_C2(int x, int y) {
        return testArrayWithThrowable(x, y);
    }
    @DontCompile
    int testArrayWithThrowable_Interp(int x, int y) {
        return testArrayWithThrowable(x, y);
    }

    // -------------------------------------------------------------------------

    @ForceInline
    int testAllocationWithoutHandler(int x, int y) {
        Point p = new Point(x, y);
        deoptPoint();
        return p.x + p.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "0" })
    int testAllocationWithoutHandler_C2(int x, int y) {
        return testAllocationWithoutHandler(x, y);
    }
    @DontCompile
    int testAllocationWithoutHandler_Interp(int x, int y) {
        return testAllocationWithoutHandler(x, y);
    }

    // -------------------------------------------------------------------------

    @ForceInline
    int testArrayWithoutHandler(int x, int y) {
        int[] p = new int[] {x, y};
        deoptPoint();
        return p[0] + p[1];
    }

    @Test
    @IR(counts = { IRNode.ALLOC_ARRAY, "0" })
    int testArrayWithoutHandler_C2(int x, int y) {
        return testArrayWithoutHandler(x, y);
    }
    @DontCompile
    int testArrayWithoutHandler_Interp(int x, int y) {
        return testArrayWithoutHandler(x, y);
    }

    // -------------------------------------------------------------------------

    @ForceInline
    int testAllocationWithoutDeopt(int x, int y) {
        Point p;
        try {
            p = new Point(x, y);
        } catch (Error ignore) {
            return -1;
        }
        return p.x + p.y;
    }

    @Test
    @IR(counts = { IRNode.ALLOC, "0" })
    int testAllocationWithoutDeopt_C2(int x, int y) {
        return testAllocationWithoutDeopt(x, y);
    }
    @DontCompile
    int testAllocationWithoutDeopt_Interp(int x, int y) {
        return testAllocationWithoutDeopt(x, y);
    }

    // -------------------------------------------------------------------------

    @ForceInline
    int testArrayWithoutDeopt(int x, int y) {
        int[] p;
        try {
            p = new int[] {x, y};
        } catch (OutOfMemoryError ignore) {
            return -1;
        }
        return p[0] + p[1];
    }

    @Test
    @IR(counts = { IRNode.ALLOC_ARRAY, "0" })
    int testArrayWithoutDeopt_C2(int x, int y) {
        return testArrayWithoutDeopt(x, y);
    }
    @DontCompile
    int testArrayWithoutDeopt_Interp(int x, int y) {
        return testArrayWithoutDeopt(x, y);
    }

    // ------------------ Utility for Testing ------------------- //

    @DontCompile
    static void deoptPoint() {
    }

    static class Point {
        int x, y;
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

}
