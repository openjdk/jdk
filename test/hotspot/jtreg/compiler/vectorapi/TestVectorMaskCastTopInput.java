/*
 * Copyright (c) 2026, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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
 * @bug 8391418
 * @key randomness
 * @summary VectorMaskCastNode::Identity must handle the TOP input during IGVN
 * @requires vm.debug == true & vm.compiler2.enabled
 * @modules jdk.incubator.vector
 *
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+StressIGVN -XX:RepeatCompilation=100
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 */

package compiler.vectorapi;

import java.lang.foreign.MemorySegment;
import java.nio.ByteOrder;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

public class TestVectorMaskCastTopInput {
    static final VectorSpecies<Double> SPECIES = DoubleVector.SPECIES_256;

    public static void main(String[] args) {
        for (int i = 0; i < 1000; i++) {
            test();
        }
    }

    static void test() {
        MemorySegment segment = MemorySegment.ofArray(new byte[2000]);
        VectorMask<Double> vectorMask = VectorMask.fromLong(SPECIES, 0x111);
        DoubleVector vector = DoubleVector.zero(SPECIES);

        for (int i = 0; i < 1000; i += 16) {
            vector.intoMemorySegment(segment, i, ByteOrder.BIG_ENDIAN, vectorMask);
        }

        // This store is always out of bounds: C2 kills this path, leaving the
        // VectorMaskCastNode that feeds the store's mask with a TOP input.
        try {
            vector.intoMemorySegment(segment, -1, ByteOrder.BIG_ENDIAN, vectorMask);
        } catch (IndexOutOfBoundsException e) {
            // Expected.
        }
    }
}
