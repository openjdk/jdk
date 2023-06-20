/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8301489
 * @summary ShortLoopOptimizer might lift instructions before their inputs
 * @requires vm.compiler1.enabled
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1
 *                   -XX:CompileOnly=compiler.c1.Test8301489::*
 *                   compiler.c1.Test8301489
 */


package compiler.c1;

public class Test8301489 {
    static int c = 0;
    static int[] arr = {};

    static void op2Test(int a, int b) {
        // Implicit edges created during dom calculation to exception handler
        if (a < 0) {
            b = 0;
        }
        // Create two branches into next loop header block
        try {
            int l = arr.length;
            for (int i = 0; i < l; i++) {
                int d = arr[i] + arr[i];
            }
        }
        // Exception handler as predecessor of the next loop header block
        catch (ArithmeticException e) {}

        // op2(a, b) as candidate for hoisting: operands are loop invariant
        while (a + b < b) {}
        // op2(a, b) should not be hoisted above 'if (a < 0) {...}' block
    }

    static void arrayLengthTest() {
        float [] newArr = new float[c];

        try {
            for (float f : newArr) {}
        }
        catch (ArrayIndexOutOfBoundsException e) {}

        while (54321 < newArr.length) {
            newArr[c] = 123.45f;
        }
    }

    static void negateTest(int a) {
        if (a <= 111) {
            a = -111;
        }

        int f = 0;
        try {
            int l = arr.length;
            f--;
        }
        catch (NegativeArraySizeException e) {}

        while (-a < f) {
            f--;
        }
    }

    static void convertTest(int a) {
        if (c == 0) {
            a = 0;
        }

        long tgt = 10;

        try {
            String s = String.valueOf(c);
        }
        catch (NumberFormatException e) {}

        while ((long)a != tgt) {
            tgt--;
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 3; i++) {
            op2Test(12, 34);
            arrayLengthTest();
            negateTest(-778);
            convertTest(4812);
        }
    }
}
