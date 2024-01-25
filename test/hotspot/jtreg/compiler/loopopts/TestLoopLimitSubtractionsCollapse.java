/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8303466
 * @summary Verify that AddL->MaxL->AddL->MaxL chains of unroll limit adjustments collapse.
 *          If it did not collapse, we would have about 10 MaxL/MinL. With the collapse, it
 *          is now one or two.
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.loopopts.TestLoopLimitSubtractionsCollapse
 */

package compiler.loopopts;
import compiler.lib.ir_framework.*;

public class TestLoopLimitSubtractionsCollapse {
    static int START = 0;
    static int FINISH = 512;
    static int RANGE = 512;

    static byte[] data1 = new byte[RANGE];
    static byte[] data2 = new byte[RANGE];

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Warmup(0)
    @IR(counts = {IRNode.MAX_L, "> 0", IRNode.MAX_L, "<= 2"},
        phase = CompilePhase.PHASEIDEALLOOP_ITERATIONS)
    public static void test1() {
        for (int j = START; j < FINISH; j++) {
            data1[j] = (byte)(data1[j] * 11);
        }
    }

    @Test
    @Warmup(0)
    @IR(counts = {IRNode.MIN_L, "> 0", IRNode.MIN_L, "<= 2"},
        phase = CompilePhase.PHASEIDEALLOOP_ITERATIONS)
    public static void test2() {
        for (int j = FINISH-1; j >= START; j--) {
            data2[j] = (byte)(data2[j] * 11);
        }
    }
}
