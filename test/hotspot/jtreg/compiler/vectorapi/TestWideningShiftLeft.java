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
import compiler.lib.ir_framework.Arguments;
import compiler.lib.ir_framework.Setup;
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
    }

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Setup
    public static Object[] setupByte() {
        return new Object[] { ByteVector.broadcast(ByteVector.SPECIES_64, (byte) 37) };
    }

    @Setup
    public static Object[] setupShort() {
        return new Object[] { ShortVector.broadcast(ShortVector.SPECIES_64, (short) 17011) };
    }

    @Setup
    public static Object[] setupInt() {
        return new Object[] { IntVector.broadcast(IntVector.SPECIES_64, 0x31234567) };
    }

    @Test
    @Arguments(setup = "setupByte")
    @IR(counts = {IRNode.RISCV_VWSLL_B2S_VI, "1"}, applyIfCPUFeature = {"zvbb", "true"})
    public static ShortVector testByteToShort(ByteVector src) {
        return ((ShortVector) src.convertShape(VectorOperators.ZERO_EXTEND_B2S,
                                               ShortVector.SPECIES_128, 0)).lanewise(VectorOperators.LSHL, 7);
    }

    @Test
    @Arguments(setup = "setupShort")
    @IR(counts = {IRNode.RISCV_VWSLL_S2I_VI, "1"}, applyIfCPUFeature = {"zvbb", "true"})
    public static IntVector testShortToInt(ShortVector src) {
        return ((IntVector) src.convertShape(VectorOperators.ZERO_EXTEND_S2I,
                                             IntVector.SPECIES_128, 0)).lanewise(VectorOperators.LSHL, 31);
    }

    @Test
    @Arguments(setup = "setupInt")
    @IR(counts = {IRNode.RISCV_VWSLL_I2L_VI, "1"}, applyIfCPUFeature = {"zvbb", "true"})
    public static LongVector testIntToLong(IntVector src) {
        return ((LongVector) src.convertShape(VectorOperators.ZERO_EXTEND_I2L,
                                              LongVector.SPECIES_128, 0)).lanewise(VectorOperators.LSHL, 31);
    }

    // ShortVector shift counts use the destination element width, so 16
    // wraps to zero and is a valid vwsll.b2s.vi immediate.
    @Test
    @Arguments(setup = "setupByte")
    @IR(counts = {IRNode.RISCV_VWSLL_B2S_VI, "1"}, applyIfCPUFeature = {"zvbb", "true"})
    public static ShortVector testByteToShortShift16(ByteVector src) {
        return ((ShortVector) src.convertShape(VectorOperators.ZERO_EXTEND_B2S,
                                               ShortVector.SPECIES_128, 0)).lanewise(VectorOperators.LSHL, 16);
    }

}
