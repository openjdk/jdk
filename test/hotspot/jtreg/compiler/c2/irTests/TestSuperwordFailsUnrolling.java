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
import sun.hotspot.WhiteBox;

/*
 * @test
 * @bug 8283187
 * @summary C2: loop candidate for superword not always unrolled fully if superword fails
 * @library /test/lib /
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -DSkipWhiteBoxInstall=true -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.c2.irTests.TestSuperwordFailsUnrolling
 */

public class TestSuperwordFailsUnrolling {
    private static int v = 0;
    private final static WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        Object avx = wb.getVMFlag("UseAVX");
        if (avx != null && ((Long)avx) > 2) {
            TestFramework.runWithFlags("-XX:UseAVX=2", "-XX:LoopMaxUnroll=8");
        }
        TestFramework.runWithFlags("-XX:LoopMaxUnroll=8");
    }

    @Test
    @IR(applyIf = { "UsePopCountInstruction", "true" }, counts = { IRNode.POPCOUNT_L, ">=10" })
    private static int test(long[] array1, long[] array2) {
        v = 0;
        for (int i = 0; i < array1.length; i++) {
            v += Long.bitCount(array1[i]);
        }
        return v;
    }

    @Run(test = "test")
    void test_runner() {
        long[] array = new long[1000];
        test(array, array);
    }
}
