/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8359603
 * @summary Redundant ConvX2Y->ConvY2X->ConvX2Y sequences should be
 *          simplified to a single ConvX2Y operation when applicable
 *          VerifyIterativeGVN checks that this optimization was applied
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions
 *      -XX:CompileCommand=compileonly,compiler.c2.TestEliminateRedundantConversionSequences::test*
 *      -XX:-TieredCompilation -Xbatch -XX:VerifyIterativeGVN=1110 compiler.c2.TestEliminateRedundantConversionSequences
 * @run main compiler.c2.TestEliminateRedundantConversionSequences
 *
 */

package compiler.c2;

public class TestEliminateRedundantConversionSequences {
    static long instanceCountD2L;
    static int instanceCoundF2I;
    static long instanceCountF2L;
    static float instanceCountI2F;

    // ConvD2L->ConvL2D->ConvD2L
    static void testD2L(double d) {
        int i = 1;
        int j = 1;
        while (++i < 3) {
            for (; 8 > j; ++j) {
                instanceCountD2L = (long)d;
                d = instanceCountD2L;
            }
        }
    }

    // ConvF2I->ConvI2F->ConvF2I
    static void testF2I(float d) {
        int i = 1;
        int j = 1;
        while (++i < 3) {
            for (; 8 > j; ++j) {
                instanceCoundF2I = (int)d;
                d = instanceCoundF2I;
            }
        }
    }

    // ConvF2L->ConvL2F->ConvF2L
    static void testF2L(float d) {
        int i = 1;
        int j = 1;
        while (++i < 3) {
            for (; 8 > j; ++j) {
                instanceCountF2L = (long)d;
                d = instanceCountF2L;
            }
        }
    }

    // ConvI2F->ConvF2I->ConvI2F
    static void testI2F(int d) {
        int i = 1;
        int j = 1;
        while (++i < 3) {
            for (; 8 > j; ++j) {
                instanceCountI2F = d;
                d = (int)instanceCountI2F;
            }
        }
    }

    public static void main(String[] strArr) {
        for (int i = 0; i < 1550; ++i) {
            testD2L(1);
        }
        for (int i = 0; i < 1550; ++i) {
            testF2I(1);
        }
        for (int i = 0; i < 1550; ++i) {
            testF2L(1);
        }
        for (int i = 0; i < 1550; ++i) {
            testI2F(1);
        }
    }
}