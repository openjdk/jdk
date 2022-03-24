/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8279258
 * @summary Auto-vectorization enhancement for two-dimensional array operations
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestAutoVectorization2DArray
 */

public class TestAutoVectorization2DArray {
    final private static int NUM = 64;

    private static double[][] a = new double[NUM][NUM];
    private static double[][] b = new double[NUM][NUM];
    private static double[][] c = new double[NUM][NUM];

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(counts = { IRNode.LOAD_VECTOR,  " >0 " })
    @IR(counts = { IRNode.ADD_VD,       " >0 " })
    @IR(counts = { IRNode.STORE_VECTOR, " >0 " })
    private static void testDouble(double[][] a , double[][] b, double[][] c) {
        for(int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                a[i][j] = b[i][j] + c[i][j];
            }
        }
    }

    @Run(test = "testDouble")
    private void testDouble_runner() {
        testDouble(a, b, c);
    }
}
