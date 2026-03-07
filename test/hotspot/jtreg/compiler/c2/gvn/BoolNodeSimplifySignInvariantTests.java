/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.generators.IntRange;
import compiler.lib.generators.LongRange;
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
        "knownAndInputIntLtR", "knownAndInputIntLtL", "knownAndInputIntGeR", "knownAndInputIntGeL",
        "knownMaxInputIntLtR", "knownMaxInputIntLtL", "knownMaxInputIntGeR", "knownMaxInputIntGeL",
        "knownOrInputIntLtR", "knownOrInputIntLtL", "knownOrInputIntGeR", "knownOrInputIntGeL",
        "knownMinInputIntLtR", "knownMinInputIntLtL", "knownMinInputIntGeR", "knownMinInputIntGeL",

        "knownXorInputIntLtRPos", "knownXorInputIntLtLPos", "knownXorInputIntGeRPos", "knownXorInputIntGeLPos",
        "knownXorInputIntLtRNeg", "knownXorInputIntLtLNeg", "knownXorInputIntGeRNeg", "knownXorInputIntGeLNeg",

        "knownAndInputLongLtR", "knownAndInputLongLtL", "knownAndInputLongGeR", "knownAndInputLongGeL",
        "knownMaxInputLongLtR", "knownMaxInputLongLtL", "knownMaxInputLongGeR", "knownMaxInputLongGeL",
        "knownOrInputLongLtR", "knownOrInputLongLtL", "knownOrInputLongGeR", "knownOrInputLongGeL",
        "knownMinInputLongLtR", "knownMinInputLongLtL", "knownMinInputLongGeR", "knownMinInputLongGeL",

        "knownXorInputLongLtRPos", "knownXorInputLongLtLPos", "knownXorInputLongGeRPos", "knownXorInputLongGeLPos",
        "knownXorInputLongLtRNeg", "knownXorInputLongLtLNeg", "knownXorInputLongGeRNeg", "knownXorInputLongGeLNeg",

        "testRandomRangeAndIntLt", "testRandomRangeAndIntGe", "testRandomRangeAndLongLt", "testRandomRangeAndLongGe",
        "testRandomRangeOrIntLt", "testRandomRangeOrIntGe", "testRandomRangeOrLongLt", "testRandomRangeOrLongGe",
        "testRandomRangeXorIntLt", "testRandomRangeXorIntGe", "testRandomRangeXorLongLt", "testRandomRangeXorLongGe",
        "testRandomRangeMaxIntLt", "testRandomRangeMaxIntGe", "testRandomRangeMaxLongLt", "testRandomRangeMaxLongGe",
        "testRandomRangeMinIntLt", "testRandomRangeMinIntGe", "testRandomRangeMinLongLt", "testRandomRangeMinLongGe",
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

        Asserts.assertEQ((a & bNeg) < 0, knownAndInputIntLtR(a, b));
        Asserts.assertEQ((aNeg & b) < 0, knownAndInputIntLtL(a, b));
        Asserts.assertEQ((a & bNeg) >= 0, knownAndInputIntGeR(a, b));
        Asserts.assertEQ((aNeg & b) >= 0, knownAndInputIntGeL(a, b));

        Asserts.assertEQ(Math.max(a, bNeg) < 0, knownMaxInputIntLtR(a, b));
        Asserts.assertEQ(Math.max(aNeg, b) < 0, knownMaxInputIntLtL(a, b));
        Asserts.assertEQ(Math.max(a, bNeg) >= 0, knownMaxInputIntGeR(a, b));
        Asserts.assertEQ(Math.max(aNeg, b) >= 0, knownMaxInputIntGeL(a, b));

        Asserts.assertEQ((a | bPos) < 0, knownOrInputIntLtR(a, b));
        Asserts.assertEQ((aPos | b) < 0, knownOrInputIntLtL(a, b));
        Asserts.assertEQ((a | bPos) >= 0, knownOrInputIntGeR(a, b));
        Asserts.assertEQ((aPos | b) >= 0, knownOrInputIntGeL(a, b));

        Asserts.assertEQ(Math.min(a, bPos) < 0, knownMinInputIntLtR(a, b));
        Asserts.assertEQ(Math.min(aPos, b) < 0, knownMinInputIntLtL(a, b));
        Asserts.assertEQ(Math.min(a, bPos) >= 0, knownMinInputIntGeR(a, b));
        Asserts.assertEQ(Math.min(aPos, b) >= 0, knownMinInputIntGeL(a, b));

        Asserts.assertEQ((a ^ bNeg) < 0, knownXorInputIntLtRNeg(a, b));
        Asserts.assertEQ((aNeg ^ b) < 0, knownXorInputIntLtLNeg(a, b));
        Asserts.assertEQ((a ^ bNeg) >= 0, knownXorInputIntGeRNeg(a, b));
        Asserts.assertEQ((aNeg ^ b) >= 0, knownXorInputIntGeLNeg(a, b));
        Asserts.assertEQ((a ^ bPos) < 0, knownXorInputIntLtRPos(a, b));
        Asserts.assertEQ((aPos ^ b) < 0, knownXorInputIntLtLPos(a, b));
        Asserts.assertEQ((a ^ bPos) >= 0, knownXorInputIntGeRPos(a, b));
        Asserts.assertEQ((aPos ^ b) >= 0, knownXorInputIntGeLPos(a, b));

        for (int i = 0; i < 10; i++) {
            int ia = GEN_INT.next();
            int ib = GEN_INT.next();
            Asserts.assertEQ(testRandomRangeAndIntLtInterpreted(ia, ib), testRandomRangeAndIntLt(ia, ib));
            Asserts.assertEQ(testRandomRangeAndIntGeInterpreted(ia, ib), testRandomRangeAndIntGe(ia, ib));
            Asserts.assertEQ(testRandomRangeOrIntLtInterpreted(ia, ib), testRandomRangeOrIntLt(ia, ib));
            Asserts.assertEQ(testRandomRangeOrIntGeInterpreted(ia, ib), testRandomRangeOrIntGe(ia, ib));
            Asserts.assertEQ(testRandomRangeXorIntLtInterpreted(ia, ib), testRandomRangeXorIntLt(ia, ib));
            Asserts.assertEQ(testRandomRangeXorIntGeInterpreted(ia, ib), testRandomRangeXorIntGe(ia, ib));
            Asserts.assertEQ(testRandomRangeMaxIntLtInterpreted(ia, ib), testRandomRangeMaxIntLt(ia, ib));
            Asserts.assertEQ(testRandomRangeMaxIntGeInterpreted(ia, ib), testRandomRangeMaxIntGe(ia, ib));
            Asserts.assertEQ(testRandomRangeMinIntLtInterpreted(ia, ib), testRandomRangeMinIntLt(ia, ib));
            Asserts.assertEQ(testRandomRangeMinIntGeInterpreted(ia, ib), testRandomRangeMinIntGe(ia, ib));
        }
    }

    @DontCompile
    private void assertResultL() {
        long a = GEN_LONG.next();
        long b = GEN_LONG.next();
        long aNeg = Math.min(HI_NEG_L, Math.max(LO_NEG_L, a));
        long bNeg = Math.min(HI_NEG_L, Math.max(LO_NEG_L, b));
        long aPos = Math.min(HI_POS_L, Math.max(LO_POS_L, a));
        long bPos = Math.min(HI_POS_L, Math.max(LO_POS_L, b));

        Asserts.assertEQ((a & bNeg) < 0, knownAndInputLongLtR(a, b));
        Asserts.assertEQ((aNeg & b) < 0, knownAndInputLongLtL(a, b));
        Asserts.assertEQ((a & bNeg) >= 0, knownAndInputLongGeR(a, b));
        Asserts.assertEQ((aNeg & b) >= 0, knownAndInputLongGeL(a, b));

        Asserts.assertEQ(Math.max(a, bNeg) < 0, knownMaxInputLongLtR(a, b));
        Asserts.assertEQ(Math.max(aNeg, b) < 0, knownMaxInputLongLtL(a, b));
        Asserts.assertEQ(Math.max(a, bNeg) >= 0, knownMaxInputLongGeR(a, b));
        Asserts.assertEQ(Math.max(aNeg, b) >= 0, knownMaxInputLongGeL(a, b));

        Asserts.assertEQ((a | bPos) < 0, knownOrInputLongLtR(a, b));
        Asserts.assertEQ((aPos | b) < 0, knownOrInputLongLtL(a, b));
        Asserts.assertEQ((a | bPos) >= 0, knownOrInputLongGeR(a, b));
        Asserts.assertEQ((aPos | b) >= 0, knownOrInputLongGeL(a, b));

        Asserts.assertEQ(Math.min(a, bPos) < 0, knownMinInputLongLtR(a, b));
        Asserts.assertEQ(Math.min(aPos, b) < 0, knownMinInputLongLtL(a, b));
        Asserts.assertEQ(Math.min(a, bPos) >= 0, knownMinInputLongGeR(a, b));
        Asserts.assertEQ(Math.min(aPos, b) >= 0, knownMinInputLongGeL(a, b));

        Asserts.assertEQ((a ^ bNeg) < 0, knownXorInputLongLtRNeg(a, b));
        Asserts.assertEQ((aNeg ^ b) < 0, knownXorInputLongLtLNeg(a, b));
        Asserts.assertEQ((a ^ bNeg) >= 0, knownXorInputLongGeRNeg(a, b));
        Asserts.assertEQ((aNeg ^ b) >= 0, knownXorInputLongGeLNeg(a, b));
        Asserts.assertEQ((a ^ bPos) < 0, knownXorInputLongLtRPos(a, b));
        Asserts.assertEQ((aPos ^ b) < 0, knownXorInputLongLtLPos(a, b));
        Asserts.assertEQ((a ^ bPos) >= 0, knownXorInputLongGeRPos(a, b));
        Asserts.assertEQ((aPos ^ b) >= 0, knownXorInputLongGeLPos(a, b));

        for (int i = 0; i < 10; i++) {
            long la = GEN_LONG.next();
            long lb = GEN_LONG.next();
            Asserts.assertEQ(testRandomRangeAndLongLtInterpreted(la, lb), testRandomRangeAndLongLt(la, lb));
            Asserts.assertEQ(testRandomRangeAndLongGeInterpreted(la, lb), testRandomRangeAndLongGe(la, lb));
            Asserts.assertEQ(testRandomRangeOrLongLtInterpreted(la, lb), testRandomRangeOrLongLt(la, lb));
            Asserts.assertEQ(testRandomRangeOrLongGeInterpreted(la, lb), testRandomRangeOrLongGe(la, lb));
            Asserts.assertEQ(testRandomRangeXorLongLtInterpreted(la, lb), testRandomRangeXorLongLt(la, lb));
            Asserts.assertEQ(testRandomRangeXorLongGeInterpreted(la, lb), testRandomRangeXorLongGe(la, lb));
            Asserts.assertEQ(testRandomRangeMaxLongLtInterpreted(la, lb), testRandomRangeMaxLongLt(la, lb));
            Asserts.assertEQ(testRandomRangeMaxLongGeInterpreted(la, lb), testRandomRangeMaxLongGe(la, lb));
            Asserts.assertEQ(testRandomRangeMinLongLtInterpreted(la, lb), testRandomRangeMinLongLt(la, lb));
            Asserts.assertEQ(testRandomRangeMinLongGeInterpreted(la, lb), testRandomRangeMinLongGe(la, lb));
        }
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputIntLtR(int a, int b) {
        return (a & Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputIntLtL(int a, int b) {
        return (Math.min(HI_NEG_I, Math.max(LO_NEG_I, a)) & b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputIntGeR(int a, int b) {
        return (a & Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputIntGeL(int a, int b) {
        return (Math.min(HI_NEG_I, Math.max(LO_NEG_I, a)) & b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputIntLtR(int a, int b) {
        return Math.max(a, Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputIntLtL(int a, int b) {
        return Math.max(Math.min(HI_NEG_I, Math.max(LO_NEG_I, a)), b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputIntGeR(int a, int b) {
        return Math.max(a, Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputIntGeL(int a, int b) {
        return Math.max(Math.min(HI_NEG_I, Math.max(LO_NEG_I, a)), b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputIntLtR(int a, int b) {
        return (a | Math.min(HI_POS_I, Math.max(LO_POS_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputIntLtL(int a, int b) {
        return (Math.min(HI_POS_I, Math.max(LO_POS_I, a)) | b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputIntGeR(int a, int b) {
        return (a | Math.min(HI_POS_I, Math.max(LO_POS_I, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputIntGeL(int a, int b) {
        return (Math.min(HI_POS_I, Math.max(LO_POS_I, a)) | b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputIntLtR(int a, int b) {
        return Math.min(a, Math.min(HI_POS_I, Math.max(LO_POS_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputIntLtL(int a, int b) {
        return Math.min(Math.min(HI_POS_I, Math.max(LO_POS_I, a)), b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputIntGeR(int a, int b) {
        return Math.min(a, Math.min(HI_POS_I, Math.max(LO_POS_I, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputIntGeL(int a, int b) {
        return Math.min(Math.min(HI_POS_I, Math.max(LO_POS_I, a)), b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputIntLtRNeg(int a, int b) {
        return (a ^ Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputIntLtLNeg(int a, int b) {
        return (Math.min(HI_NEG_I, Math.max(LO_NEG_I, a)) ^ b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputIntGeRNeg(int a, int b) {
        return (a ^ Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputIntGeLNeg(int a, int b) {
        return (Math.min(HI_NEG_I, Math.max(LO_NEG_I, a)) ^ b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputIntLtRPos(int a, int b) {
        return (a ^ Math.min(HI_POS_I, Math.max(LO_POS_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputIntLtLPos(int a, int b) {
        return (Math.min(HI_POS_I, Math.max(LO_POS_I, a)) ^ b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputIntGeRPos(int a, int b) {
        return (a ^ Math.min(HI_POS_I, Math.max(LO_POS_I, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputIntGeLPos(int a, int b) {
        return (Math.min(HI_POS_I, Math.max(LO_POS_I, a)) ^ b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputLongLtR(long a, long b) {
        return (a & Math.min(HI_NEG_I, Math.max(LO_NEG_I, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputLongLtL(long a, long b) {
        return (Math.min(HI_NEG_L, Math.max(LO_NEG_L, a)) & b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputLongGeR(long a, long b) {
        return (a & Math.min(HI_NEG_L, Math.max(LO_NEG_L, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.AND, IRNode.MAX, IRNode.MIN})
    public boolean knownAndInputLongGeL(long a, long b) {
        return (Math.min(HI_NEG_L, Math.max(LO_NEG_L, a)) & b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputLongLtR(long a, long b) {
        return Math.max(a, Math.min(HI_NEG_L, Math.max(LO_NEG_L, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputLongLtL(long a, long b) {
        return Math.max(Math.min(HI_NEG_L, Math.max(LO_NEG_L, a)), b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputLongGeR(long a, long b) {
        return Math.max(a, Math.min(HI_NEG_L, Math.max(LO_NEG_L, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMaxInputLongGeL(long a, long b) {
        return Math.max(Math.min(HI_NEG_L, Math.max(LO_NEG_L, a)), b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputLongLtR(long a, long b) {
        return (a | Math.min(HI_POS_L, Math.max(LO_POS_L, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputLongLtL(long a, long b) {
        return (Math.min(HI_POS_L, Math.max(LO_POS_L, a)) | b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputLongGeR(long a, long b) {
        return (a | Math.min(HI_POS_L, Math.max(LO_POS_L, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.OR, IRNode.MAX, IRNode.MIN})
    public boolean knownOrInputLongGeL(long a, long b) {
        return (Math.min(HI_POS_L, Math.max(LO_POS_L, a)) | b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLongLtRNeg(long a, long b) {
        return (a ^ Math.min(HI_NEG_L, Math.max(LO_NEG_L, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputLongLtR(long a, long b) {
        return Math.min(a, Math.min(HI_POS_L, Math.max(LO_POS_L, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputLongLtL(long a, long b) {
        return Math.min(Math.min(HI_POS_L, Math.max(LO_POS_L, a)), b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputLongGeR(long a, long b) {
        return Math.min(a, Math.min(HI_POS_L, Math.max(LO_POS_L, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.MAX, IRNode.MIN})
    public boolean knownMinInputLongGeL(long a, long b) {
        return Math.min(Math.min(HI_POS_L, Math.max(LO_POS_L, a)), b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLongLtLNeg(long a, long b) {
        return (Math.min(HI_NEG_L, Math.max(LO_NEG_L, a)) ^ b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLongGeRNeg(long a, long b) {
        return (a ^ Math.min(HI_NEG_L, Math.max(LO_NEG_L, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLongGeLNeg(long a, long b) {
        return (Math.min(HI_NEG_L, Math.max(LO_NEG_L, a)) ^ b) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLongLtRPos(long a, long b) {
        return (a ^ Math.min(HI_POS_L, Math.max(LO_POS_L, b))) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLongLtLPos(long a, long b) {
        return (Math.min(HI_POS_L, Math.max(LO_POS_L, a)) ^ b) < 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLongGeRPos(long a, long b) {
        return (a ^ Math.min(HI_POS_L, Math.max(LO_POS_L, b))) >= 0;
    }

    @Test
    @IR(failOn = {IRNode.XOR, IRNode.MAX, IRNode.MIN})
    public boolean knownXorInputLongGeLPos(long a, long b) {
        return (Math.min(HI_POS_L, Math.max(LO_POS_L, a)) ^ b) >= 0;
    }

    private static final IntRange INT_RANGE_1 = IntRange.generate(GEN_INT);
    private static final IntRange INT_RANGE_2 = IntRange.generate(GEN_INT);

    @Test
    public boolean testRandomRangeAndIntLt(int a, int b) {
        return (INT_RANGE_1.clamp(a) & INT_RANGE_2.clamp(b)) < 0;
    }

    @DontCompile
    public boolean testRandomRangeAndIntLtInterpreted(int a, int b) {
        return (INT_RANGE_1.clamp(a) & INT_RANGE_2.clamp(b)) < 0;
    }

    @Test
    public boolean testRandomRangeAndIntGe(int a, int b) {
        return (INT_RANGE_1.clamp(a) & INT_RANGE_2.clamp(b)) >= 0;
    }

    @DontCompile
    public boolean testRandomRangeAndIntGeInterpreted(int a, int b) {
        return (INT_RANGE_1.clamp(a) & INT_RANGE_2.clamp(b)) >= 0;
    }

    @Test
    public boolean testRandomRangeOrIntLt(int a, int b) {
        return (INT_RANGE_1.clamp(a) | INT_RANGE_2.clamp(b)) < 0;
    }

    @DontCompile
    public boolean testRandomRangeOrIntLtInterpreted(int a, int b) {
        return (INT_RANGE_1.clamp(a) | INT_RANGE_2.clamp(b)) < 0;
    }

    @Test
    public boolean testRandomRangeOrIntGe(int a, int b) {
        return (INT_RANGE_1.clamp(a) | INT_RANGE_2.clamp(b)) >= 0;
    }

    @DontCompile
    public boolean testRandomRangeOrIntGeInterpreted(int a, int b) {
        return (INT_RANGE_1.clamp(a) | INT_RANGE_2.clamp(b)) >= 0;
    }

    @Test
    public boolean testRandomRangeXorIntLt(int a, int b) {
        return (INT_RANGE_1.clamp(a) ^ INT_RANGE_2.clamp(b)) < 0;
    }

    @DontCompile
    public boolean testRandomRangeXorIntLtInterpreted(int a, int b) {
        return (INT_RANGE_1.clamp(a) ^ INT_RANGE_2.clamp(b)) < 0;
    }

    @Test
    public boolean testRandomRangeXorIntGe(int a, int b) {
        return (INT_RANGE_1.clamp(a) ^ INT_RANGE_2.clamp(b)) >= 0;
    }

    @DontCompile
    public boolean testRandomRangeXorIntGeInterpreted(int a, int b) {
        return (INT_RANGE_1.clamp(a) ^ INT_RANGE_2.clamp(b)) >= 0;
    }

    @Test
    public boolean testRandomRangeMaxIntLt(int a, int b) {
        return Math.max(INT_RANGE_1.clamp(a), INT_RANGE_2.clamp(b)) < 0;
    }

    @DontCompile
    public boolean testRandomRangeMaxIntLtInterpreted(int a, int b) {
        return Math.max(INT_RANGE_1.clamp(a), INT_RANGE_2.clamp(b)) < 0;
    }

    @Test
    public boolean testRandomRangeMaxIntGe(int a, int b) {
        return Math.max(INT_RANGE_1.clamp(a), INT_RANGE_2.clamp(b)) >= 0;
    }

    @DontCompile
    public boolean testRandomRangeMaxIntGeInterpreted(int a, int b) {
        return Math.max(INT_RANGE_1.clamp(a), INT_RANGE_2.clamp(b)) >= 0;
    }

    @Test
    public boolean testRandomRangeMinIntLt(int a, int b) {
        return Math.min(INT_RANGE_1.clamp(a), INT_RANGE_2.clamp(b)) < 0;
    }

    @DontCompile
    public boolean testRandomRangeMinIntLtInterpreted(int a, int b) {
        return Math.min(INT_RANGE_1.clamp(a), INT_RANGE_2.clamp(b)) < 0;
    }

    @Test
    public boolean testRandomRangeMinIntGe(int a, int b) {
        return Math.min(INT_RANGE_1.clamp(a), INT_RANGE_2.clamp(b)) >= 0;
    }

    @DontCompile
    public boolean testRandomRangeMinIntGeInterpreted(int a, int b) {
        return Math.min(INT_RANGE_1.clamp(a), INT_RANGE_2.clamp(b)) >= 0;
    }

    private static final LongRange LONG_RANGE_1 = LongRange.generate(GEN_LONG);
    private static final LongRange LONG_RANGE_2 = LongRange.generate(GEN_LONG);

    @Test
    public boolean testRandomRangeAndLongLt(long a, long b) {
        return (LONG_RANGE_1.clamp(a) & LONG_RANGE_2.clamp(b)) < 0;
    }

    @DontCompile
    public boolean testRandomRangeAndLongLtInterpreted(long a, long b) {
        return (LONG_RANGE_1.clamp(a) & LONG_RANGE_2.clamp(b)) < 0;
    }

    @Test
    public boolean testRandomRangeAndLongGe(long a, long b) {
        return (LONG_RANGE_1.clamp(a) & LONG_RANGE_2.clamp(b)) >= 0;
    }

    @DontCompile
    public boolean testRandomRangeAndLongGeInterpreted(long a, long b) {
        return (LONG_RANGE_1.clamp(a) & LONG_RANGE_2.clamp(b)) >= 0;
    }

    @Test
    public boolean testRandomRangeOrLongLt(long a, long b) {
        return (LONG_RANGE_1.clamp(a) | LONG_RANGE_2.clamp(b)) < 0;
    }

    @DontCompile
    public boolean testRandomRangeOrLongLtInterpreted(long a, long b) {
        return (LONG_RANGE_1.clamp(a) | LONG_RANGE_2.clamp(b)) < 0;
    }

    @Test
    public boolean testRandomRangeOrLongGe(long a, long b) {
        return (LONG_RANGE_1.clamp(a) | LONG_RANGE_2.clamp(b)) >= 0;
    }

    @DontCompile
    public boolean testRandomRangeOrLongGeInterpreted(long a, long b) {
        return (LONG_RANGE_1.clamp(a) | LONG_RANGE_2.clamp(b)) >= 0;
    }

    @Test
    public boolean testRandomRangeXorLongLt(long a, long b) {
        return (LONG_RANGE_1.clamp(a) ^ LONG_RANGE_2.clamp(b)) < 0;
    }

    @DontCompile
    public boolean testRandomRangeXorLongLtInterpreted(long a, long b) {
        return (LONG_RANGE_1.clamp(a) ^ LONG_RANGE_2.clamp(b)) < 0;
    }

    @Test
    public boolean testRandomRangeXorLongGe(long a, long b) {
        return (LONG_RANGE_1.clamp(a) ^ LONG_RANGE_2.clamp(b)) >= 0;
    }

    @DontCompile
    public boolean testRandomRangeXorLongGeInterpreted(long a, long b) {
        return (LONG_RANGE_1.clamp(a) ^ LONG_RANGE_2.clamp(b)) >= 0;
    }

    @Test
    public boolean testRandomRangeMaxLongLt(long a, long b) {
        return Math.max(LONG_RANGE_1.clamp(a), LONG_RANGE_2.clamp(b)) < 0;
    }

    @DontCompile
    public boolean testRandomRangeMaxLongLtInterpreted(long a, long b) {
        return Math.max(LONG_RANGE_1.clamp(a), LONG_RANGE_2.clamp(b)) < 0;
    }

    @Test
    public boolean testRandomRangeMaxLongGe(long a, long b) {
        return Math.max(LONG_RANGE_1.clamp(a), LONG_RANGE_2.clamp(b)) >= 0;
    }

    @DontCompile
    public boolean testRandomRangeMaxLongGeInterpreted(long a, long b) {
        return Math.max(LONG_RANGE_1.clamp(a), LONG_RANGE_2.clamp(b)) >= 0;
    }

    @Test
    public boolean testRandomRangeMinLongLt(long a, long b) {
        return Math.min(LONG_RANGE_1.clamp(a), LONG_RANGE_2.clamp(b)) < 0;
    }

    @DontCompile
    public boolean testRandomRangeMinLongLtInterpreted(long a, long b) {
        return Math.min(LONG_RANGE_1.clamp(a), LONG_RANGE_2.clamp(b)) < 0;
    }

    @Test
    public boolean testRandomRangeMinLongGe(long a, long b) {
        return Math.min(LONG_RANGE_1.clamp(a), LONG_RANGE_2.clamp(b)) >= 0;
    }

    @DontCompile
    public boolean testRandomRangeMinLongGeInterpreted(long a, long b) {
        return Math.min(LONG_RANGE_1.clamp(a), LONG_RANGE_2.clamp(b)) >= 0;
    }
}
