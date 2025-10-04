/*
 * Copyright (c) 2025 Alibaba Group Holding Limited. All Rights Reserved.
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
 * @bug 8347499
 * @summary Tests that redundant safepoints can be eliminated in loops.
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run main compiler.c2.irTests.TestLoopSafepoint
 */
public class TestLoopSafepoint {
    public static void main(String[] args) {
        TestFramework.run();
    }

    static int loopCount = 100000;
    static int anotherInt = 1;

    @DontInline
    private void empty() {}

    @DontInline
    private int constInt() {
        return 100000;
    }

    @Test
    @IR(failOn = IRNode.SAFEPOINT)
    public void loopConst() {
        for (int i = 0; i < 100000; i++) {
            empty();
        }
    }

    @Test
    @IR(failOn = IRNode.SAFEPOINT)
    public void loopVar() {
        for (int i = 0; i < loopCount; i++) {
            empty();
        }
    }

    @Test
    @IR(counts = {IRNode.SAFEPOINT, "1"})
    public int loopVarWithoutCall() {
        int sum = 0;
        for (int i = 0; i < loopCount; i++) {
            sum += anotherInt;
        }
        return sum;
    }

    @Test
    @IR(failOn = IRNode.SAFEPOINT)
    public void loopFunc() {
        for (int i = 0; i < constInt(); i++) {
            empty();
        }
    }
}
