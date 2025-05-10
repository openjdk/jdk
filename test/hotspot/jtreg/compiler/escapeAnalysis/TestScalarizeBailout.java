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
 * @bug 8315916
 * @summary Test early bailout during the creation of graph nodes for the scalarization of array fields, rather than during code generation.
 * @run main/othervm -Xcomp
 *                   -XX:-TieredCompilation
 *                   -XX:EliminateAllocationArraySizeLimit=32000
 *                   -XX:MaxNodeLimit=20000
 *                   -XX:CompileCommand=dontinline,compiler.escapeAnalysis.TestScalarizeBailout::initializeArray
 *                   -XX:CompileCommand=compileonly,compiler.escapeAnalysis.TestScalarizeBailout::*
 *                   compiler.escapeAnalysis.TestScalarizeBailout
 * @run main/othervm -Xcomp
 *                   -XX:-TieredCompilation
 *                   -XX:CompileCommand=dontinline,compiler.escapeAnalysis.TestScalarizeBailout::initializeArray
 *                   -XX:CompileCommand=compileonly,compiler.escapeAnalysis.TestScalarizeBailout::*
 *                   compiler.escapeAnalysis.TestScalarizeBailout
 */

package compiler.escapeAnalysis;

public class TestScalarizeBailout {
    static Object var1;

    public static void main(String[] args) {
        // The test is designed to trigger a bailout during the scalarization of array fields.
        // The array size is set to 16K, which is below the threshold for scalarization (MaxNodeLimit=20000).
        var1 = new long[16 * 1024];
        long[] a1 = new long[16 * 1024];
        TestScalarizeBailout test = new TestScalarizeBailout();
        test.initializeArray(a1);
    }

    // This method is used to initialize the array with values from 0 to length - 1.
    // Esape analysis should be able to eliminate the allocation of the array as the size 16k is
    // below the EliminateAllocationArraySizeLimit=32000.
    private void initializeArray(long[] a1) {
        for (int i = 0; i < a1.length; i++) {
            a1[i] = i;
        }
    }
}
