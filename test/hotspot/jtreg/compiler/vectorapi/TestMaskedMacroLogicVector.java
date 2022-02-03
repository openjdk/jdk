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
 * @bug 8273322
 * @key randomness
 * @summary Enhance macro logic optimization for masked logic operations.
 * @modules jdk.incubator.vector
 * @requires vm.compiler2.enabled
 * @requires os.simpleArch == "x64"
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestMaskedMacroLogicVector
 */

package compiler.vectorapi;

import java.util.concurrent.Callable;
import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.driver.IRViolationException;
import jdk.test.lib.Asserts;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import java.util.Random;

import jdk.incubator.vector.*;

public class TestMaskedMacroLogicVector {
    boolean [] br;
    boolean [] ba;
    boolean [] bb;

    short [] sr;
    char  [] ca;
    char  [] cb;

    int [] r;
    int [] a;
    int [] b;
    int [] c;
    int [] d;
    int [] e;
    int [] f;

    long [] rl;
    long [] al;
    long [] bl;
    long [] cl;

    boolean [] mask;

    static boolean booleanFunc1(boolean a, boolean b) {
        return a & b;
    }

    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV" , " > 0 "})
    public void testSubWordBoolean(boolean[] r, boolean[] a, boolean[] b) {
        for (int i = 0; i < r.length; i++) {
            r[i] = booleanFunc1(a[i], b[i]);
        }
    }
    public void verifySubWordBoolean(boolean[] r, boolean[] a, boolean[] b) {
        for (int i = 0; i < r.length; i++) {
            boolean expected = booleanFunc1(a[i], b[i]);
            if (r[i] != expected) {
                throw new AssertionError(
                        String.format("at #%d: r=%b, expected = %b = booleanFunc1(%b,%b)",
                                      i, r[i], expected, a[i], b[i]));
            }
        }
    }


    static short charFunc1(char a, char b) {
        return (short)((a & b) & 1);
    }

    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV" , " > 0 "})
    public void testSubWordChar(short[] r, char[] a, char[] b) {
        for (int i = 0; i < r.length; i++) {
            r[i] = charFunc1(a[i], b[i]);
        }
    }
    public void verifySubWordChar(short[] r, char[] a, char[] b) {
        for (int i = 0; i < r.length; i++) {
            short expected = charFunc1(a[i], b[i]);
            if (r[i] != expected) {
                throw new AssertionError(
                        String.format("testSubWordChar: at #%d: r=%d, expected = %d = booleanFunc1(%d,%d)",
                                      i, r[i], expected, (int)a[i], (int)b[i]));
            }
        }
    }

    // Case 1): Unmasked expression tree.
    //        P_LOP
    //   L_LOP     R_LOP

    static int intFunc1(int a, int b, int c) {
        return (a & b) ^ (a & c);
    }

    @ForceInline
    public void testInt1Kernel(VectorSpecies SPECIES, int [] r, int [] a, int [] b, int [] c) {
        for (int i = 0; i < SPECIES.loopBound(r.length); i += SPECIES.length()) {
            IntVector va = IntVector.fromArray(SPECIES, a, i);
            IntVector vb = IntVector.fromArray(SPECIES, b, i);
            IntVector vc = IntVector.fromArray(SPECIES, c, i);
            va.lanewise(VectorOperators.AND, vc)
            .lanewise(VectorOperators.XOR, va.lanewise(VectorOperators.AND, vb))
            .intoArray(r, i);
        }
    }

    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt1_Int128(int[] r, int[] a, int[] b, int[] c) {
        testInt1Kernel(IntVector.SPECIES_128, r, a, b, c);
    }

    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt1_Int256(int[] r, int[] a, int[] b, int[] c) {
        testInt1Kernel(IntVector.SPECIES_256, r, a, b, c);
    }

    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt1_Int512(int[] r, int[] a, int[] b, int[] c) {
        testInt1Kernel(IntVector.SPECIES_512, r, a, b, c);
    }

    public void verifyInt1(int[] r, int[] a, int[] b, int[] c) {
        for (int i = 0; i < r.length; i++) {
            int expected = intFunc1(a[i], b[i], c[i]);
            if (r[i] != expected) {
                throw new AssertionError(String.format("testInt1: at #%d: r=%d, expected = %d = intFunc1(%d,%d,%d)",
                                                       i, r[i], expected, a[i], b[i], c[i]));
            }
        }
    }

    // Case 2): Only right child is masked.
    //        P_LOP
    //   L_LOP    R_LOP(mask)

    static int intFunc2(int a, int b, int c, boolean mask) {
        return (a & b) ^ (mask == true ? a & c : a);
    }

    @ForceInline
    public void testInt2Kernel(VectorSpecies SPECIES, int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < SPECIES.loopBound(r.length); i += SPECIES.length()) {
            VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask , i);
            IntVector va = IntVector.fromArray(SPECIES, a, i);
            IntVector vb = IntVector.fromArray(SPECIES, b, i);
            IntVector vc = IntVector.fromArray(SPECIES, c, i);
            va.lanewise(VectorOperators.AND, vb)
            .lanewise(VectorOperators.XOR,
                      va.lanewise(VectorOperators.AND, vc, vmask))
           .intoArray(r, i);
        }
    }

    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt2_Int128(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt2Kernel(IntVector.SPECIES_128, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt2_Int256(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt2Kernel(IntVector.SPECIES_256, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt2_Int512(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt2Kernel(IntVector.SPECIES_512, r, a, b, c, mask);
    }

    public void verifyInt2(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < r.length; i++) {
            int expected = intFunc2(a[i], b[i], c[i], mask[i]);
            if (r[i] != expected) {
                throw new AssertionError(String.format("testInt2: at #%d: r=%d, expected = %d = intFunc2(%d,%d,%d,%b)",
                                                       i, r[i], expected, a[i], b[i], c[i], mask[i]));
            }
        }
    }

    // Case 3): Only left child is masked.
    //             P_LOP
    //   L_LOP(mask)    R_LOP

    static int intFunc3(int a, int b, int c, boolean mask) {
        return (mask == true ? a & b : a) ^ (a & c);
    }

    @ForceInline
    public void testInt3Kernel(VectorSpecies SPECIES, int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < SPECIES.loopBound(r.length); i += SPECIES.length()) {
            VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask , i);
            IntVector va = IntVector.fromArray(SPECIES, a, i);
            IntVector vb = IntVector.fromArray(SPECIES, b, i);
            IntVector vc = IntVector.fromArray(SPECIES, c, i);
            va.lanewise(VectorOperators.AND, vb, vmask)
            .lanewise(VectorOperators.XOR,
                      va.lanewise(VectorOperators.AND, vc))
           .intoArray(r, i);
        }
    }

    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt3_Int128(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt3Kernel(IntVector.SPECIES_128, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt3_Int256(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt3Kernel(IntVector.SPECIES_256, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt3_Int512(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt3Kernel(IntVector.SPECIES_512, r, a, b, c, mask);
    }


    @ForceInline
    public void verifyInt3(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < r.length; i++) {
            int expected = intFunc3(a[i], b[i], c[i], mask[i]);
            if (r[i] != expected) {
                throw new AssertionError(String.format("testInt3: at #%d: r=%d, expected = %d = intFunc3(%d,%d,%d,%b)",
                                                       i, r[i], expected, a[i], b[i], c[i], mask[i]));
            }
        }
    }

    // Case 4): Both child nodes are masked.
    //             P_LOP
    //   L_LOP(mask)    R_LOP(mask)

    static int intFunc4(int a, int b, int c, boolean mask) {
        return (mask == true ? b & a : b) ^ (mask == true ? c & a : c);
    }

    @ForceInline
    public void testInt4Kernel(VectorSpecies SPECIES, int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < SPECIES.loopBound(r.length); i += SPECIES.length()) {
            VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask , i);
            IntVector va = IntVector.fromArray(SPECIES, a, i);
            IntVector vb = IntVector.fromArray(SPECIES, b, i);
            IntVector vc = IntVector.fromArray(SPECIES, c, i);
            vb.lanewise(VectorOperators.AND, va, vmask)
            .lanewise(VectorOperators.XOR,
                      vc.lanewise(VectorOperators.AND, va, vmask))
           .intoArray(r, i);
        }
    }

    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"AndV", " > 0 ", "XorV", " > 0 "})
    public void testInt4_Int128(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt4Kernel(IntVector.SPECIES_128, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"AndV", " > 0 ", "XorV", " > 0 "})
    public void testInt4_Int256(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt4Kernel(IntVector.SPECIES_256, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"AndV", " > 0 ", "XorV", " > 0 "})
    public void testInt4_Int512(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt4Kernel(IntVector.SPECIES_512, r, a, b, c, mask);
    }

    public void verifyInt4(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < r.length; i++) {
            int expected = intFunc4(a[i], b[i], c[i], mask[i]);
            if (r[i] != expected) {
                throw new AssertionError(String.format("testInt4: at #%d: r=%d, expected = %d = intFunc4(%d,%d,%d,%b)",
                                                       i, r[i], expected, a[i], b[i], c[i], mask[i]));
            }
        }
    }

    // Case 5): Parent is masked with unmasked child expressions.
    //        P_LOP(mask)
    //   L_LOP     R_LOP

    static int intFunc5(int a, int b, int c, boolean mask) {
        return mask == true ? ((a & b) ^ (a & c)) : (a & b);
    }

    @ForceInline
    public void testInt5Kernel(VectorSpecies SPECIES, int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < SPECIES.loopBound(r.length); i += SPECIES.length()) {
            VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask , i);
            IntVector va = IntVector.fromArray(SPECIES, a, i);
            IntVector vb = IntVector.fromArray(SPECIES, b, i);
            IntVector vc = IntVector.fromArray(SPECIES, c, i);
            va.lanewise(VectorOperators.AND, vb)
            .lanewise(VectorOperators.XOR,
                      va.lanewise(VectorOperators.AND, vc), vmask)
           .intoArray(r, i);
        }
    }

    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt5_Int128(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt5Kernel(IntVector.SPECIES_128, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt5_Int256(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt5Kernel(IntVector.SPECIES_256, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt5_Int512(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt5Kernel(IntVector.SPECIES_512, r, a, b, c, mask);
    }

    @ForceInline
    public void verifyInt5(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < r.length; i++) {
            int expected = intFunc5(a[i], b[i], c[i], mask[i]);
            if (r[i] != expected) {
                throw new AssertionError(String.format("testInt5: at #%d: r=%d, expected = %d = intFunc5(%d,%d,%d,%b)",
                                                       i, r[i], expected, a[i], b[i], c[i], mask[i]));
            }
        }
    }

    // Case 6): Parent and right child are masked.
    //        P_LOP(mask)
    //   L_LOP     R_LOP(mask)

    static int intFunc6(int a, int b, int c, boolean mask) {
        return mask == true ? ((a & b) ^ (mask == true ? a & c : a)) : (a & b);
    }

    @ForceInline
    public void testInt6Kernel(VectorSpecies SPECIES, int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < SPECIES.loopBound(r.length); i += SPECIES.length()) {
            VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask , i);
            IntVector va = IntVector.fromArray(SPECIES, a, i);
            IntVector vb = IntVector.fromArray(SPECIES, b, i);
            IntVector vc = IntVector.fromArray(SPECIES, c, i);
            va.lanewise(VectorOperators.AND, vb)
            .lanewise(VectorOperators.XOR,
                      va.lanewise(VectorOperators.AND, vc, vmask), vmask)
           .intoArray(r, i);
        }
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt6_Int128(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt6Kernel(IntVector.SPECIES_128, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt6_Int256(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt6Kernel(IntVector.SPECIES_256, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt6_Int512(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt6Kernel(IntVector.SPECIES_512, r, a, b, c, mask);
    }


    public void verifyInt6(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < r.length; i++) {
            int expected = intFunc6(a[i], b[i], c[i], mask[i]);
            if (r[i] != expected) {
                throw new AssertionError(String.format("testInt6: at #%d: r=%d, expected = %d = intFunc6(%d,%d,%d,%b)",
                                                       i, r[i], expected, a[i], b[i], c[i], mask[i]));
            }
        }
    }

    // Case 7): Parent and left child are masked.
    //            P_LOP(mask)
    //   L_LOP(mask)       R_LOP

    static int intFunc7(int a, int b, int c, boolean mask) {
        return mask == true ? ((mask == true ? a & b : a) ^ (a & c)) : a;
    }

    @ForceInline
    public void testInt7Kernel(VectorSpecies SPECIES, int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < SPECIES.loopBound(r.length); i += SPECIES.length()) {
            VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask , i);
            IntVector va = IntVector.fromArray(SPECIES, a, i);
            IntVector vb = IntVector.fromArray(SPECIES, b, i);
            IntVector vc = IntVector.fromArray(SPECIES, c, i);
            va.lanewise(VectorOperators.AND, vb, vmask)
            .lanewise(VectorOperators.XOR,
                      va.lanewise(VectorOperators.AND, vc), vmask)
           .intoArray(r, i);
        }
    }

    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt7_Int128(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt7Kernel(IntVector.SPECIES_128, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt7_Int256(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt7Kernel(IntVector.SPECIES_256, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt7_Int512(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt7Kernel(IntVector.SPECIES_512, r, a, b, c, mask);
    }

    public void verifyInt7(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < r.length; i++) {
            int expected = intFunc7(a[i], b[i], c[i], mask[i]);
            if (r[i] != expected) {
                throw new AssertionError(String.format("testInt7: at #%d: r=%d, expected = %d = intFunc7(%d,%d,%d,%b)",
                                                       i, r[i], expected, a[i], b[i], c[i], mask[i]));
            }
        }
    }

    // Case 8): Parent and both child expressions are masked.
    //            P_LOP(mask)
    //   L_LOP(mask)       R_LOP (mask)

    static int intFunc8(int a, int b, int c, boolean mask) {
        return mask == true ? ((mask == true ? b & a : b) ^ (mask == true ? c & a  : c)) : b;
    }

    @ForceInline
    public void testInt8Kernel(VectorSpecies SPECIES, int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < SPECIES.loopBound(r.length); i += SPECIES.length()) {
            VectorMask<Integer> vmask = VectorMask.fromArray(SPECIES, mask , i);
            IntVector va = IntVector.fromArray(SPECIES, a, i);
            IntVector vb = IntVector.fromArray(SPECIES, b, i);
            IntVector vc = IntVector.fromArray(SPECIES, c, i);
            vb.lanewise(VectorOperators.AND, va, vmask)
            .lanewise(VectorOperators.XOR,
                      vc.lanewise(VectorOperators.AND, va, vmask), vmask)
           .intoArray(r, i);
        }
    }

    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt8_Int128(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt8Kernel(IntVector.SPECIES_128, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt8_Int256(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt8Kernel(IntVector.SPECIES_256, r, a, b, c, mask);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testInt8_Int512(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        testInt8Kernel(IntVector.SPECIES_512, r, a, b, c, mask);
    }

    public void verifyInt8(int[] r, int[] a, int[] b, int[] c, boolean [] mask) {
        for (int i = 0; i < r.length; i++) {
            int expected = intFunc8(a[i], b[i], c[i], mask[i]);
            if (r[i] != expected) {
                throw new AssertionError(String.format("testInt8: at #%d: r=%d, expected = %d = intFunc8(%d,%d,%d,%b)",
                                                       i, r[i], expected, a[i], b[i], c[i], mask[i]));
            }
        }
    }


    // ===================================================== //

    static long longFunc(long a, long b, long c) {
        long v1 = (a & b) ^ (a & c) ^ (b & c);
        long v2 = (~a & b) | (~b & c) | (~c & a);
        return v1 & v2;
    }

    @ForceInline
    public void testLongKernel(VectorSpecies SPECIES, long[] r, long[] a, long[] b, long[] c) {
        for (int i = 0; i < SPECIES.loopBound(r.length); i  +=  SPECIES.length()) {
            LongVector va = LongVector.fromArray(SPECIES, a, i);
            LongVector vb = LongVector.fromArray(SPECIES, b, i);
            LongVector vc = LongVector.fromArray(SPECIES, c, i);

            va.lanewise(VectorOperators.AND, vb)
            .lanewise(VectorOperators.XOR, va.lanewise(VectorOperators.AND, vc))
            .lanewise(VectorOperators.XOR, vb.lanewise(VectorOperators.AND, vc))
            .lanewise(VectorOperators.AND,
                       va.lanewise(VectorOperators.NOT).lanewise(VectorOperators.AND, vb)
                      .lanewise(VectorOperators.OR, vb.lanewise(VectorOperators.NOT).lanewise(VectorOperators.AND, vc))
                      .lanewise(VectorOperators.OR, vc.lanewise(VectorOperators.NOT).lanewise(VectorOperators.AND, va)))
            .intoArray(r, i);
        }
    }

    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testLong_Long256(long[] r, long[] a, long[] b, long[] c) {
        testLongKernel(LongVector.SPECIES_256, r, a, b, c);
    }
    @Test
    @IR(applyIf = {"UseAVX", "3"}, counts = {"MacroLogicV", " > 0 "})
    public void testLong_Long512(long[] r, long[] a, long[] b, long[] c) {
        testLongKernel(LongVector.SPECIES_512, r, a, b, c);
    }

    public void verifyLong(long[] r, long[] a, long[] b, long[] c) {
        for (int i = 0; i < r.length; i++) {
            long expected = longFunc(a[i], b[i], c[i]);
            if (r[i] != expected) {
                throw new AssertionError(
                        String.format("testLong: at #%d: r=%d, expected = %d = longFunc(%d,%d,%d)",
                                      i, r[i], expected, a[i], b[i], c[i]));
            }
        }
    }

    // ===================================================== //

    private static final Random R = Utils.getRandomInstance();

    static boolean[] fillBooleanRandom(Callable<boolean[]> factory) {
        try {
            boolean[] arr = factory.call();
            for (int i = 0; i < arr.length; i++) {
                arr[i] = R.nextBoolean();
            }
            return arr;
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }
    static char[] fillCharRandom(Callable<char[]> factory) {
        try {
            char[] arr = factory.call();
            for (int i = 0; i < arr.length; i++) {
                arr[i] = (char)R.nextInt();
            }
            return arr;
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }
    static int[] fillIntRandom(Callable<int[]> factory) {
        try {
            int[] arr = factory.call();
            for (int i = 0; i < arr.length; i++) {
                arr[i] = R.nextInt();
            }
            return arr;
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }
    static long[] fillLongRandom(Callable<long[]> factory) {
        try {
            long[] arr = factory.call();
            for (int i = 0; i < arr.length; i++) {
                arr[i] = R.nextLong();
            }
            return arr;
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    // ===================================================== //

    static final int SIZE = 512;

    @Run(test = {"testInt4_Int128"}, mode = RunMode.STANDALONE)
    public void kernel_testInt4_Int128() {
        for (int i = 0; i < 10000; i++) {
            testInt4_Int128(r, a, b, c, mask);
            verifyInt4(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt4_Int256"}, mode = RunMode.STANDALONE)
    public void kernel_testInt4_Int256() {
        for (int i = 0; i < 10000; i++) {
            testInt4_Int256(r, a, b, c, mask);
            verifyInt4(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt4_Int512"}, mode = RunMode.STANDALONE)
    public void kernel_testInt4_Int512() {
        for (int i = 0; i < 10000; i++) {
            testInt4_Int512(r, a, b, c, mask);
            verifyInt4(r, a, b, c, mask);
        }
    }

    @Run(test = {"testSubWordBoolean"}, mode = RunMode.STANDALONE)
    public void kernel_test_SubWordBoolean() {
        for (int i = 0; i < 10000; i++) {
            testSubWordBoolean(br, ba, bb);
            verifySubWordBoolean(br, ba, bb);
        }
    }

    @Run(test = {"testSubWordChar"}, mode = RunMode.STANDALONE)
    public void kernel_test_SubWordChar() {
        for (int i = 0; i < 10000; i++) {
            testSubWordChar(sr, ca, cb);
            verifySubWordChar(sr, ca, cb);
        }
    }

    @Run(test = {"testInt1_Int128"}, mode = RunMode.STANDALONE)
    public void kernel_testInt1_Int128() {
        for (int i = 0; i < 10000; i++) {
            testInt1_Int128(r, a, b, c);
            verifyInt1(r, a, b, c);
        }
    }
    @Run(test = {"testInt1_Int256"}, mode = RunMode.STANDALONE)
    public void kernel_testInt1_Int256() {
        for (int i = 0; i < 10000; i++) {
            testInt1_Int256(r, a, b, c);
            verifyInt1(r, a, b, c);
        }
    }
    @Run(test = {"testInt1_Int512"}, mode = RunMode.STANDALONE)
    public void kernel_testInt1_Int512() {
        for (int i = 0; i < 10000; i++) {
            testInt1_Int512(r, a, b, c);
            verifyInt1(r, a, b, c);
        }
    }

    @Run(test = {"testInt2_Int128"}, mode = RunMode.STANDALONE)
    public void kernel_testInt2_Int128() {
        for (int i = 0; i < 10000; i++) {
            testInt2_Int128(r, a, b, c, mask);
            verifyInt2(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt2_Int256"}, mode = RunMode.STANDALONE)
    public void kernel_testInt2_Int256() {
        for (int i = 0; i < 10000; i++) {
            testInt2_Int256(r, a, b, c, mask);
            verifyInt2(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt2_Int512"}, mode = RunMode.STANDALONE)
    public void kernel_testInt2_Int512() {
        for (int i = 0; i < 10000; i++) {
            testInt2_Int512(r, a, b, c, mask);
            verifyInt2(r, a, b, c, mask);
        }
    }

    @Run(test = {"testInt3_Int128"}, mode = RunMode.STANDALONE)
    public void kernel_testInt3_Int128() {
        for (int i = 0; i < 10000; i++) {
            testInt3_Int128(r, a, b, c, mask);
            verifyInt3(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt3_Int256"}, mode = RunMode.STANDALONE)
    public void kernel_testInt3_Int256() {
        for (int i = 0; i < 10000; i++) {
            testInt3_Int256(r, a, b, c, mask);
            verifyInt3(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt3_Int512"}, mode = RunMode.STANDALONE)
    public void kernel_testInt3_Int512() {
        for (int i = 0; i < 10000; i++) {
            testInt3_Int512(r, a, b, c, mask);
            verifyInt3(r, a, b, c, mask);
        }
    }

    @Run(test = {"testInt5_Int128"}, mode = RunMode.STANDALONE)
    public void kernel_testInt5_128() {
        for (int i = 0; i < 10000; i++) {
            testInt5_Int128(r, a, b, c, mask);
            verifyInt5(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt5_Int256"}, mode = RunMode.STANDALONE)
    public void kernel_testInt5_256() {
        for (int i = 0; i < 10000; i++) {
            testInt5_Int256(r, a, b, c, mask);
            verifyInt5(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt5_Int512"}, mode = RunMode.STANDALONE)
    public void kernel_testInt5_Int512() {
        for (int i = 0; i < 10000; i++) {
            testInt5_Int512(r, a, b, c, mask);
            verifyInt5(r, a, b, c, mask);
        }
    }

    @Run(test = {"testInt6_Int128"}, mode = RunMode.STANDALONE)
    public void kernel_testInt6_Int128() {
        for (int i = 0; i < 10000; i++) {
            testInt6_Int128(r, a, b, c, mask);
            verifyInt6(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt6_Int256"}, mode = RunMode.STANDALONE)
    public void kernel_testInt6_Int256() {
        for (int i = 0; i < 10000; i++) {
            testInt6_Int256(r, a, b, c, mask);
            verifyInt6(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt6_Int512"}, mode = RunMode.STANDALONE)
    public void kernel_testInt6_Int512() {
        for (int i = 0; i < 10000; i++) {
            testInt6_Int512(r, a, b, c, mask);
            verifyInt6(r, a, b, c, mask);
        }
    }

    @Run(test = {"testInt7_Int128"}, mode = RunMode.STANDALONE)
    public void kernel_testInt7_Int128() {
        for (int i = 0; i < 10000; i++) {
            testInt7_Int128(r, a, b, c, mask);
            verifyInt7(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt7_Int256"}, mode = RunMode.STANDALONE)
    public void kernel_testInt7_Int256() {
        for (int i = 0; i < 10000; i++) {
            testInt7_Int256(r, a, b, c, mask);
            verifyInt7(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt7_Int512"}, mode = RunMode.STANDALONE)
    public void kernel_testInt7_Int512() {
        for (int i = 0; i < 10000; i++) {
            testInt7_Int512(r, a, b, c, mask);
            verifyInt7(r, a, b, c, mask);
        }
    }

    @Run(test = {"testInt8_Int128"}, mode = RunMode.STANDALONE)
    public void kernel_testInt8_Int128() {
        for (int i = 0; i < 10000; i++) {
            testInt8_Int128(r, a, b, c, mask);
            verifyInt8(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt8_Int256"}, mode = RunMode.STANDALONE)
    public void kernel_testInt8_Int256() {
        for (int i = 0; i < 10000; i++) {
            testInt8_Int256(r, a, b, c, mask);
            verifyInt8(r, a, b, c, mask);
        }
    }
    @Run(test = {"testInt8_Int512"}, mode = RunMode.STANDALONE)
    public void kernel_testInt8_Int512() {
        for (int i = 0; i < 10000; i++) {
            testInt8_Int512(r, a, b, c, mask);
            verifyInt8(r, a, b, c, mask);
        }
    }

    @Run(test = {"testLong_Long256"}, mode = RunMode.STANDALONE)
    public void kernel_testLong_Long256() {
        for (int i = 0; i < 10000; i++) {
            testLong_Long256(rl, al, bl, cl);
            verifyLong(rl, al, bl, cl);
        }
    }
    @Run(test = {"testLong_Long512"}, mode = RunMode.STANDALONE)
    public void kernel_testLong_Long512() {
        for (int i = 0; i < 10000; i++) {
            testLong_Long512(rl, al, bl, cl);
            verifyLong(rl, al, bl, cl);
        }
    }

    public TestMaskedMacroLogicVector() {
        br = new boolean[SIZE];
        ba = fillBooleanRandom((()-> new boolean[SIZE]));
        bb = fillBooleanRandom((()-> new boolean[SIZE]));

        sr = new short[SIZE];
        ca = fillCharRandom((()-> new char[SIZE]));
        cb = fillCharRandom((()-> new char[SIZE]));

        r = new int[SIZE];
        a = fillIntRandom(()-> new int[SIZE]);
        b = fillIntRandom(()-> new int[SIZE]);
        c = fillIntRandom(()-> new int[SIZE]);
        d = fillIntRandom(()-> new int[SIZE]);
        e = fillIntRandom(()-> new int[SIZE]);
        f = fillIntRandom(()-> new int[SIZE]);

        rl = new long[SIZE];
        al = fillLongRandom(() -> new long[SIZE]);
        bl = fillLongRandom(() -> new long[SIZE]);
        cl = fillLongRandom(() -> new long[SIZE]);

        mask = fillBooleanRandom((()-> new boolean[SIZE]));
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-TieredCompilation",
                                   "-XX:UseAVX=3",
                                   "--add-modules=jdk.incubator.vector",
                                   "-XX:CompileThresholdScaling=0.3");
    }
}
