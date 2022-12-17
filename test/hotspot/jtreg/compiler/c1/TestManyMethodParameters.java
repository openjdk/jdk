/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
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
 *
 */

/**
 * @test
 * @bug 8288781
 * @summary Test if a call with 8 integer + 13 float = 21 parameters can can be compiled.
 *          On ppc all parameters can be passed in registers.
 * @author Richard Reingruber
 *
 * @run main/othervm -Xbatch -XX:CompileCommand=dontinline,*::*dontinline* compiler.c1.TestManyMethodParameters
 */

package compiler.c1;

public class TestManyMethodParameters {
    public static void main(String[] args) {
        for (int i = 30_000; i >= 0; i--) {
            double sum = testMethod_01_dontinline();
            if (sum != 127) {
                throw new Error("Wrong sum: " + sum);
            }
        }
    }

    public static double testMethod_01_dontinline() {
        return testMethod_01_manyArgs(1, 2, 3, 4, 5, 6, 7, 8,
                                      1.0d, 2.0d, 3.0d, 4.0d, 5.0d, 6.0d, 7.0d, 8.0d, 9.0d, 10.0d, 11.0d, 12.0d, 13.0d);
    }

    public static double testMethod_01_manyArgs(long l1, long l2, long l3, long l4, long l5, long l6, long l7, long l8,
                                                double d1, double d2, double d3, double d4, double d5, double d6, double d7,
                                                double d8, double d9, double d10, double d11, double d12, double d13) {
        return l1+l2+l3+l4+l5+l6+l7+l8+d1+d2+d3+d4+d5+d6+d7+d8+d9+d10+d11+d12+d13;
    }
}
