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
package compiler.igvn;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8382147
 * @summary Test that the inputs of a Phi notify the Phi for possible CmpLTMask pattern
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestCmpLTMaskWorklist {
    public static void main(String[] args) {
        var framwork = new TestFramework();
        framwork.setDefaultWarmup(0);
        framwork.addFlags("-XX:+IgnoreUnrecognizedVMOptions", "-XX:+UnlockDiagnosticVMOptions", "-XX:+AlwaysIncrementalInline");
        framwork.start();
    }

    @ForceInline
    private static boolean constantTrue() {
        return true;
    }

    @Test
    @IR(counts = {IRNode.CMP_LT_MASK, "1"})
    private static int test(int a, int b, int x) {
        int sum = x;
        while (true) {
            if (a < b) {
                sum += 1;
            } else {
                sum = x;
            }

            if (constantTrue()) {
                break;
            }
        }
        return sum;
    }

    @Run(test = "test")
    public void run() {
        Asserts.assertEQ(1, test(1, 0, 1));
        Asserts.assertEQ(2, test(0, 1, 1));
    }
}
