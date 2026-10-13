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

package compiler.vectorapi.reshape;

/*
 * @test
 * @bug 8392004
 * @key randomness
 * @modules jdk.incubator.vector
 * @summary Test that vector reinterpret doesn't allocate and slow down.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.RandomFactory;

import java.util.Random;

public class TestAsVectorAllocation {
    // Kept static final so that C2 can constant fold the shape.  Override the
    // vector size with -jvmArgsAppend -DvectorBits=128 (etc.) to match the
    // 128-bit shape used in the original report.
    private static final int VECTOR_BITS =
            Integer.getInteger("vectorBits", FloatVector.SPECIES_PREFERRED.vectorBitSize());

    private static final VectorSpecies<Float> FLOAT_SPECIES =
            VectorSpecies.of(float.class, VectorShape.forBitSize(VECTOR_BITS));

    public static void main(String[] args) {
        // Test with default MaxVectorSize
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    private int size;
    private float[] target;

    public TestAsVectorAllocation() {
        Random r = RandomFactory.getRandom();
        size = Math.max(1024, FLOAT_SPECIES.length());
        target = new float[size];
        for (int i = 0; i < size; i++) {
            target[i] = r.nextFloat() * 4.0f - 2.0f;
        }
    }

    @Test
    @IR(failOn = {IRNode.SAFEPOINT_SCALAROBJECT_OF, ".*"})
    public float roundTripWithReinterpret() {
        float sum = 0.0f;
        for (int i = 0; i < FLOAT_SPECIES.loopBound(size); i += FLOAT_SPECIES.length()) {
            FloatVector v = FloatVector.fromArray(FLOAT_SPECIES, target, i);
            IntVector iv = v.convert(VectorOperators.F2I, 0).reinterpretAsInts();
            FloatVector fv = iv.convert(VectorOperators.I2F, 0).reinterpretAsFloats();
            sum += fv.reduceLanes(VectorOperators.ADD);
        }
        return sum;
    }

    @Check(test = "roundTripWithReinterpret")
    public void checkRoundTripWithReinterpret(float res) {
        int sum = 0;
        for (var f : target) {
            sum += (int) f;
        }

        if ((int) res != sum) {
            throw new AssertionError("Sum incorrect, expected %d != actual %f".formatted(sum, res));
        }
    }
}
