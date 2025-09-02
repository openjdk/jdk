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
package compiler.c2.gvn;

import compiler.lib.generators.Generators;
import compiler.lib.generators.RestrictableGenerator;
import compiler.lib.ir_framework.DontCompile;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8373555
 * @summary Test that sign-invariant comparisons are simplified.
 * @library /test/lib /
 * @run driver compiler.c2.gvn.BoolNodeSimplifySignInvariantTests
 */
public class BoolNodeSimplifySignInvariantTests {
    private static final RestrictableGenerator<Integer> GEN_INT = Generators.G.ints();
    private static final RestrictableGenerator<Long> GEN_LONG = Generators.G.longs();

    private static final int LO_POS_I;
    private static final int HI_POS_I;
    private static final int LO_NEG_I;
    private static final int HI_NEG_I;
    private static final long LO_POS_L;
    private static final long HI_POS_L;
    private static final long LO_NEG_L;
    private static final long HI_NEG_L;

    static {
        RestrictableGenerator<Integer> posI = GEN_INT.restricted(0, Integer.MAX_VALUE);
        int pos1i = posI.next();
        int pos2i = posI.next();
        LO_POS_I = Math.min(pos1i, pos2i);
        HI_POS_I = Math.max(pos1i, pos2i);
        RestrictableGenerator<Integer> negI = GEN_INT.restricted(Integer.MIN_VALUE, -1);
        int neg1i = negI.next();
        int neg2i = negI.next();
        LO_NEG_I = Math.min(neg1i, neg2i);
        HI_NEG_I = Math.max(neg1i, neg2i);
        RestrictableGenerator<Long> posL = GEN_LONG.restricted(0L, Long.MAX_VALUE);
        long pos1l = posL.next();
        long pos2l = posL.next();
        LO_POS_L = Math.min(pos1l, pos2l);
        HI_POS_L = Math.max(pos1l, pos2l);
        RestrictableGenerator<Long> negL = GEN_LONG.restricted(Long.MIN_VALUE, -1L);
        long neg1l = negL.next();
        long neg2l = negL.next();
        LO_NEG_L = Math.min(neg1l, neg2l);
        HI_NEG_L = Math.max(neg1l, neg2l);
    }

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {
        "knownAndInputInt1", "knownAndInputInt2", "knownAndInputInt3", "knownAndInputInt4",
        "knownMaxInputInt1", "knownMaxInputInt2", "knownMaxInputInt3", "knownMaxInputInt4",
        "knownOrInputInt1", "knownOrInputInt2", "knownOrInputInt3", "knownOrInputInt4",
        "knownMinInputInt1", "knownMinInputInt2", "knownMinInputInt3", "knownMinInputInt4",
        "knownXorInputInt1", "knownXorInputInt2", "knownXorInputInt3", "knownXorInputInt4",
        "knownXorInputInt5", "knownXorInputInt6", "knownXorInputInt7", "knownXorInputInt8",
        "knownAndInputLong1", "knownAndInputLong2", "knownAndInputLong3", "knownAndInputLong4",
        "knownMaxInputLong1", "knownMaxInputLong2", "knownMaxInputLong3", "knownMaxInputLong4",
        "knownOrInputLong1", "knownOrInputLong2", "knownOrInputLong3", "knownOrInputLong4",
        "knownMinInputLong1", "knownMinInputLong2", "knownMinInputLong3", "knownMinInputLong4",
        "knownXorInputLong1", "knownXorInputLong2", "knownXorInputLong3", "knownXorInputLong4",
        "knownXorInputLong5", "knownXorInputLong6", "knownXorInputLong7", "knownXorInputLong8",
    })
    public void runMethod() {
        assertResultI();
        assertResultL();
    }

    @DontCompile
    private void assertResultI() {
        int a = GEN_INT.next();
        int b = GEN_INT.next();
        int aNeg = Math.min(HI_NEG_I, Math.max(LO_NEG_I, a));
        int bNeg = Math.min(HI_NEG_I, Math.max(LO_NEG_I, b));
        int aPos = Math.min(HI_POS_I, Math.max(LO_POS_I, a));
        int bPos = Math.min(HI_POS_I, Math.max(LO_POS_I, b));

        Asserts.assertEQ((a & bNeg) < 0, knownAndInputInt1(a, b));
        Asserts.assertEQ((aNeg & b) < 0, knownAndInputInt2(a, b));
        Asserts.assertEQ((a & bNeg) >= 0, knownAndInputInt3(a, b));
        Asserts.assertEQ((aNeg & b) >= 0, knownAndInputInt4(a, b));

        Asserts.assertEQ(Math.max(a, bNeg) < 0, knownMaxInputInt1(a, b));
        Asserts.assertEQ(Math.max(aNeg, b) < 0, knownMaxInputInt2(a, b));
        Asserts.assertEQ(Math.max(a, bNeg) >= 0, knownMaxInputInt3(a, b));
        Asserts.assertEQ(Math.max(aNeg, b) >= 0, knownMaxInputInt4(a, b));

        Asserts.assertEQ((a | bPos) < 0, knownOrInputInt1(a, b));
        Asserts.assertEQ((aPos | b) < 0, knownOrInputInt2(a, b));
        Asserts.assertEQ((a | bPos) >= 0, knownOrInputInt3(a, b));
        Asserts.assertEQ((aPos | b) >= 0, knownOrInputInt4(a, b));

        Asserts.assertEQ(Math.min(a, bPos) < 0, knownMinInputInt1(a, b));
        Asserts.assertEQ(Math.min(aPos, b) < 0, knownMinInputInt2(a, b));
        Asserts.assertEQ(Math.min(a, bPos) >= 0, knownMinInputInt3(a, b));
        Asserts.assertEQ(Math.min(aPos, b) >= 0, knownMinInputInt4(a, b));

        Asserts.assertEQ((a ^ bNeg) < 0, knownXorInputInt1(a, b));
        Asserts.assertEQ((aNeg ^ b) < 0, knownXorInputInt2(a, b));
        Asserts.assertEQ((a ^ bNeg) >= 0, knownXorInputInt3(a, b));
        Asserts.assertEQ((aNeg ^ b) >= 0, knownXorInputInt4(a, b));
        Asserts.assertEQ((a ^ bPos) < 0, knownXorInputInt5(a, b));
        Asserts.assertEQ((aPos ^ b) < 0, knownXorInputInt6(a, b));
        Asserts.assertEQ((a ^ bPos) >= 0, knownXorInputInt7(a, b));
        Asserts.assertEQ((aPos ^ b) >= 0, knownXorInputInt8(a, b));
    }

    @DontCompile
    private void assertResultL() {
        long a = GEN_LONG.next();
        long b = GEN_LONG.next();
        long aNeg = Math.min(HI_NEG_L, Math.max(LO_NEG_L, a));
        long bNeg = Math.min(HI_NEG_L, Math.max(LO_NEG_L, b));
        long aPos = Math.min(HI_POS_L, Math.max(LO_POS_L, a));
        long bPos = Math.min(HI_POS_L, Math.max(LO_POS_L, b));

        Asserts.assertEQ((a & bNeg) < 0, knownAndInputLong1(a, b));
        Asserts.assertEQ((aNeg & b) < 0, knownAndInputLong2(a, b));
        Asserts.assertEQ((a & bNeg) >= 0, knownAndInputLong3(a, b));
        Asserts.assertEQ((aNeg & b) >= 0, knownAndInputLong4(a, b));

        Asserts.assertEQ(Math.max(a, bNeg) < 0, knownMaxInputLong1(a, b));
        Asserts.assertEQ(Math.max(aNeg, b) < 0, knownMaxInputLong2(a, b));
        Asserts.assertEQ(Math.max(a, bNeg) >= 0, knownMaxInputLong3(a, b));
        Asserts.assertEQ(Math.max(aNeg, b) >= 0, knownMaxInputLong4(a, b));

        Asserts.assertEQ((a | bPos) < 0, knownOrInputLong1(a, b));
        Asserts.assertEQ((aPos | b) < 0, knownOrInputLong2(a, b));
        Asserts.assertEQ((a | bPos) >= 0, knownOrInputLong3(a, b));
        Asserts.assertEQ((aPos | b) >= 0, knownOrInputLong4(a, b));

        Asserts.assertEQ(Math.min(a, bPos) < 0, knownMinInputLong1(a, b));
        Asserts.assertEQ(Math.min(aPos, b) < 0, knownMinInputLong2(a, b));
        Asserts.assertEQ(Math.min(a, bPos) >= 0, knownMinInputLong3(a, b));
        Asserts.assertEQ(Math.min(aPos, b) >= 0, knownMinInputLong4(a, b));

        Asserts.assertEQ((a ^ bNeg) < 0, knownXorInputLong1(a, b));
        Asserts.assertEQ((aNeg ^ b) < 0, knownXorInputLong2(a, b));
        Asserts.assertEQ((a ^ bNeg) >= 0, knownXorInputLong3(a, b));
        Asserts.assertEQ((aNeg ^ b) >= 0, knownXorInputLong4(a, b));
        Asserts.assertEQ((a ^ bPos) < 0, knownXorInputLong5(a, b));
        Asserts.assertEQ((aPos ^ b) < 0, knownXorInputLong6(a, b));
        Asserts.assertEQ((a ^ bPos) >= 0, knownXorInputLong7(a, b));
        Asserts.assertEQ((aPos ^ b) >= 0, knownXorInputLong8(a, b));
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputInt1(int a, int b) {
        return (a & Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputInt2(int a, int b) {
        return (Math.min(HI_NEG_I, Math.max(LO_NEG_I, a)) & b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputInt3(int a, int b) {
        return (a & Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputInt4(int a, int b) {
        return (Math.min(HI_NEG_I, Math.max(LO_NEG_I, a)) & b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputInt1(int a, int b) {
        return Math.max(a, Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputInt2(int a, int b) {
        return Math.max(Math.min(HI_NEG_I, Math.max(LO_NEG_I, a)), b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputInt3(int a, int b) {
        return Math.max(a, Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputInt4(int a, int b) {
        return Math.max(Math.min(HI_NEG_I, Math.max(LO_NEG_I, a)), b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputInt1(int a, int b) {
        return (a | Math.min(HI_POS_I, Math.max(LO_POS_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputInt2(int a, int b) {
        return (Math.min(HI_POS_I, Math.max(LO_POS_I, a)) | b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputInt3(int a, int b) {
        return (a | Math.min(HI_POS_I, Math.max(LO_POS_I, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputInt4(int a, int b) {
        return (Math.min(HI_POS_I, Math.max(LO_POS_I, a)) | b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputInt1(int a, int b) {
        return Math.min(a, Math.min(HI_POS_I, Math.max(LO_POS_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputInt2(int a, int b) {
        return Math.min(Math.min(HI_POS_I, Math.max(LO_POS_I, a)), b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputInt3(int a, int b) {
        return Math.min(a, Math.min(HI_POS_I, Math.max(LO_POS_I, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputInt4(int a, int b) {
        return Math.min(Math.min(HI_POS_I, Math.max(LO_POS_I, a)), b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputInt1(int a, int b) {
        return (a ^ Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputInt2(int a, int b) {
        return (Math.min(HI_NEG_I, Math.max(LO_NEG_I, a)) ^ b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputInt3(int a, int b) {
        return (a ^ Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputInt4(int a, int b) {
        return (Math.min(HI_NEG_I, Math.max(LO_NEG_I, a)) ^ b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputInt5(int a, int b) {
        return (a ^ Math.min(HI_POS_I, Math.max(LO_POS_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputInt6(int a, int b) {
        return (Math.min(HI_POS_I, Math.max(LO_POS_I, a)) ^ b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputInt7(int a, int b) {
        return (a ^ Math.min(HI_POS_I, Math.max(LO_POS_I, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputInt8(int a, int b) {
        return (Math.min(HI_POS_I, Math.max(LO_POS_I, a)) ^ b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputLong1(long a, long b) {
        return (a & Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputLong2(long a, long b) {
        return (Math.min(HI_NEG_L, Math.max(LO_NEG_L, a)) & b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputLong3(long a, long b) {
        return (a & Math.min(HI_NEG_L, Math.max(LO_NEG_L, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputLong4(long a, long b) {
        return (Math.min(HI_NEG_L, Math.max(LO_NEG_L, a)) & b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputLong1(long a, long b) {
        return Math.max(a, Math.min(HI_NEG_L, Math.max(LO_NEG_L, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputLong2(long a, long b) {
        return Math.max(Math.min(HI_NEG_L, Math.max(LO_NEG_L, a)), b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputLong3(long a, long b) {
        return Math.max(a, Math.min(HI_NEG_L, Math.max(LO_NEG_L, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputLong4(long a, long b) {
        return Math.max(Math.min(HI_NEG_L, Math.max(LO_NEG_L, a)), b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputLong1(long a, long b) {
        return (a | Math.min(HI_POS_L, Math.max(LO_POS_L, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputLong2(long a, long b) {
        return (Math.min(HI_POS_L, Math.max(LO_POS_L, a)) | b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputLong3(long a, long b) {
        return (a | Math.min(HI_POS_L, Math.max(LO_POS_L, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputLong4(long a, long b) {
        return (Math.min(HI_POS_L, Math.max(LO_POS_L, a)) | b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLong1(long a, long b) {
        return (a ^ Math.min(HI_NEG_L, Math.max(LO_NEG_L, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputLong1(long a, long b) {
        return Math.min(a, Math.min(HI_POS_L, Math.max(LO_POS_L, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputLong2(long a, long b) {
        return Math.min(Math.min(HI_POS_L, Math.max(LO_POS_L, a)), b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputLong3(long a, long b) {
        return Math.min(a, Math.min(HI_POS_L, Math.max(LO_POS_L, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputLong4(long a, long b) {
        return Math.min(Math.min(HI_POS_L, Math.max(LO_POS_L, a)), b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLong2(long a, long b) {
        return (Math.min(HI_NEG_L, Math.max(LO_NEG_L, a)) ^ b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLong3(long a, long b) {
        return (a ^ Math.min(HI_NEG_L, Math.max(LO_NEG_L, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLong4(long a, long b) {
        return (Math.min(HI_NEG_L, Math.max(LO_NEG_L, a)) ^ b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLong5(long a, long b) {
        return (a ^ Math.min(HI_POS_L, Math.max(LO_POS_L, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLong6(long a, long b) {
        return (Math.min(HI_POS_L, Math.max(LO_POS_L, a)) ^ b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLong7(long a, long b) {
        return (a ^ Math.min(HI_POS_L, Math.max(LO_POS_L, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLong8(long a, long b) {
        return (Math.min(HI_POS_L, Math.max(LO_POS_L, a)) ^ b) >= 0;
    }
}
