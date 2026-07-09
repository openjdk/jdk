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

/*
 * @test
 * @bug 8386155
 * @summary Test missing trunctation after subword vector operation reassociation
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestTruncationAfterReassociation
 */

package compiler.vectorapi;

import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import jdk.incubator.vector.*;

public class TestTruncationAfterReassociation {

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    static final VectorSpecies<Byte>  BSP = ByteVector.SPECIES_PREFERRED;
    static final VectorSpecies<Short> SSP = ShortVector.SPECIES_PREFERRED;

    // Random value source (covers the full integer range, biased towards
    // interesting/special values such as 0, MIN, MAX and powers of two).
    static final Generator<Integer> INT_GEN = Generators.G.ints();

    static final int RAND_ITERS = 2048;

    static byte  B_127  = (byte) 127;
    static byte  B_N16  = (byte) -16;
    static byte  B_N7   = (byte) -7;
    static byte  B_100  = (byte) 100;
    static byte  B_4    = (byte) 4;
    static byte  B_5    = (byte) 5;
    static byte  B_10   = (byte) 10;
    static byte  B_N128 = (byte) -128;
    static byte  B_1    = (byte) 1;

    static short S_32767  = (short) 32767;
    static short S_N16    = (short) -16;
    static short S_N7     = (short) -7;
    static short S_200    = (short) 200;
    static short S_5      = (short) 5;
    static short S_10     = (short) 10;
    static short S_N32768 = (short) -32768;
    static short S_1      = (short) 1;

    static byte  bmul(byte x, byte y)  { return (byte) (x * y); }
    static byte  badd(byte x, byte y)  { return (byte) (x + y); }
    static byte  bsub(byte x, byte y)  { return (byte) (x - y); }
    static byte  bmax(byte x, byte y)  { return (byte) Math.max(x, y); }
    static byte  bmin(byte x, byte y)  { return (byte) Math.min(x, y); }

    static short smul(short x, short y) { return (short) (x * y); }
    static short sadd(short x, short y) { return (short) (x + y); }
    static short ssub(short x, short y) { return (short) (x - y); }
    static short smax(short x, short y) { return (short) Math.max(x, y); }
    static short smin(short x, short y) { return (short) Math.min(x, y); }

    @Test
    static byte bug_8386155_reproducer() {
        return ByteVector.broadcast(ByteVector.SPECIES_64, (byte) 127)
                // Expected: mul is truncated to signed byte: 127 * -16 = (byte)-2032 = 16
                .mul((byte) -16)
                // Expected: max(16, -7) = 16
                .max((byte) -7)
                .lane(0);
    }

    @Run(test = "bug_8386155_reproducer")
    static void run_bug_8386155_reproducer() {
        Verify.checkEQ(bug_8386155_reproducer(), (byte) 16);
    }

    /* =========================================================
     * BYTE: <overflowing ADD/SUB/MUL> then <MAX/MIN>.
     * ========================================================= */

    @Test
    @IR(failOn = { IRNode.MUL_VB, IRNode.MAX_VB },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1", IRNode.MAX_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static byte byte_mul_then_max() {
        return ByteVector.broadcast(BSP, B_127)
                .mul(ByteVector.broadcast(BSP, B_N16))
                .max(ByteVector.broadcast(BSP, B_N7))
                .lane(0);
    }

    @Run(test = "byte_mul_then_max")
    static void run_byte_mul_then_max() {
        Verify.checkEQ(byte_mul_then_max(), bmax(bmul(B_127, B_N16), B_N7));
    }

    @Test
    @IR(failOn = { IRNode.MUL_VB, IRNode.MIN_VB },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1", IRNode.MIN_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static byte byte_mul_then_min() {
        return ByteVector.broadcast(BSP, B_100)
                .mul(ByteVector.broadcast(BSP, B_4))
                .min(ByteVector.broadcast(BSP, B_5))
                .lane(0);
    }

    @Run(test = "byte_mul_then_min")
    static void run_byte_mul_then_min() {
        Verify.checkEQ(byte_mul_then_min(), bmin(bmul(B_100, B_4), B_5));
    }

    @Test
    @IR(failOn = { IRNode.ADD_VB, IRNode.MAX_VB },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_I, ">= 1", IRNode.MAX_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static byte byte_add_then_max() {
        return ByteVector.broadcast(BSP, B_127)
                .add(ByteVector.broadcast(BSP, B_127))
                .max(ByteVector.broadcast(BSP, B_5))
                .lane(0);
    }

    @Run(test = "byte_add_then_max")
    static void run_byte_add_then_max() {
        Verify.checkEQ(byte_add_then_max(), bmax(badd(B_127, B_127), B_5));
    }

    @Test
    @IR(failOn = { IRNode.ADD_VB, IRNode.MIN_VB },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_I, ">= 1", IRNode.MIN_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static byte byte_add_then_min() {
        return ByteVector.broadcast(BSP, B_127)
                .add(ByteVector.broadcast(BSP, B_127))
                .min(ByteVector.broadcast(BSP, B_10))
                .lane(0);
    }

    @Run(test = "byte_add_then_min")
    static void run_byte_add_then_min() {
        Verify.checkEQ(byte_add_then_min(), bmin(badd(B_127, B_127), B_10));
    }

    @Test
    @IR(failOn = { IRNode.SUB_VB, IRNode.MAX_VB },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_I, ">= 1", IRNode.MAX_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static byte byte_sub_then_max() {
        return ByteVector.broadcast(BSP, B_N128)
                .sub(ByteVector.broadcast(BSP, B_1))
                .max(ByteVector.broadcast(BSP, B_10))
                .lane(0);
    }

    @Run(test = "byte_sub_then_max")
    static void run_byte_sub_then_max() {
        Verify.checkEQ(byte_sub_then_max(), bmax(bsub(B_N128, B_1), B_10));
    }

    @Test
    @IR(failOn = { IRNode.SUB_VB, IRNode.MIN_VB },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_I, ">= 1", IRNode.MIN_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static byte byte_sub_then_min() {
        return ByteVector.broadcast(BSP, B_N128)
                .sub(ByteVector.broadcast(BSP, B_1))
                .min(ByteVector.broadcast(BSP, B_10))
                .lane(0);
    }

    @Run(test = "byte_sub_then_min")
    static void run_byte_sub_then_min() {
        Verify.checkEQ(byte_sub_then_min(), bmin(bsub(B_N128, B_1), B_10));
    }

    /* =========================================================
     * SHORT: <overflowing ADD/SUB/MUL> then <MAX/MIN>.
     * ========================================================= */

    @Test
    @IR(failOn = { IRNode.MUL_VS, IRNode.MAX_VS },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1", IRNode.MAX_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static short short_mul_then_max() {
        return ShortVector.broadcast(SSP, S_32767)
                .mul(ShortVector.broadcast(SSP, S_N16))
                .max(ShortVector.broadcast(SSP, S_N7))
                .lane(0);
    }

    @Run(test = "short_mul_then_max")
    static void run_short_mul_then_max() {
        Verify.checkEQ(short_mul_then_max(), smax(smul(S_32767, S_N16), S_N7));
    }

    @Test
    @IR(failOn = { IRNode.MUL_VS, IRNode.MIN_VS },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1", IRNode.MIN_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static short short_mul_then_min() {
        return ShortVector.broadcast(SSP, S_200)
                .mul(ShortVector.broadcast(SSP, S_200))
                .min(ShortVector.broadcast(SSP, S_5))
                .lane(0);
    }

    @Run(test = "short_mul_then_min")
    static void run_short_mul_then_min() {
        Verify.checkEQ(short_mul_then_min(), smin(smul(S_200, S_200), S_5));
    }

    @Test
    @IR(failOn = { IRNode.ADD_VS, IRNode.MAX_VS },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_I, ">= 1", IRNode.MAX_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static short short_add_then_max() {
        return ShortVector.broadcast(SSP, S_32767)
                .add(ShortVector.broadcast(SSP, S_32767))
                .max(ShortVector.broadcast(SSP, S_5))
                .lane(0);
    }

    @Run(test = "short_add_then_max")
    static void run_short_add_then_max() {
        Verify.checkEQ(short_add_then_max(), smax(sadd(S_32767, S_32767), S_5));
    }

    @Test
    @IR(failOn = { IRNode.SUB_VS, IRNode.MIN_VS },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.SUB_I, ">= 1", IRNode.MIN_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static short short_sub_then_min() {
        return ShortVector.broadcast(SSP, S_N32768)
                .sub(ShortVector.broadcast(SSP, S_1))
                .min(ShortVector.broadcast(SSP, S_10))
                .lane(0);
    }

    @Run(test = "short_sub_then_min")
    static void run_short_sub_then_min() {
        Verify.checkEQ(short_sub_then_min(), smin(ssub(S_N32768, S_1), S_10));
    }

    // Two independent overflowing products feeding a single max
    @Test
    @IR(failOn = { IRNode.MUL_VB, IRNode.MAX_VB },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1", IRNode.MAX_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static byte byte_two_muls_then_max() {
        return ByteVector.broadcast(BSP, B_127).mul(ByteVector.broadcast(BSP, B_N16))
                .max(ByteVector.broadcast(BSP, B_100).mul(ByteVector.broadcast(BSP, B_4)))
                .lane(0);
    }

    @Run(test = "byte_two_muls_then_max")
    static void run_byte_two_muls_then_max() {
        Verify.checkEQ(byte_two_muls_then_max(),
                       bmax(bmul(B_127, B_N16), bmul(B_100, B_4)));
    }

    // Chained (reassociated) adds whose running value overflows, then a max
    @Test
    @IR(failOn = { IRNode.ADD_VB, IRNode.MAX_VB },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.ADD_I, ">= 1", IRNode.MAX_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static byte byte_chain_add_then_max() {
        return ByteVector.broadcast(BSP, B_127)
                .add(ByteVector.broadcast(BSP, B_127))
                .add(ByteVector.broadcast(BSP, B_127))
                .max(ByteVector.broadcast(BSP, B_127))
                .lane(0);
    }

    @Run(test = "byte_chain_add_then_max")
    static void run_byte_chain_add_then_max() {
        Verify.checkEQ(byte_chain_add_then_max(),
                       bmax(badd(badd(B_127, B_127), B_127), B_127));
    }

    // Overflowing product feeding max then min:
    @Test
    @IR(failOn = { IRNode.MUL_VB, IRNode.MAX_VB, IRNode.MIN_VB },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1", IRNode.MAX_I, ">= 1", IRNode.MIN_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static byte byte_mul_max_then_min() {
        return ByteVector.broadcast(BSP, B_127)
                .mul(ByteVector.broadcast(BSP, B_N16))
                .max(ByteVector.broadcast(BSP, B_N7))
                .min(ByteVector.broadcast(BSP, B_10))
                .lane(0);
    }

    @Run(test = "byte_mul_max_then_min")
    static void run_byte_mul_max_then_min() {
        Verify.checkEQ(byte_mul_max_then_min(),
                       bmin(bmax(bmul(B_127, B_N16), B_N7), B_10));
    }

    // Two overflowing short products feeding a single min
    @Test
    @IR(failOn = { IRNode.MUL_VS, IRNode.MIN_VS },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1", IRNode.MIN_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static short short_two_muls_then_min() {
        return ShortVector.broadcast(SSP, S_200).mul(ShortVector.broadcast(SSP, S_200))
                .min(ShortVector.broadcast(SSP, S_32767).mul(ShortVector.broadcast(SSP, S_N16)))
                .lane(0);
    }

    @Run(test = "short_two_muls_then_min")
    static void run_short_two_muls_then_min() {
        Verify.checkEQ(short_two_muls_then_min(),
                       smin(smul(S_200, S_200), smul(S_32767, S_N16)));
    }

    /* =========================================================
     * Randomized coverage (Generators).
     * ========================================================= */

    @Test
    @IR(failOn = { IRNode.MUL_VB, IRNode.MAX_VB },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1", IRNode.MAX_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static byte rand_byte_mul_then_max(byte a, byte b, byte c) {
        return ByteVector.broadcast(BSP, a)
                .mul(ByteVector.broadcast(BSP, b))
                .max(ByteVector.broadcast(BSP, c))
                .lane(0);
    }

    @Run(test = "rand_byte_mul_then_max")
    static void run_rand_byte_mul_then_max() {
        for (int i = 0; i < RAND_ITERS; i++) {
            byte a = INT_GEN.next().byteValue();
            byte b = INT_GEN.next().byteValue();
            byte c = INT_GEN.next().byteValue();
            Verify.checkEQ(rand_byte_mul_then_max(a, b, c), bmax(bmul(a, b), c));
        }
    }

    @Test
    @IR(failOn = { IRNode.MUL_VB, IRNode.MIN_VB },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1", IRNode.MIN_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static byte rand_byte_mul_then_min(byte a, byte b, byte c) {
        return ByteVector.broadcast(BSP, a)
                .mul(ByteVector.broadcast(BSP, b))
                .min(ByteVector.broadcast(BSP, c))
                .lane(0);
    }

    @Run(test = "rand_byte_mul_then_min")
    static void run_rand_byte_mul_then_min() {
        for (int i = 0; i < RAND_ITERS; i++) {
            byte a = INT_GEN.next().byteValue();
            byte b = INT_GEN.next().byteValue();
            byte c = INT_GEN.next().byteValue();
            Verify.checkEQ(rand_byte_mul_then_min(a, b, c), bmin(bmul(a, b), c));
        }
    }

    @Test
    @IR(failOn = { IRNode.MUL_VS, IRNode.MAX_VS },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1", IRNode.MAX_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static short rand_short_mul_then_max(short a, short b, short c) {
        return ShortVector.broadcast(SSP, a)
                .mul(ShortVector.broadcast(SSP, b))
                .max(ShortVector.broadcast(SSP, c))
                .lane(0);
    }

    @Run(test = "rand_short_mul_then_max")
    static void run_rand_short_mul_then_max() {
        for (int i = 0; i < RAND_ITERS; i++) {
            short a = INT_GEN.next().shortValue();
            short b = INT_GEN.next().shortValue();
            short c = INT_GEN.next().shortValue();
            Verify.checkEQ(rand_short_mul_then_max(a, b, c), smax(smul(a, b), c));
        }
    }

    @Test
    @IR(failOn = { IRNode.MUL_VS, IRNode.MIN_VS },
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"},
        counts = { IRNode.MUL_I, ">= 1", IRNode.MIN_I, ">= 1",
                   IRNode.LSHIFT_I, ">= 1", IRNode.RSHIFT_I, ">= 1" })
    static short rand_short_mul_then_min(short a, short b, short c) {
        return ShortVector.broadcast(SSP, a)
                .mul(ShortVector.broadcast(SSP, b))
                .min(ShortVector.broadcast(SSP, c))
                .lane(0);
    }

    @Run(test = "rand_short_mul_then_min")
    static void run_rand_short_mul_then_min() {
        for (int i = 0; i < RAND_ITERS; i++) {
            short a = INT_GEN.next().shortValue();
            short b = INT_GEN.next().shortValue();
            short c = INT_GEN.next().shortValue();
            Verify.checkEQ(rand_short_mul_then_min(a, b, c), smin(smul(a, b), c));
        }
    }
}
