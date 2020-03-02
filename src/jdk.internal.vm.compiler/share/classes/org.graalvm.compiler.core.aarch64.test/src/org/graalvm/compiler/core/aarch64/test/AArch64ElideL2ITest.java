/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2020, Arm Limited. All rights reserved.
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



package org.graalvm.compiler.core.aarch64.test;

import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp.BinaryConstOp;
import org.junit.Test;

import java.util.function.Predicate;

public class AArch64ElideL2ITest extends AArch64MatchRuleTest {
    private static final Predicate<LIRInstruction> predicate = op -> {
        if (op instanceof BinaryConstOp && op.name().toUpperCase().equals("AND")) {
            return true;
        }
        return false;
    };

    public int addWithSingleL2I(long m) {
        return (int) m + 100;
    }

    @Test
    public void testAddWithSingleL2I() {
        test("addWithSingleL2I", 5L);
        checkLIR("addWithSingleL2I", predicate, 0);
    }

    public int addWithTwoL2I(long m, long n) {
        return (int) m + (int) n;
    }

    @Test
    public void testAddWithTwoL2I() {
        test("addWithTwoL2I", 5L, 0x1FFFFFFFFL);
        checkLIR("addWithTwoL2I", predicate, 0);
    }

    public int addWithTwoNarrow(long m, long n) {
        return (int) m + (short) n;
    }

    @Test
    public void testAddWithTwoNarrow() {
        test("addWithTwoNarrow", 0x80000000L, 6L);
        checkLIR("addWithTwoNarrow", predicate, 0);
    }

    public int subSingleL2I(int m, long n) {
        return m - (int) n;
    }

    @Test
    public void testSubSingleL2I() {
        test("subSingleL2I", 13, 40L);
        checkLIR("subSingleL2I", predicate, 0);
    }

    public int shiftWithSingleL2I(long m) {
        return ((int) m) >> 5;
    }

    @Test
    public void testShiftWithSingleL2I() {
        test("shiftWithSingleL2I", 234L);
        checkLIR("shiftWithSingleL2I", predicate, 0);
    }

    public int shiftWithTwoL2I(long m, long n) {
        return (int) m << (int) n;
    }

    @Test
    public void testShiftWithTwoL2I() {
        test("shiftWithTwoL2I", 234L, 3L);
        checkLIR("shiftWithTwoL2I", predicate, 0);
    }

    public long shiftLongWithL2I(long a, int m) {
        return a + ((m & 0xFFFFFFFFL) << (int) a);
    }

    @Test
    public void testShiftLongWithL2I() {
        test("shiftLongWithL2I", 0xFFFFFFFFL, 123);
        checkLIR("shiftLongWithL2I", predicate, 1);
    }

    public int logicWithTwoL2I(long m, long n) {
        return (int) m | (int) n;
    }

    @Test
    public void testLogicWithTwoL2I() {
        test("logicWithTwoL2I", 234L, 3L);
        checkLIR("logicWithTwoL2I", predicate, 0);
    }

    public int negateL2I(long m) {
        return -((int) m);
    }

    @Test
    public void testNegateL2I() {
        test("negateL2I", 0xFFFFFFFFL);
        checkLIR("negateL2I", predicate, 0);
    }

    public int notL2I(long m) {
        return ~((int) m);
    }

    @Test
    public void testNotL2I() {
        test("notL2I", 0xFFFFFFFFL);
        checkLIR("notL2I", predicate, 0);
    }
}
