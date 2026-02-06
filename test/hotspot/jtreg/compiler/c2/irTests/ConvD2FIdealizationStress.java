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
package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8375633
 * @requires vm.debug == true & vm.compiler2.enabled
 * @summary Test that ConvD2F::Ideal optimization is not missed with StressIncrementalInlining.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.ConvD2FIdealizationStress
 */
public class ConvD2FIdealizationStress {

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("-XX:-TieredCompilation",
                               "-XX:+UnlockDiagnosticVMOptions",
                               "-XX:+StressIncrementalInlining",
                               "-XX:VerifyIterativeGVN=1110");
        testFramework.start();
    }

    // Pattern: ConvD2F(SqrtD(ConvF2D(x))) => SqrtF(x)
    // When ConvF2D changes inside SqrtD, ConvD2F users of SqrtD should be notified.
    @Test
    @IR(counts = {IRNode.SQRT_F, ">=1"})
    public static float testSqrtConversion(float x) {
        return (float) Math.sqrt((double) x);
    }

    @Run(test = "testSqrtConversion")
    public void runSqrtConversion() {
        float result = testSqrtConversion(4.0f);
        if (result != 2.0f) {
            throw new RuntimeException("Expected 2.0f but got " + result);
        }
    }
}
