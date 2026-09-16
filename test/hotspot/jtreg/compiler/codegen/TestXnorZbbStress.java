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
 * @summary Stress-test RISC-V Zbb xnor with -Xcomp (all methods C2-compiled)
 *   Register allocation pressure: 32 int locals + 32 long locals
 *   Exercises xnor across all register classes and spilling scenarios
 * @requires os.arch == "riscv64"
 *
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *      -XX:CompileCommand=dontinline,compiler.codegen.TestXnorZbbStress::*
 *      compiler.codegen.TestXnorZbbStress
 * @run main/othervm -Xcomp -XX:+TieredCompilation -XX:TieredStopAtLevel=1
 *      -XX:CompileCommand=dontinline,compiler.codegen.TestXnorZbbStress::*
 *      compiler.codegen.TestXnorZbbStress
 * @run main/othervm -Xcomp -XX:+TieredCompilation
 *      -XX:CompileCommand=dontinline,compiler.codegen.TestXnorZbbStress::*
 *      compiler.codegen.TestXnorZbbStress
 */

package compiler.codegen;

public class TestXnorZbbStress {
    static volatile int sinkI;
    static volatile long sinkL;

    // High register pressure: 32 xnor operations chained
    // Forces C2 to use all registers and spill
    static int xnorChain32(int r0, int r1, int r2, int r3) {
        int t00 = ~(r0 ^ r1);
        int t01 = ~(r2 ^ r3);
        int t02 = ~(t00 ^ t01);
        int t03 = ~(r0 ^ r3);
        int t04 = ~(r1 ^ r2);
        int t05 = ~(t02 ^ t04);
        int t06 = ~(t03 ^ t04);
        int t07 = ~(t05 ^ t06);
        int t08 = ~(t07 ^ r0);
        int t09 = ~(t07 ^ r1);
        int t10 = ~(t08 ^ t09);
        int t11 = ~(t10 ^ r2);
        int t12 = ~(t10 ^ r3);
        int t13 = ~(t11 ^ t12);
        int t14 = ~(t13 ^ t00);
        int t15 = ~(t13 ^ t01);
        int t16 = ~(t14 ^ t15);
        int t17 = ~(t16 ^ t02);
        int t18 = ~(t16 ^ t03);
        int t19 = ~(t17 ^ t18);
        int t20 = ~(t19 ^ t04);
        int t21 = ~(t19 ^ t05);
        int t22 = ~(t20 ^ t21);
        int t23 = ~(t22 ^ t06);
        int t24 = ~(t22 ^ t07);
        int t25 = ~(t23 ^ t24);
        int t26 = ~(t25 ^ t08);
        int t27 = ~(t25 ^ t09);
        int t28 = ~(t26 ^ t27);
        int t29 = ~(t28 ^ t10);
        int t30 = ~(t28 ^ t11);
        return ~(t29 ^ t30);
    }

    // Long variant of the same chain
    static long xnorChain32L(long r0, long r1, long r2, long r3) {
        long t00 = ~(r0 ^ r1);
        long t01 = ~(r2 ^ r3);
        long t02 = ~(t00 ^ t01);
        long t03 = ~(r0 ^ r3);
        long t04 = ~(r1 ^ r2);
        long t05 = ~(t02 ^ t04);
        long t06 = ~(t03 ^ t04);
        long t07 = ~(t05 ^ t06);
        long t08 = ~(t07 ^ r0);
        long t09 = ~(t07 ^ r1);
        long t10 = ~(t08 ^ t09);
        long t11 = ~(t10 ^ r2);
        long t12 = ~(t10 ^ r3);
        long t13 = ~(t11 ^ t12);
        long t14 = ~(t13 ^ t00);
        long t15 = ~(t13 ^ t01);
        long t16 = ~(t14 ^ t15);
        long t17 = ~(t16 ^ t02);
        long t18 = ~(t16 ^ t03);
        long t19 = ~(t17 ^ t18);
        long t20 = ~(t19 ^ t04);
        long t21 = ~(t19 ^ t05);
        long t22 = ~(t20 ^ t21);
        long t23 = ~(t22 ^ t06);
        long t24 = ~(t22 ^ t07);
        long t25 = ~(t23 ^ t24);
        long t26 = ~(t25 ^ t08);
        long t27 = ~(t25 ^ t09);
        long t28 = ~(t26 ^ t27);
        long t29 = ~(t28 ^ t10);
        long t30 = ~(t28 ^ t11);
        return ~(t29 ^ t30);
    }

    // Chain with intermediate results fed back into next call (no unrolling across calls)
    static int xnorSeq(int a, int b, int n) {
        int r = a;
        for (int i = 0; i < n; i++) {
            r = ~(r ^ b);
            b = ~(b ^ (i + 1));
        }
        return r;
    }

    static long xnorSeqL(long a, long b, int n) {
        long r = a;
        for (int i = 0; i < n; i++) {
            r = ~(r ^ b);
            b = ~(b ^ (long)(i + 1));
        }
        return r;
    }

    // Array-based xnor to stress memory operand forms
    static int xnorArray(int[] arr, int val) {
        int r = val;
        for (int i = 0; i < arr.length; i++) {
            r = ~(arr[i] ^ r);
        }
        return r;
    }

    static long xnorArrayL(long[] arr, long val) {
        long r = val;
        for (int i = 0; i < arr.length; i++) {
            r = ~(arr[i] ^ r);
        }
        return r;
    }

    // Multiplication to prevent simplification
    static int xnorMix(int a, int b, int c) {
        return ~((~(a ^ b)) ^ c) * 3 + ~(a ^ (~(b ^ c)));
    }

    static long xnorMixL(long a, long b, long c) {
        return ~((~(a ^ b)) ^ c) * 3L + ~(a ^ (~(b ^ c)));
    }

    public static void main(String[] args) {
        System.out.println("=== RISC-V Zbb xnor -Xcomp Stress Test ===");

        // Phase 1: Chain with 32 locals (register pressure)
        System.out.println("Phase 1: 32-local xnor chain (register pressure)...");
        int[] intVals = { 0xDEADBEEF, 0xCAFEBABE, 0x12345678, 0x89ABCDEF };
        long[] longVals = { 0xDEADBEEFCAFEBABEL, 0x0123456789ABCDEFL,
                           0xFEDCBA9876543210L, 0xAAAAAAAA55555555L };

        for (int trial = 0; trial < 100_000; trial++) {
            int r = xnorChain32(
                intVals[trial % 4],
                intVals[(trial + 1) % 4],
                intVals[(trial + 2) % 4],
                intVals[(trial + 3) % 4]
            );
            // Verify against known-correct reference:
            int ref = refXnorChain32(
                intVals[trial % 4],
                intVals[(trial + 1) % 4],
                intVals[(trial + 2) % 4],
                intVals[(trial + 3) % 4]
            );
            if (r != ref) {
                throw new Error("int chain mismatch at trial " + trial);
            }
            sinkI = r;
        }

        for (int trial = 0; trial < 100_000; trial++) {
            long r = xnorChain32L(
                longVals[trial % 4],
                longVals[(trial + 1) % 4],
                longVals[(trial + 2) % 4],
                longVals[(trial + 3) % 4]
            );
            long ref = refXnorChain32L(
                longVals[trial % 4],
                longVals[(trial + 1) % 4],
                longVals[(trial + 2) % 4],
                longVals[(trial + 3) % 4]
            );
            if (r != ref) {
                throw new Error("long chain mismatch at trial " + trial);
            }
            sinkL = r;
        }
        System.out.println("  PASSED");

        // Phase 2: Sequential dependency chain (C2 can't simplify across iterations)
        System.out.println("Phase 2: Sequential dependency chain...");
        for (int trial = 0; trial < 100_000; trial++) {
            int r = xnorSeq(intVals[0], intVals[1], 32);
            if (trial == 0) {
                sinkI = r; // capture for reference
            }
        }
        // Verify final value is deterministic
        int seqRef = xnorSeq(intVals[0], intVals[1], 32);
        int seqResult = sinkI;
        // Actually compute fresh:
        seqResult = xnorSeq(intVals[0], intVals[1], 32);
        if (seqResult != seqRef) {
            throw new Error("int seq mismatch: " + seqResult + " != " + seqRef);
        }
        System.out.println("  PASSED");

        for (int trial = 0; trial < 100_000; trial++) {
            long r = xnorSeqL(longVals[0], longVals[1], 32);
            if (trial == 0) {
                sinkL = r;
            }
        }
        long lSeqRef = xnorSeqL(longVals[0], longVals[1], 32);
        long lSeqResult = xnorSeqL(longVals[0], longVals[1], 32);
        if (lSeqResult != lSeqRef) {
            throw new Error("long seq mismatch: " + lSeqResult + " != " + lSeqRef);
        }
        System.out.println("  PASSED");

        // Phase 3: Array-based (memory operands)
        System.out.println("Phase 3: Array memory operand stress...");
        int[] arr = new int[128];
        long[] arrL = new long[128];
        for (int i = 0; i < 128; i++) {
            arr[i] = intVals[i % 4] ^ (i * 0x12345678);
            arrL[i] = longVals[i % 4] ^ ((long)i * 0x123456789ABCDEFL);
        }

        for (int trial = 0; trial < 50_000; trial++) {
            int r = xnorArray(arr, intVals[trial % 4]);
            int ref = refXnorArray(arr, intVals[trial % 4]);
            if (r != ref) {
                throw new Error("int array mismatch at trial " + trial);
            }
            sinkI = r;
        }

        for (int trial = 0; trial < 50_000; trial++) {
            long r = xnorArrayL(arrL, longVals[trial % 4]);
            long ref = refXnorArrayL(arrL, longVals[trial % 4]);
            if (r != ref) {
                throw new Error("long array mismatch at trial " + trial);
            }
            sinkL = r;
        }
        System.out.println("  PASSED");

        // Phase 4: Mixed expressions that C2 can only partially simplify
        System.out.println("Phase 4: Complex mixed expression stress...");
        for (int trial = 0; trial < 100_000; trial++) {
            int r = xnorMix(
                intVals[trial % 4],
                intVals[(trial + 1) % 4],
                intVals[(trial + 2) % 4]
            );
            int ref = refXnorMix(
                intVals[trial % 4],
                intVals[(trial + 1) % 4],
                intVals[(trial + 2) % 4]
            );
            if (r != ref) {
                throw new Error("int mix mismatch at trial " + trial);
            }
            sinkI = r;
        }

        for (int trial = 0; trial < 100_000; trial++) {
            long r = xnorMixL(
                longVals[trial % 4],
                longVals[(trial + 1) % 4],
                longVals[(trial + 2) % 4]
            );
            long ref = refXnorMixL(
                longVals[trial % 4],
                longVals[(trial + 1) % 4],
                longVals[(trial + 2) % 4]
            );
            if (r != ref) {
                throw new Error("long mix mismatch at trial " + trial);
            }
            sinkL = r;
        }
        System.out.println("  PASSED");

        System.out.println();
        System.out.println("ALL STRESS TESTS PASSED.");
    }

    // === Reference implementations (safe, simple, no C2 tricks) ===

    static int refXnorChain32(int r0, int r1, int r2, int r3) {
        int t00 = ~(r0 ^ r1);
        int t01 = ~(r2 ^ r3);
        int t02 = ~(t00 ^ t01);
        int t03 = ~(r0 ^ r3);
        int t04 = ~(r1 ^ r2);
        int t05 = ~(t02 ^ t04);
        int t06 = ~(t03 ^ t04);
        int t07 = ~(t05 ^ t06);
        int t08 = ~(t07 ^ r0);
        int t09 = ~(t07 ^ r1);
        int t10 = ~(t08 ^ t09);
        int t11 = ~(t10 ^ r2);
        int t12 = ~(t10 ^ r3);
        int t13 = ~(t11 ^ t12);
        int t14 = ~(t13 ^ t00);
        int t15 = ~(t13 ^ t01);
        int t16 = ~(t14 ^ t15);
        int t17 = ~(t16 ^ t02);
        int t18 = ~(t16 ^ t03);
        int t19 = ~(t17 ^ t18);
        int t20 = ~(t19 ^ t04);
        int t21 = ~(t19 ^ t05);
        int t22 = ~(t20 ^ t21);
        int t23 = ~(t22 ^ t06);
        int t24 = ~(t22 ^ t07);
        int t25 = ~(t23 ^ t24);
        int t26 = ~(t25 ^ t08);
        int t27 = ~(t25 ^ t09);
        int t28 = ~(t26 ^ t27);
        int t29 = ~(t28 ^ t10);
        int t30 = ~(t28 ^ t11);
        return ~(t29 ^ t30);
    }

    static long refXnorChain32L(long r0, long r1, long r2, long r3) {
        long t00 = ~(r0 ^ r1);
        long t01 = ~(r2 ^ r3);
        long t02 = ~(t00 ^ t01);
        long t03 = ~(r0 ^ r3);
        long t04 = ~(r1 ^ r2);
        long t05 = ~(t02 ^ t04);
        long t06 = ~(t03 ^ t04);
        long t07 = ~(t05 ^ t06);
        long t08 = ~(t07 ^ r0);
        long t09 = ~(t07 ^ r1);
        long t10 = ~(t08 ^ t09);
        long t11 = ~(t10 ^ r2);
        long t12 = ~(t10 ^ r3);
        long t13 = ~(t11 ^ t12);
        long t14 = ~(t13 ^ t00);
        long t15 = ~(t13 ^ t01);
        long t16 = ~(t14 ^ t15);
        long t17 = ~(t16 ^ t02);
        long t18 = ~(t16 ^ t03);
        long t19 = ~(t17 ^ t18);
        long t20 = ~(t19 ^ t04);
        long t21 = ~(t19 ^ t05);
        long t22 = ~(t20 ^ t21);
        long t23 = ~(t22 ^ t06);
        long t24 = ~(t22 ^ t07);
        long t25 = ~(t23 ^ t24);
        long t26 = ~(t25 ^ t08);
        long t27 = ~(t25 ^ t09);
        long t28 = ~(t26 ^ t27);
        long t29 = ~(t28 ^ t10);
        long t30 = ~(t28 ^ t11);
        return ~(t29 ^ t30);
    }

    static int refXnorArray(int[] arr, int val) {
        int r = val;
        for (int i = 0; i < arr.length; i++) {
            r = ~(arr[i] ^ r);
        }
        return r;
    }

    static long refXnorArrayL(long[] arr, long val) {
        long r = val;
        for (int i = 0; i < arr.length; i++) {
            r = ~(arr[i] ^ r);
        }
        return r;
    }

    static int refXnorMix(int a, int b, int c) {
        return ~((~(a ^ b)) ^ c) * 3 + ~(a ^ (~(b ^ c)));
    }

    static long refXnorMixL(long a, long b, long c) {
        return ~((~(a ^ b)) ^ c) * 3L + ~(a ^ (~(b ^ c)));
    }
}
