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
* @summary Test x86_64 intrinsics for Float methods isNaN, isFinite, isInfinite.
* @requires vm.cpu.features ~= ".*avx512dq.*"
* @library /test/lib /
* @run driver compiler.intrinsics.TestFloatClassCheck
*/

package compiler.intrinsics;
import compiler.lib.ir_framework.*;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public class TestFloatClassCheck {
    RandomGenerator rng;
    int BUFFER_SIZE = 1024;
    float[] inputs;
    boolean[] outputs;

    public static void main(String args[]) {
        TestFramework.run(TestFloatClassCheck.class);
    }

    public TestFloatClassCheck() {
        outputs = new boolean[BUFFER_SIZE];
        inputs = new float[BUFFER_SIZE];
        RandomGenerator rng = RandomGeneratorFactory.getDefault().create(0);
        float input;
        for (int i = 0; i < BUFFER_SIZE; i++) {
            if (i % 5 == 0) {
                input = (i%2 == 0) ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
            }
            else if (i % 3 == 0) input = Float.NaN;
            else input = rng.nextFloat();
            inputs[i] = input;
        }
    }

    @Test // needs to be run in (fast) debug mode
    @Warmup(10000)
    @IR(counts = {"IsInfiniteF", ">= 1"}) // Atleast one IsInfiniteF node is generated if intrinsic is used
    public void testIsInfinite() {
        for (int i = 0; i < BUFFER_SIZE; i++) {
            outputs[i] = Float.isInfinite(inputs[i]);
        }
        checkResult("isInfinite");
    }

    public void checkResult(String method) {
        for (int i=0; i < BUFFER_SIZE; i++) {
            boolean expected = floatClassCheck(inputs[i], method);
            if (expected != outputs[i]) {
                String errorMsg = "Correctness check failed for Float." + method +
                "() for input = " + inputs[i];
                throw new RuntimeException(errorMsg);
            }
        }
    }

    public boolean floatClassCheck(float f, String method) {
        int infBits = Float.floatToRawIntBits(Float.POSITIVE_INFINITY);
        int bits =  Float.floatToRawIntBits(f);
        bits = bits & Integer.MAX_VALUE;
        switch (method) {
            case "isFinite": return (bits < infBits);
            case "isInfinite": return (bits == infBits);
            case "isNaN": return (bits > infBits);
            default: throw new IllegalArgumentException("incorrect method for Float");
        }
    }

}
