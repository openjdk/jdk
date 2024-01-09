/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2.cr7200264;

import compiler.lib.ir_framework.*;

/*
 * Based on test/compiler/6340864/TestIntVect.java.
 */
public class TestIntVect {

    private static final int ARRLEN = 997;
    private static final int ADD_INIT = Integer.MAX_VALUE-500;
    private static final int BIT_MASK = 0xEC80F731;
    private static final int VALUE = 15;
    private static final int SHIFT = 32;

    private static int[] a0 = new int[ARRLEN];
    private static int[] a1 = new int[ARRLEN];
    private static int[] a2 = new int[ARRLEN];
    private static int[] a3 = new int[ARRLEN];
    private static int[] a4 = new int[ARRLEN];
    private static long[] p2 = new long[ARRLEN/2];

    // Initialize
    static{
        for (int i=0; i<ARRLEN; i++) {
            int val = (int)(ADD_INIT+i);
            a1[i] = val;
            a2[i] = (int)VALUE;
            a3[i] = (int)-VALUE;
            a4[i] = (int)BIT_MASK;
        }
    }

    @ForceInline
    public static void testInner() {
            test_sum(a0, a1);
            test_addc(a0, a1);
            test_addv(a0, a1, (int)VALUE);
            test_adda(a0, a1, a2);
            test_subc(a0, a1);
            test_subv(a0, a1, (int)VALUE);
            test_suba(a0, a1, a2);
            test_mulc(a0, a1);
            test_mulv(a0, a1, (int)VALUE);
            test_mula(a0, a1, a2);
            test_divc(a0, a1);
            test_divv(a0, a1, (int)VALUE);
            test_diva(a0, a1, a2);
            test_mulc_n(a0, a1);
            test_mulv(a0, a1, (int)-VALUE);
            test_mula(a0, a1, a3);
            test_divc_n(a0, a1);
            test_divv(a0, a1, (int)-VALUE);
            test_diva(a0, a1, a3);
            test_andc(a0, a1);
            test_andv(a0, a1, (int)BIT_MASK);
            test_anda(a0, a1, a4);
            test_orc(a0, a1);
            test_orv(a0, a1, (int)BIT_MASK);
            test_ora(a0, a1, a4);
            test_xorc(a0, a1);
            test_xorv(a0, a1, (int)BIT_MASK);
            test_xora(a0, a1, a4);
            test_sllc(a0, a1);
            test_sllv(a0, a1, VALUE);
            test_srlc(a0, a1);
            test_srlv(a0, a1, VALUE);
            test_srac(a0, a1);
            test_srav(a0, a1, VALUE);
            test_sllc_n(a0, a1);
            test_sllv(a0, a1, -VALUE);
            test_srlc_n(a0, a1);
            test_srlv(a0, a1, -VALUE);
            test_srac_n(a0, a1);
            test_srav(a0, a1, -VALUE);
            test_sllc_o(a0, a1);
            test_sllv(a0, a1, SHIFT);
            test_srlc_o(a0, a1);
            test_srlv(a0, a1, SHIFT);
            test_srac_o(a0, a1);
            test_srav(a0, a1, SHIFT);
            test_sllc_on(a0, a1);
            test_sllv(a0, a1, -SHIFT);
            test_srlc_on(a0, a1);
            test_srlv(a0, a1, -SHIFT);
            test_srac_on(a0, a1);
            test_srav(a0, a1, -SHIFT);
            test_pack2(p2, a1);
            test_unpack2(a0, p2);
            test_pack2_swap(p2, a1);
            test_unpack2_swap(a0, p2);
    }

    @ForceInline
    static void test_sum(int[] a0, int[] a1) {
        a0[0] = 0;
        for (int i = 0; i < a1.length; i+=1) {
            a0[0] += a1[i];
        }
    }

    @ForceInline
    static void test_addc(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]+VALUE);
        }
    }
    @ForceInline
    static void test_addv(int[] a0, int[] a1, int b) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]+b);
        }
    }
    @ForceInline
    static void test_adda(int[] a0, int[] a1, int[] a2) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]+a2[i]);
        }
    }

    @ForceInline
    static void test_subc(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]-VALUE);
        }
    }
    @ForceInline
    static void test_subv(int[] a0, int[] a1, int b) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]-b);
        }
    }
    @ForceInline
    static void test_suba(int[] a0, int[] a1, int[] a2) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]-a2[i]);
        }
    }

    @ForceInline
    static void test_mulc(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]*VALUE);
        }
    }
    @ForceInline
    static void test_mulc_n(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]*(-VALUE));
        }
    }
    @ForceInline
    static void test_mulv(int[] a0, int[] a1, int b) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]*b);
        }
    }
    @ForceInline
    static void test_mula(int[] a0, int[] a1, int[] a2) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]*a2[i]);
        }
    }

    @ForceInline
    static void test_divc(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]/VALUE);
        }
    }
    @ForceInline
    static void test_divc_n(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]/(-VALUE));
        }
    }
    @ForceInline
    static void test_divv(int[] a0, int[] a1, int b) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]/b);
        }
    }
    @ForceInline
    static void test_diva(int[] a0, int[] a1, int[] a2) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]/a2[i]);
        }
    }

    @ForceInline
    static void test_andc(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]&BIT_MASK);
        }
    }
    @ForceInline
    static void test_andv(int[] a0, int[] a1, int b) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]&b);
        }
    }
    @ForceInline
    static void test_anda(int[] a0, int[] a1, int[] a2) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]&a2[i]);
        }
    }

    @ForceInline
    static void test_orc(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]|BIT_MASK);
        }
    }
    @ForceInline
    static void test_orv(int[] a0, int[] a1, int b) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]|b);
        }
    }
    @ForceInline
    static void test_ora(int[] a0, int[] a1, int[] a2) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]|a2[i]);
        }
    }

    @ForceInline
    static void test_xorc(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]^BIT_MASK);
        }
    }
    @ForceInline
    static void test_xorv(int[] a0, int[] a1, int b) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]^b);
        }
    }
    @ForceInline
    static void test_xora(int[] a0, int[] a1, int[] a2) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]^a2[i]);
        }
    }

    @ForceInline
    static void test_sllc(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]<<VALUE);
        }
    }
    @ForceInline
    static void test_sllc_n(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]<<(-VALUE));
        }
    }
    @ForceInline
    static void test_sllc_o(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]<<SHIFT);
        }
    }
    @ForceInline
    static void test_sllc_on(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]<<(-SHIFT));
        }
    }
    @ForceInline
    static void test_sllv(int[] a0, int[] a1, int b) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]<<b);
        }
    }

    @ForceInline
    static void test_srlc(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]>>>VALUE);
        }
    }
    @ForceInline
    static void test_srlc_n(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]>>>(-VALUE));
        }
    }
    @ForceInline
    static void test_srlc_o(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]>>>SHIFT);
        }
    }
    @ForceInline
    static void test_srlc_on(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]>>>(-SHIFT));
        }
    }
    @ForceInline
    static void test_srlv(int[] a0, int[] a1, int b) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]>>>b);
        }
    }

    @ForceInline
    static void test_srac(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]>>VALUE);
        }
    }
    @ForceInline
    static void test_srac_n(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]>>(-VALUE));
        }
    }
    @ForceInline
    static void test_srac_o(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]>>SHIFT);
        }
    }
    @ForceInline
    static void test_srac_on(int[] a0, int[] a1) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]>>(-SHIFT));
        }
    }
    @ForceInline
    static void test_srav(int[] a0, int[] a1, int b) {
        for (int i = 0; i < a0.length; i+=1) {
            a0[i] = (int)(a1[i]>>b);
        }
    }

    @ForceInline
    static void test_pack2(long[] p2, int[] a1) {
        if (p2.length*2 > a1.length) return;
        for (int i = 0; i < p2.length; i+=1) {
            long l0 = (long)a1[i*2+0];
            long l1 = (long)a1[i*2+1];
            p2[i] = (l1 << 32) | (l0 & 0xFFFFFFFFl);
        }
    }
    @ForceInline
    static void test_unpack2(int[] a0, long[] p2) {
        if (p2.length*2 > a0.length) return;
        for (int i = 0; i < p2.length; i+=1) {
            long l = p2[i];
            a0[i*2+0] = (int)(l & 0xFFFFFFFFl);
            a0[i*2+1] = (int)(l >> 32);
        }
    }
    @ForceInline
    static void test_pack2_swap(long[] p2, int[] a1) {
        if (p2.length*2 > a1.length) return;
        for (int i = 0; i < p2.length; i+=1) {
            long l0 = (long)a1[i*2+0];
            long l1 = (long)a1[i*2+1];
            p2[i] = (l0 << 32) | (l1 & 0xFFFFFFFFl);
        }
    }
    @ForceInline
    static void test_unpack2_swap(int[] a0, long[] p2) {
        if (p2.length*2 > a0.length) return;
        for (int i = 0; i < p2.length; i+=1) {
            long l = p2[i];
            a0[i*2+0] = (int)(l >> 32);
            a0[i*2+1] = (int)(l & 0xFFFFFFFFl);
        }
    }
}
