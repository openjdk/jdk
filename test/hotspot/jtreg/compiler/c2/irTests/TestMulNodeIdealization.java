/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8291336
 * @key randomness
 * @summary Test that transformation of multiply-by-2 is appropriately turned into additions.
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.irTests.TestMulNodeIdealization
 */
public class TestMulNodeIdealization {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.MUL})
    // Checks x * 2 -> x + x
    public float testFloat(float x) {
        return x * 2;
    }

    @Test
    @IR(failOn = {IRNode.MUL})
    // Checks x * 2 -> x + x
    public double testDouble(double x) {
        return x * 2;
    }

    @Run(test = "testFloat")
    public void runTestFloat() {
        float value = RANDOM.nextFloat();
        float interpreterResult = value * 2;
        Asserts.assertEQ(testFloat(value), interpreterResult);
    }

    @Run(test = "testDouble")
    public void runTestDouble() {
        double value = RANDOM.nextDouble();
        double interpreterResult = value * 2;
        Asserts.assertEQ(testDouble(value), interpreterResult);
    }
}