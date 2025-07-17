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

package compiler.vectorapi;

import java.util.Random;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8287794
 * @summary Test various reverse bytes ideal transforms on AARCH64 SVE.
 * @requires vm.compiler2.enabled
 * @modules jdk.incubator.vector
 * @requires vm.cpu.features ~= ".*sve.*"
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestReverseByteTransformsSVE
 */

public class TestReverseByteTransformsSVE {
    static final VectorSpecies<Long> LSPECIES = LongVector.SPECIES_MAX;
    static final VectorSpecies<Integer> ISPECIES = IntVector.SPECIES_MAX;
    static final VectorSpecies<Short> SSPECIES = ShortVector.SPECIES_MAX;

    static final int SIZE = 1024;
    static final int ITERS = 50000;

    static long [] lout = new long[SIZE];
    static long [] linp = new long[SIZE];

    static int [] iout = new int[SIZE];
    static int [] iinp = new int[SIZE];

    static short [] sout = new short[SIZE];
    static short [] sinp = new short[SIZE];

    static void init() {
        Random r = new Random(1024);
        for(int i = 0; i < SIZE; i++) {
            linp[i] = r.nextLong();
            iinp[i] = r.nextInt();
            sinp[i] = (short)r.nextInt();
        }
    }

    public static void main(String args[]) {
        init();
        TestFramework.runWithFlags("-XX:-TieredCompilation", "--add-modules=jdk.incubator.vector");
        System.out.println("PASSED");
    }

    @Test
    @IR(applyIf = {"UseSVE", " > 0"}, failOn = {IRNode.REVERSE_BYTES_VL})
    public void test_reversebytes_long_transform(long[] lout, long[] linp) {
        VectorMask<Long> mask = VectorMask.fromLong(LSPECIES, 3);
        for (int i = 0; i < LSPECIES.loopBound(linp.length); i+=LSPECIES.length()) {
            LongVector.fromArray(LSPECIES, linp, i)
                     .lanewise(VectorOperators.REVERSE_BYTES, mask)
                     .lanewise(VectorOperators.REVERSE_BYTES, mask)
                     .intoArray(lout, i);
        }
    }

    @Run(test = {"test_reversebytes_long_transform"}, mode = RunMode.STANDALONE)
    public void kernel_test_reversebytes_long_transform() {
        for (int i = 0; i < ITERS; i++) {
            test_reversebytes_long_transform(lout, linp);
        }
    }

    @Test
    @IR(applyIf = {"UseSVE", " > 0"}, failOn = {IRNode.REVERSE_BYTES_VI})
    public void test_reversebytes_int_transform(int[] iout, int[] iinp) {
        VectorMask<Integer> mask = VectorMask.fromLong(ISPECIES, 3);
        for (int i = 0; i < ISPECIES.loopBound(iinp.length); i+=ISPECIES.length()) {
            IntVector.fromArray(ISPECIES, iinp, i)
                     .lanewise(VectorOperators.REVERSE_BYTES, mask)
                     .lanewise(VectorOperators.REVERSE_BYTES, mask)
                     .intoArray(iout, i);
        }
    }

    @Run(test = {"test_reversebytes_int_transform"}, mode = RunMode.STANDALONE)
    public void kernel_test_reversebytes_int_transform() {
        for (int i = 0; i < ITERS; i++) {
            test_reversebytes_int_transform(iout, iinp);
        }
    }

    @Test
    @IR(applyIf = {"UseSVE", " > 0"}, failOn = {IRNode.REVERSE_BYTES_VS})
    public void test_reversebytes_short_transform(short[] sout, short[] sinp) {
        VectorMask<Short> mask = VectorMask.fromLong(SSPECIES, 3);
        for (int i = 0; i < SSPECIES.loopBound(sinp.length); i+=SSPECIES.length()) {
            ShortVector.fromArray(SSPECIES, sinp, i)
                     .lanewise(VectorOperators.REVERSE_BYTES, mask)
                     .lanewise(VectorOperators.REVERSE_BYTES, mask)
                     .intoArray(sout, i);
        }
    }

    @Run(test = {"test_reversebytes_short_transform"}, mode = RunMode.STANDALONE)
    public void kernel_test_reversebytes_short_transform() {
        for (int i = 0; i < ITERS; i++) {
            test_reversebytes_short_transform(sout, sinp);
        }
    }
}
