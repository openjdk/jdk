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
 * bug 8281322
 * @summary check counted loop is properly constructed with/without safepoint
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestCountedLoopSafepoint
 */

public class TestCountedLoopSafepoint {
    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:LoopMaxUnroll=1", "-XX:-UseCountedLoopSafepoints");
        TestFramework.runWithFlags("-XX:LoopMaxUnroll=1", "-XX:+UseCountedLoopSafepoints", "-XX:LoopStripMiningIter=1");
        TestFramework.runWithFlags("-XX:LoopMaxUnroll=1", "-XX:+UseCountedLoopSafepoints", "-XX:LoopStripMiningIter=1000");
    }

    @Test
    @IR(counts = { IRNode.COUNTEDLOOP, "1" })
    @IR(applyIf = { "LoopStripMiningIter", "0" }, failOn = { IRNode.SAFEPOINT, IRNode.OUTERSTRIPMINEDLOOP })
    @IR(applyIf = { "LoopStripMiningIter", "1" }, counts = { IRNode.SAFEPOINT, "1" }, failOn = { IRNode.OUTERSTRIPMINEDLOOP })
    @IR(applyIf = { "LoopStripMiningIter", "> 1" }, counts = { IRNode.SAFEPOINT, "1", IRNode.OUTERSTRIPMINEDLOOP, "1" })
    public static float test(int start, int stop) {
        float v = 1;
        for (int i = start; i < stop; i++) {
            v *= 2;
        }
        return v;
    }

    @Run(test = "test")
    private void testRunner() {
        test(0, 100);
    }

}
