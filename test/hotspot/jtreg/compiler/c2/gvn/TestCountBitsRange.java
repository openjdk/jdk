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
import jdk.test.lib.Asserts;

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

    @Run(test = {
        "clzCompareInt", "clzDiv8Int",
        "clzCompareLong", "clzDiv8Long",
        "ctzCompareInt", "ctzDiv8Int",
        "ctzCompareLong", "ctzDiv8Long",
    })
    public void runTest() {
        int i = RunInfo.getRandom().nextInt();
        long l = RunInfo.getRandom().nextLong();
        assertResult(i, l);
    }

    @DontCompile
    public void assertResult(int i, long l) {
        Asserts.assertEQ(Integer.numberOfLeadingZeros(42),
                         clzConstInt());
        Asserts.assertEQ(Integer.numberOfLeadingZeros(i) < 0 || Integer.numberOfLeadingZeros(i) > 32,
                         clzCompareInt(i));
        Asserts.assertEQ(Integer.numberOfLeadingZeros(i) / 8,
                         clzDiv8Int(i));
        Asserts.assertEQ(Long.numberOfLeadingZeros(42),
                         clzConstLong());
        Asserts.assertEQ(Long.numberOfLeadingZeros(l) < 0 || Long.numberOfLeadingZeros(l) > 64,
                         clzCompareLong(l));
        Asserts.assertEQ(Long.numberOfLeadingZeros(l) / 8,
                         clzDiv8Long(l));
        Asserts.assertEQ(Integer.numberOfTrailingZeros(42),
                         ctzConstInt());
        Asserts.assertEQ(Integer.numberOfTrailingZeros(i) < 0 || Integer.numberOfTrailingZeros(i) > 32,
                         ctzCompareInt(i));
        Asserts.assertEQ(Integer.numberOfTrailingZeros(i) / 8,
                         ctzDiv8Int(i));
        Asserts.assertEQ(Long.numberOfTrailingZeros(42),
                         ctzConstLong());
        Asserts.assertEQ(Long.numberOfTrailingZeros(l) < 0 || Long.numberOfTrailingZeros(l) > 64,
                         ctzCompareLong(l));
        Asserts.assertEQ(Long.numberOfTrailingZeros(l) / 8,
                         ctzDiv8Long(l));
    }

    @Test
    @IR(failOn = IRNode.COUNT_LEADING_ZEROS_I)
    public int clzConstInt() {
        return Integer.numberOfLeadingZeros(42);
    }

    @Test
    @IR(failOn = IRNode.COUNT_LEADING_ZEROS_I)
    public boolean clzCompareInt(int i) {
        return Integer.numberOfLeadingZeros(i) < 0 || Integer.numberOfLeadingZeros(i) > 32;
    }

    @Test
    @IR(counts = {IRNode.COUNT_LEADING_ZEROS_I, "1",
                  IRNode.RSHIFT_I, "1",
                  IRNode.URSHIFT_I, "0",
                  IRNode.ADD_I, "0"})
    public int clzDiv8Int(int i) {
        return Integer.numberOfLeadingZeros(i) / 8;
    }

    @Test
    @IR(failOn = IRNode.COUNT_LEADING_ZEROS_L)
    public int clzConstLong() {
        return Long.numberOfLeadingZeros(42);
    }

    @Test
    @IR(failOn = IRNode.COUNT_LEADING_ZEROS_L)
    public boolean clzCompareLong(long l) {
        return Long.numberOfLeadingZeros(l) < 0 || Long.numberOfLeadingZeros(l) > 64;
    }

    @Test
    @IR(counts = {IRNode.COUNT_LEADING_ZEROS_L, "1",
                  IRNode.RSHIFT_I, "1",
                  IRNode.URSHIFT_I, "0",
                  IRNode.ADD_I, "0"})
    public int clzDiv8Long(long l) {
        return Long.numberOfLeadingZeros(l) / 8;
    }

    @Test
    @IR(failOn = IRNode.COUNT_TRAILING_ZEROS_I)
    public int ctzConstInt() {
        return Integer.numberOfTrailingZeros(42);
    }

    @Test
    @IR(failOn = IRNode.COUNT_TRAILING_ZEROS_I)
    public boolean ctzCompareInt(int i) {
        return Integer.numberOfTrailingZeros(i) < 0 || Integer.numberOfTrailingZeros(i) > 32;
    }

    @Test
    @IR(counts = {IRNode.COUNT_TRAILING_ZEROS_I, "1",
                  IRNode.RSHIFT_I, "1",
                  IRNode.URSHIFT_I, "0",
                  IRNode.ADD_I, "0"})
    public int ctzDiv8Int(int i) {
        return Integer.numberOfTrailingZeros(i) / 8;
    }

    @Test
    @IR(failOn = IRNode.COUNT_TRAILING_ZEROS_L)
    public int ctzConstLong() {
        return Long.numberOfTrailingZeros(42);
    }

    @Test
    @IR(failOn = IRNode.COUNT_TRAILING_ZEROS_L)
    public boolean ctzCompareLong(long l) {
        return Long.numberOfTrailingZeros(l) < 0 || Long.numberOfTrailingZeros(l) > 64;
    }

    @Test
    @IR(counts = {IRNode.COUNT_TRAILING_ZEROS_L, "1",
                  IRNode.RSHIFT_I, "1",
                  IRNode.URSHIFT_I, "0",
                  IRNode.ADD_I, "0"})
    public int ctzDiv8Long(long l) {
        return Long.numberOfTrailingZeros(l) / 8;
    }
}
