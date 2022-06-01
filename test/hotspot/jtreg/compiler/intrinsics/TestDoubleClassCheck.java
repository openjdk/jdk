/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
* @test
* @summary Test x86_64 intrinsics for Double methods isNaN, isFinite, isInfinite.
* @requires vm.cpu.features ~= ".*avx512dq.*"
* @library /test/lib /
* @run driver compiler.intrinsics.TestDoubleClassCheck
*/

package compiler.intrinsics;
import compiler.lib.ir_framework.*;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public class TestDoubleClassCheck {
    RandomGenerator rng;
    int BUFFER_SIZE = 1024;
    double[] inputs;
    boolean[] outputs;

    public static void main(String args[]) {
        TestFramework.run(TestDoubleClassCheck.class);
    }

    public TestDoubleClassCheck() {
        outputs = new boolean[BUFFER_SIZE];
        inputs = new double[BUFFER_SIZE];
        RandomGenerator rng = RandomGeneratorFactory.getDefault().create(0);
        double input;
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (i % 5 == 0) {
                input = (i%2 == 0) ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            }
            else if (i % 3 == 0) input = Double.NaN;
            else input = rng.nextDouble();
            inputs[i] = input;
        }
    }

    @Test // needs to be run in (fast) debug mode
    @Warmup(10000)
    @IR(counts = {"IsInfiniteD", ">= 1"}) // Atleast one IsInfiniteD node is generated if intrinsic is used
    public void testIsInfinite() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            outputs[i] = Double.isInfinite(inputs[i]);
        }
        checkResult("isInfinite");
    }


    public void checkResult(String method) {
        for (int i=0; i < BUFFER_SIZE; i++) {
            boolean expected = doubleClassCheck(inputs[i], method);
            if (expected != outputs[i]) {
                String errorMsg = "Correctness check failed for Double." + method +
                "() for input = " + inputs[i];
                throw new RuntimeException(errorMsg);
            }
        }
    }

    public boolean doubleClassCheck(double f, String method) {
        long infBits = Double.doubleToRawLongBits(Double.POSITIVE_INFINITY);
        long bits =  Double.doubleToRawLongBits(f);
        bits = bits & Long.MAX_VALUE;
        switch (method) {
            case "isFinite": return (bits < infBits);
            case "isInfinite": return (bits == infBits);
            case "isNaN": return (bits > infBits);
            default: throw new IllegalArgumentException("incorrect method for Double");
        }
    }

}
