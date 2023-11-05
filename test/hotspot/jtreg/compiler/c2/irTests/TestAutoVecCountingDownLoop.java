/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
 * @bug 8284981
 * @summary Auto-vectorization enhancement for special counting down loops
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="aarch64"
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestAutoVecCountingDownLoop
 */

public class TestAutoVecCountingDownLoop {
    final private static int ARRLEN = 3000;

    private static int[] a = new int[ARRLEN];
    private static int[] b = new int[ARRLEN];

    public static void main(String[] args) {
        TestFramework.run();
    }


    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,  " >0 "})
    @IR(counts = {IRNode.STORE_VECTOR, " >0 "})
    private static void testCountingDown(int[] a, int[] b) {
        for (int i = 2000; i > 0; i--) {
            b[ARRLEN - i] = a[ARRLEN - i];
        }
    }

    @Run(test = "testCountingDown")
    private void testCountingDown_runner() {
        testCountingDown(a, b);
    }
}
