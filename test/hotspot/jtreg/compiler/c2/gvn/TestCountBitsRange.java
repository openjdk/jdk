/*
 * Copyright (c) 2025 Alibaba Group Holding Limited. All Rights Reserved.
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

package compiler.c2.gvn;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8360192
 * @summary Tests that count bits nodes are handled correctly.
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.gvn.TestCountBitsRange
 */
public class TestCountBitsRange {
    public static void main(String[] args) {
        TestFramework.run();
    }

    static int i = RunInfo.getRandom().nextInt();
    static long l = RunInfo.getRandom().nextLong();

    @Test
    @IR(failOn = IRNode.COUNT_LEADING_ZEROS_I)
    public boolean clzCompareInt() {
        return Integer.numberOfLeadingZeros(i) < 0 || Integer.numberOfLeadingZeros(i) > 32;
    }

    @Test
    @IR(counts = {IRNode.COUNT_LEADING_ZEROS_I, "1",
                  IRNode.RSHIFT_I, "1",
                  IRNode.URSHIFT_I, "0",
                  IRNode.ADD_I, "0"})
    public int clzDiv8Int() {
        return Integer.numberOfLeadingZeros(i) / 8;
    }

    @Test
    @IR(failOn = IRNode.COUNT_LEADING_ZEROS_L)
    public boolean clzCompareLong() {
        return Long.numberOfLeadingZeros(l) < 0 || Long.numberOfLeadingZeros(l) > 64;
    }

    @Test
    @IR(counts = {IRNode.COUNT_LEADING_ZEROS_L, "1",
                  IRNode.RSHIFT_I, "1",
                  IRNode.URSHIFT_I, "0",
                  IRNode.ADD_I, "0"})
    public int clzDiv8Long() {
        return Long.numberOfLeadingZeros(l) / 8;
    }

    @Test
    @IR(failOn = IRNode.COUNT_TRAILING_ZEROS_I)
    public boolean ctzCompareInt() {
        return Integer.numberOfTrailingZeros(i) < 0 || Integer.numberOfTrailingZeros(i) > 32;
    }

    @Test
    @IR(counts = {IRNode.COUNT_TRAILING_ZEROS_I, "1",
                  IRNode.RSHIFT_I, "1",
                  IRNode.URSHIFT_I, "0",
                  IRNode.ADD_I, "0"})
    public int ctzDiv8Int() {
        return Integer.numberOfTrailingZeros(i) / 8;
    }

    @Test
    @IR(failOn = IRNode.COUNT_TRAILING_ZEROS_L)
    public boolean ctzCompareLong() {
        return Long.numberOfTrailingZeros(l) < 0 || Long.numberOfTrailingZeros(l) > 64;
    }

    @Test
    @IR(counts = {IRNode.COUNT_TRAILING_ZEROS_L, "1",
                  IRNode.RSHIFT_I, "1",
                  IRNode.URSHIFT_I, "0",
                  IRNode.ADD_I, "0"})
    public int ctzDiv8Long() {
        return Long.numberOfTrailingZeros(l) / 8;
    }
}
