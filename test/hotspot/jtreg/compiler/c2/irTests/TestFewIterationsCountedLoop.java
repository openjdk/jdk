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
 * @bug 8262721
 * @summary Add Tests to verify single iteration loops are properly optimized
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestFewIterationsCountedLoop
 */

public class TestFewIterationsCountedLoop {

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:LoopUnrollLimit=0");
        TestFramework.run();
    }

    static volatile int barrier;
    static final Object object = new Object();

    @Test
    @IR(failOn = { IRNode.COUNTEDLOOP, IRNode.LOOP })
    public static void singleIterationFor() {
        for (int i = 0; i < 1; i++) {
            barrier = 0x42; // something that can't be optimized out
        }
    }

    @Test
    @IR(failOn = { IRNode.COUNTEDLOOP, IRNode.LOOP })
    public static void singleIterationWhile() {
        int i = 0;
        while (i < 1) {
            barrier = 0x42;
            i++;
        }
    }

    @Test
    @IR(failOn = { IRNode.COUNTEDLOOP, IRNode.LOOP })
    @Warmup(1) // So C2 can't rely on profile data
    public static void singleIterationDoWhile() {
        int i = 0;
        do {
            synchronized(object) {} // so loop head is not cloned by ciTypeFlow
            barrier = 0x42;
            i++;
        } while (i < 1);
    }

    @Test
    @IR(applyIf = { "LoopUnrollLimit", "0" }, counts = { IRNode.COUNTEDLOOP, "1" })
    @IR(applyIf = { "LoopUnrollLimit", "> 0" }, failOn = { IRNode.COUNTEDLOOP, IRNode.LOOP })
    public static void twoIterationsFor() {
        for (int i = 0; i < 2; i++) {
            barrier = 0x42; // something that can't be optimized out
        }
    }

    @Test
    @IR(applyIf = { "LoopUnrollLimit", "0" }, counts = { IRNode.COUNTEDLOOP, "1" })
    @IR(applyIf = { "LoopUnrollLimit", "> 0" }, failOn = { IRNode.COUNTEDLOOP, IRNode.LOOP })
    public static void twoIterationsWhile() {
        int i = 0;
        while (i < 2) {
            barrier = 0x42;
            i++;
        }
    }

    @Test
    @IR(applyIf = { "LoopUnrollLimit", "0" }, counts = { IRNode.COUNTEDLOOP, "1" })
    @IR(applyIf = { "LoopUnrollLimit", "> 0" }, failOn = { IRNode.COUNTEDLOOP, IRNode.LOOP })
    public static void twoIterationsDoWhile() {
        int i = 0;
        do {
            synchronized(object) {} // so loop head is not cloned by ciTypeFlow
            barrier = 0x42;
            i++;
        } while (i < 2);
    }

    @Test
    @IR(applyIf = { "LoopUnrollLimit", "0" }, counts = { IRNode.COUNTEDLOOP, "1" })
    @IR(applyIf = { "LoopUnrollLimit", "> 0" }, failOn = { IRNode.COUNTEDLOOP, IRNode.LOOP })
    public static void threadIterationsFor() {
        for (int i = 0; i < 2; i++) {
            barrier = 0x42; // something that can't be optimized out
        }
    }

    @Test
    @IR(applyIf = { "LoopUnrollLimit", "0" }, counts = { IRNode.COUNTEDLOOP, "1" })
    @IR(applyIf = { "LoopUnrollLimit", "> 0" }, failOn = { IRNode.COUNTEDLOOP, IRNode.LOOP })
    public static void threeIterationsWhile() {
        int i = 0;
        while (i < 2) {
            barrier = 0x42;
            i++;
        }
    }

    @Test
    @IR(applyIf = { "LoopUnrollLimit", "0" }, counts = { IRNode.COUNTEDLOOP, "1" })
    @IR(applyIf = { "LoopUnrollLimit", "> 0" }, failOn = { IRNode.COUNTEDLOOP, IRNode.LOOP })
    public static void threeIterationsDoWhile() {
        int i = 0;
        do {
            synchronized(object) {} // so loop head is not cloned by ciTypeFlow
            barrier = 0x42;
            i++;
        } while (i < 2);
    }
}
