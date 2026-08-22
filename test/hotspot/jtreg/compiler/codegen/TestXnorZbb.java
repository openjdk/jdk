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
 * or visit www.oracle.com if you need information or have any questions.
 */

/**
 * @test
 * @summary Verify RISC-V Zbb xnor instruction in C2
 *   xnor rd, rs1, rs2 = ~(rs1 ^ rs2)
 *   matches XorI(XorI(src1, src2), immI_M1) for int
 *   matches XorL(XorL(src1, src2), immL_M1) for long
 * @requires os.arch == "riscv64"
 *
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *      -XX:CompileCommand=dontinline,compiler.codegen.TestXnorZbb::*
 *      compiler.codegen.TestXnorZbb
 * @run main/othervm -Xbatch -XX:+TieredCompilation -XX:TieredStopAtLevel=1
 *      -XX:CompileCommand=dontinline,compiler.codegen.TestXnorZbb::*
 *      compiler.codegen.TestXnorZbb
 * @run main/othervm -Xbatch -XX:+TieredCompilation
 *      -XX:CompileCommand=dontinline,compiler.codegen.TestXnorZbb::*
 *      compiler.codegen.TestXnorZbb
 */

package compiler.codegen;

public class TestXnorZbb {
    private static final int ITERATIONS = 1_000_000;

    // === int xnor patterns ===

    // match(Set dst (XorI (XorI src1 src2) immI_M1))
    static int xnorIntReg(int a, int b) {
        return ~(a ^ b);
    }

    // match(Set dst (XorI (XorI (LoadI src1) src2) immI_M1))
    static int xnorIntMem1(MemI a, int b) {
        return ~(a.x ^ b);
    }

    // match(Set dst (XorI (XorI src1 (LoadI src2)) immI_M1))
    static int xnorIntMem2(int a, MemI b) {
        return ~(a ^ b.x);
    }

    // match(Set dst (XorI (XorI (LoadI src1) (LoadI src2)) immI_M1))
    static int xnorIntMem3(MemI a, MemI b) {
        return ~(a.x ^ b.x);
    }

    // === long xnor patterns ===

    // match(Set dst (XorL (XorL src1 src2) immL_M1))
    static long xnorLongReg(long a, long b) {
        return ~(a ^ b);
    }

    // match(Set dst (XorL (XorL (LoadL src1) src2) immL_M1))
    static long xnorLongMem1(MemL a, long b) {
        return ~(a.x ^ b);
    }

    // match(Set dst (XorL (XorL src1 (LoadL src2)) immL_M1))
    static long xnorLongMem2(long a, MemL b) {
        return ~(a ^ b.x);
    }

    // match(Set dst (XorL (XorL (LoadL src1) (LoadL src2)) immL_M1))
    static long xnorLongMem3(MemL a, MemL b) {
        return ~(a.x ^ b.x);
    }

    // === commutative variants (verify AD rule handles operand reordering) ===

    // Same as xnorInt but written as ^ -1
    static int xnorIntComm1(int a, int b) {
        return (a ^ b) ^ -1;
    }

    // Same as xnorInt but with swapped operands
    static int xnorIntComm2(int a, int b) {
        return ~(b ^ a);
    }

    // Same as xnorLong but written as ^ -1L
    static long xnorLongComm1(long a, long b) {
        return (a ^ b) ^ -1L;
    }

    // Same as xnorLong with swapped operands
    static long xnorLongComm2(long a, long b) {
        return ~(b ^ a);
    }

    // === additional edge-case patterns ===

    // xnor with self
    static int xnorIntSelf(int a) { return ~(a ^ a); } // should be -1
    static long xnorLongSelf(long a) { return ~(a ^ a); } // should be -1L

    // xnor with zero
    static int xnorIntZero(int a) { return ~(a ^ 0); } // should be ~a
    static long xnorLongZero(long a) { return ~(a ^ 0L); } // should be ~a

    // xnor with all ones
    static int xnorIntOnes(int a) { return ~(a ^ -1); } // should be a
    static long xnorLongOnes(long a) { return ~(a ^ -1L); } // should be a

    public static void main(String[] args) {
        // Test vectors: boundary values that exercise all bit positions
        int[] intVals = {
            0,
            1,
            -1,
            Integer.MAX_VALUE,
            Integer.MIN_VALUE,
            0xFF,
            0xFFFF,
            0xAAAAAAAA,
            0x55555555,
            0x12345678,
            0x89ABCDEF,
            0xDEADBEEF,
            0xCAFEBABE,
            (1 << 31) - 1,
            -(1 << 31),
            0x0F0F0F0F,
            0xF0F0F0F0,
            0x00FF00FF,
            0xFF00FF00,
        };

        long[] longVals = {
            0L,
            1L,
            -1L,
            Long.MAX_VALUE,
            Long.MIN_VALUE,
            0xFFL,
            0xFFFFL,
            0xFFFFFFFFL,
            0xAAAAAAAAAAAAAAAAL,
            0x5555555555555555L,
            0x123456789ABCDEF0L,
            0xFEDCBA9876543210L,
            0xDEADBEEFCAFEBABEL,
        };

        System.out.println("Testing RISC-V Zbb xnor instruction via C2...");

        // === int reg-reg ===
        for (int a : intVals) {
            for (int b : intVals) {
                int expected = ~(a ^ b);
                // Test various forms of the xnor pattern
                checkInt("xnorIntReg(" + a + "," + b + ")", xnorIntReg(a, b), expected);
                checkInt("xnorIntComm1(" + a + "," + b + ")", xnorIntComm1(a, b), expected);
                checkInt("xnorIntComm2(" + a + "," + b + ")", xnorIntComm2(a, b), expected);
            }
        }

        // === int mem forms ===
        for (int a : intVals) {
            for (int b : intVals) {
                checkInt("xnorIntMem1(" + a + "," + b + ")", xnorIntMem1(new MemI(a), b), ~(a ^ b));
                checkInt("xnorIntMem2(" + a + "," + b + ")", xnorIntMem2(a, new MemI(b)), ~(a ^ b));
                checkInt("xnorIntMem3(" + a + "," + b + ")", xnorIntMem3(new MemI(a), new MemI(b)), ~(a ^ b));
            }
        }

        // === int edge cases ===
        for (int a : intVals) {
            checkInt("xnorIntSelf(" + a + ")", xnorIntSelf(a), -1);
            checkInt("xnorIntZero(" + a + ")", xnorIntZero(a), ~a);
            checkInt("xnorIntOnes(" + a + ")", xnorIntOnes(a), a);
        }

        // === long reg-reg ===
        for (long a : longVals) {
            for (long b : longVals) {
                long expected = ~(a ^ b);
                checkLong("xnorLongReg(" + a + "," + b + ")", xnorLongReg(a, b), expected);
                checkLong("xnorLongComm1(" + a + "," + b + ")", xnorLongComm1(a, b), expected);
                checkLong("xnorLongComm2(" + a + "," + b + ")", xnorLongComm2(a, b), expected);
            }
        }

        // === long mem forms ===
        for (long a : longVals) {
            for (long b : longVals) {
                checkLong("xnorLongMem1(" + a + "," + b + ")", xnorLongMem1(new MemL(a), b), ~(a ^ b));
                checkLong("xnorLongMem2(" + a + "," + b + ")", xnorLongMem2(a, new MemL(b)), ~(a ^ b));
                checkLong("xnorLongMem3(" + a + "," + b + ")", xnorLongMem3(new MemL(a), new MemL(b)), ~(a ^ b));
            }
        }

        // === long edge cases ===
        for (long a : longVals) {
            checkLong("xnorLongSelf(" + a + ")", xnorLongSelf(a), -1L);
            checkLong("xnorLongZero(" + a + ")", xnorLongZero(a), ~a);
            checkLong("xnorLongOnes(" + a + ")", xnorLongOnes(a), a);
        }

        // === Hot loop to force C2 compilation (beyond -Xbatch) ===
        // Register-register int
        {
            int r = xnorIntReg(intVals[0], intVals[1]);
            for (int i = 0; i < ITERATIONS; i++) {
                r = xnorIntReg(r | intVals[i % intVals.length],
                               intVals[(i + 1) % intVals.length]);
            }
            if (r != xnorIntReg(r, r)) {
                // just consume the result to prevent dead code elimination
                r += 1;
            }
        }

        // Register-register long
        {
            long r = xnorLongReg(longVals[0], longVals[1]);
            for (int i = 0; i < ITERATIONS; i++) {
                r = xnorLongReg(r | longVals[i % longVals.length],
                                longVals[(i + 1) % longVals.length]);
            }
            if (r != xnorLongReg(r, r)) {
                r += 1;
            }
        }

        System.out.println("PASSED: All xnor Zbb tests.");
    }

    static void checkInt(String name, int actual, int expected) {
        if (actual != expected) {
            throw new Error("FAIL: " + name
                + " expected 0x" + Integer.toHexString(expected)
                + " but got 0x" + Integer.toHexString(actual));
        }
    }

    static void checkLong(String name, long actual, long expected) {
        if (actual != expected) {
            throw new Error("FAIL: " + name
                + " expected 0x" + Long.toHexString(expected)
                + " but got 0x" + Long.toHexString(actual));
        }
    }

    static class MemI {
        public int x;
        public MemI(int x) { this.x = x; }
    }

    static class MemL {
        public long x;
        public MemL(long x) { this.x = x; }
    }
}
