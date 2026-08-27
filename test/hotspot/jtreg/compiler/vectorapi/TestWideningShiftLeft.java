/*
 * Copyright (c) 2026, Institute of Software, Chinese Academy of Sciences.
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

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;

/*
 * @test
 * @bug 8329897
 * @summary Test RISC-V Zvbb widening shift left rules
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestWideningShiftLeft
 */

public class TestWideningShiftLeft {
    private static final byte[] BYTES = new byte[ByteVector.SPECIES_64.length()];
    private static final short[] SHORTS = new short[ShortVector.SPECIES_64.length()];
    private static final int[] INTS = new int[IntVector.SPECIES_64.length()];
    private static final ByteVector BYTE_VECTOR;
    private static final ShortVector SHORT_VECTOR;
    private static final IntVector INT_VECTOR;

    static {
        for (int i = 0; i < BYTES.length; i++) {
            BYTES[i] = (byte) (i * 37 - 91);
        }
        for (int i = 0; i < SHORTS.length; i++) {
            SHORTS[i] = (short) (i * 17011 - 30001);
        }
        for (int i = 0; i < INTS.length; i++) {
            INTS[i] = i * 0x31234567 + 0x89abcdef;
        }
        BYTE_VECTOR = ByteVector.fromArray(ByteVector.SPECIES_64, BYTES, 0);
        SHORT_VECTOR = ShortVector.fromArray(ShortVector.SPECIES_64, SHORTS, 0);
        INT_VECTOR = IntVector.fromArray(IntVector.SPECIES_64, INTS, 0);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }

    @Test
    @IR(counts = {IRNode.RISCV_VWSLL_B2S_VI, "1"}, applyIfCPUFeature = {"zvbb", "true"})
    public static ShortVector testByteToShort() {
        return ((ShortVector) BYTE_VECTOR.convertShape(VectorOperators.ZERO_EXTEND_B2S,
                                                       ShortVector.SPECIES_128, 0)).lanewise(VectorOperators.LSHL, 7);
    }

    @Run(test = "testByteToShort")
    public static void runByteToShort() {
        ShortVector result = testByteToShort();
        for (int i = 0; i < result.length(); i++) {
            short expected = (short) ((BYTES[i] & 0xff) << 7);
            if (result.lane(i) != expected) {
                throw new RuntimeException("byte-to-short mismatch at lane " + i);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.RISCV_VWSLL_S2I_VI, "1"}, applyIfCPUFeature = {"zvbb", "true"})
    public static IntVector testShortToInt() {
        return ((IntVector) SHORT_VECTOR.convertShape(VectorOperators.ZERO_EXTEND_S2I,
                                                      IntVector.SPECIES_128, 0)).lanewise(VectorOperators.LSHL, 31);
    }

    @Run(test = "testShortToInt")
    public static void runShortToInt() {
        IntVector result = testShortToInt();
        for (int i = 0; i < result.length(); i++) {
            int expected = (SHORTS[i] & 0xffff) << 31;
            if (result.lane(i) != expected) {
                throw new RuntimeException("short-to-int mismatch at lane " + i);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.RISCV_VWSLL_I2L_VI, "1"}, applyIfCPUFeature = {"zvbb", "true"})
    public static LongVector testIntToLong() {
        return ((LongVector) INT_VECTOR.convertShape(VectorOperators.ZERO_EXTEND_I2L,
                                                     LongVector.SPECIES_128, 0)).lanewise(VectorOperators.LSHL, 31);
    }

    @Run(test = "testIntToLong")
    public static void runIntToLong() {
        LongVector result = testIntToLong();
        for (int i = 0; i < result.length(); i++) {
            long expected = (INTS[i] & 0xffffffffL) << 31;
            if (result.lane(i) != expected) {
                throw new RuntimeException("int-to-long mismatch at lane " + i);
            }
        }
    }

    // ShortVector masks the shift count to four bits, so 16 is equivalent to
    // zero and C2 eliminates the shift before matching vwsll.vi.
    @Test
    @IR(failOn = {IRNode.RISCV_VWSLL_B2S_VI}, applyIfCPUFeature = {"zvbb", "true"})
    public static ShortVector testByteToShortShift16() {
        return ((ShortVector) BYTE_VECTOR.convertShape(VectorOperators.ZERO_EXTEND_B2S,
                                                       ShortVector.SPECIES_128, 0)).lanewise(VectorOperators.LSHL, 16);
    }

    @Run(test = "testByteToShortShift16")
    public static void runByteToShortShift16() {
        ShortVector result = testByteToShortShift16();
        for (int i = 0; i < result.length(); i++) {
            short expected = (short) (BYTES[i] & 0xff);
            if (result.lane(i) != expected) {
                throw new RuntimeException("byte-to-short shift-16 mismatch at lane " + i);
            }
        }
    }
}
