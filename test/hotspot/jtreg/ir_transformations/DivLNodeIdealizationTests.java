/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package ir_transformations;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @summary Test that Ideal transformations of DivLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver ir_transformations.DivLNodeIdealizationTests
 */
public class DivLNodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks x / x => 1
    public long constant(long x) {
        return x / x;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks x / 1 => x
    public long identity(long x) {
        return x / 1L;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks x / (c / c) => x
    public long identity1(long x) {
        return x / (13L / 13L);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    // Checks x / (y / y) => x
    public long identity2(long x, long y) {
        return x / (y / y);
    }

    @Test
    @Arguments({Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.ADD, IRNode.SUB})
    @IR(counts = {IRNode.MUL_L, "1",
                  IRNode.TRAP, "1"
                 })
    // Hotspot should keep the division because it may cause a division by zero trap
    public long retainDenominator(long x, long y) {
        return (x * y) / y;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD})
    @IR(counts = {IRNode.SUB, "1"})
    // Checks x / -1 => 0 - x
    public long divByNegOne(long x) {
        return x / -1L;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB})
    @IR(counts = {IRNode.AND, "1",
                  IRNode.RSHIFT, "1",
                 })
    // Checks (x & -(2^c0)) / 2^c1 => (x >> c1) & (2^c0 >> c1) => (x >> c1) & c3 where 2^c0 > |2^c1| and c3 = 2^c0 >> c1
    // Having a large enough and in the dividend removes the need to account for
    // rounding when converting to shifts and multiplies as in divByPow2()
    public long divByPow2And(long x) {
        return (x & -4L) / 2L;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.ADD, IRNode.SUB, IRNode.AND})
    @IR(counts = {IRNode.RSHIFT, "1"})
    // Checks (x & -(2^c0)) / 2^c0 => x >> c0
    // If the negative of the constant within the & equals the divisor then
    // the and can be removed as it only affects bits that will be shifted off
    public long divByPow2And1(long x) {
        return (x & -2L) / 2L;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV, IRNode.SUB})
    @IR(counts = {IRNode.URSHIFT, "1",
                  IRNode.RSHIFT, "2",
                  IRNode.ADD, "1",
                 })
    // Checks x / 2^c0 => x + ((x >> (32-1)) >>> (32 - c0)) >> c0 => x + ((x >> 31) >>> c1) >> c0 where c1 = 32 - c0
    // An additional (dividend - 1) needs to be added to the shift to account
    // for rounding when dealing with negative numbers. Since x may be negative
    // in this method, an additional add, logical right shift, and signed shift
    // are needed to account for rounding.
    public long divByPow2(long x) {
        return x / 8L;
    }

    @Test
    @Arguments(Argument.RANDOM_EACH)
    @IR(failOn = {IRNode.LOAD, IRNode.STORE, IRNode.MUL, IRNode.DIV})
    @IR(counts = {IRNode.URSHIFT, "1",
                  IRNode.RSHIFT, "2",
                  IRNode.ADD, "1",
                  IRNode.SUB, "1",
                 })
    // Checks x / -(2^c0) =>0 - (x + ((x >> (32-1)) >>> (32 - c0)) >> c0) => 0 -  (x + ((x >> 31) >>> c1) >> c0) where c1 = 32 - c0
    // Similar to divByPow2() except a negative divisor turns positive.
    // After the transformations, 0 is subtracted by the whole expression
    // to account for the negative.
    public long divByNegPow2(long x) {
        return x / -8L;
    }
}