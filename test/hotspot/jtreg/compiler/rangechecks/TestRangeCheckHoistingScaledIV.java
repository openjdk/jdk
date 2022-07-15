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

/*
 * @test
 * @bug 8289996
 * @summary Test range check hoisting for some scaled iv at array index
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.rangechecks.TestRangeCheckHoistingScaledIV
 */

package compiler.rangechecks;

import compiler.lib.ir_framework.*;

public class TestRangeCheckHoistingScaledIV {

    private static final int SIZE = 16000;

    private static int[] a = new int[SIZE];
    private static int[] b = new int[SIZE];
    private static int count = 567;

    // If the loop predication successfully hoists range checks in below
    // loops, there is only uncommon trap with reason='predicate' and no
    // uncommon trap with reason='range_check'.

    @Test
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void ivMul3() {
        for (int i = 0; i < count; i++) {
            b[3 * i] = a[3 * i];
        }
    }

    @Test
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void ivMul6() {
        for (int i = 0; i < count; i++) {
            b[6 * i] = a[6 * i];
        }
    }

    @Test
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void ivMul7() {
        for (int i = 0; i < count; i++) {
            b[7 * i] = a[7 * i];
        }
    }

    @Test
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void ivMulMinus3() {
        for (int i = 0; i > -count; i--) {
            b[-3 * i] = a[-3 * i];
        }
    }

    @Test
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void ivMulMinus6() {
        for (int i = 0; i > -count; i--) {
            b[-6 * i] = a[-6 * i];
        }
    }

    @Test
    @IR(failOn = {IRNode.RANGE_CHECK_TRAP})
    public static void ivMulMinus9() {
        for (int i = 0; i > -count; i--) {
            b[-9 * i] = a[-9 * i];
        }
    }

    public static void main(String[] args) {
        TestFramework.run();
    }
}
