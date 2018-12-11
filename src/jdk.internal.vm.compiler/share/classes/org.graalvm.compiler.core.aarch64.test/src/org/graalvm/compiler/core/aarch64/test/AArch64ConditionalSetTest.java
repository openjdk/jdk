/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Arm Limited and affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.aarch64.AArch64ControlFlow;
import org.junit.Test;

import java.util.function.Predicate;

public class AArch64ConditionalSetTest extends AArch64MatchRuleTest {
    private static final Predicate<LIRInstruction> predicate = op -> (op instanceof AArch64ControlFlow.CondSetOp);

    /**
     * conditionalSet test for integer tests condition.
     */
    public static int conditionalSetEQZero(int m) {
        if ((m & 2) == 0) {
            return 1;
        }
        return 0;
    }

    @Test
    public void testConditionalSetEQZero() {
        test("conditionalSetEQZero", 0);
        test("conditionalSetEQZero", 2);
        checkLIR("conditionalSetEQZero", predicate, 1);
    }

    /**
     * conditionalSet test for integer equals condition.
     */
    public static int conditionalSetEQ(int m, int n) {
        if (m == n) {
            return 1;
        }
        return 0;
    }

    @Test
    public void testConditionalSetEQ() {
        test("conditionalSetEQ", 1, 2);
        test("conditionalSetEQ", 2, 2);
        checkLIR("conditionalSetEQ", predicate, 1);
    }

    /**
     * conditionalSet test for integer less than condition.
     */
    public static int conditionalSetLT(int m, int n) {
        if (m < n) {
            return 1;
        }
        return 0;
    }

    @Test
    public void testConditionalSetLT() {
        test("conditionalSetLT", 1, 2);
        test("conditionalSetLT", 3, 2);
        checkLIR("conditionalSetLT", predicate, 1);
    }

    /**
     * conditionalSet test for integer below condition.
     */
    public static boolean conditionalSetBT(int m, int n) {
        return UnsignedMath.belowThan(m, n);
    }

    @Test
    public void testConditionalSetBT() {
        test("conditionalSetBT", 1, 2);
        test("conditionalSetBT", 3, 2);
        checkLIR("conditionalSetBT", predicate, 1);
    }

    /**
     * conditionalSet test for float point equals condition.
     */
    public static int conditionalSetFPEQ(float m, float n) {
        if (m == n) {
            return 1;
        }
        return 0;
    }

    @Test
    public void testConditionalSetFPEQ() {
        test("conditionalSetFPEQ", 1.0f, 2.0f);
        test("conditionalSetFPEQ", 2.0f, 2.0f);
        checkLIR("conditionalSetFPEQ", predicate, 1);
    }

    /**
     * conditionalSet test for float point less than condition.
     */
    public static int conditionalSetFPLT(float m, float n) {
        if (m < n) {
            return 1;
        }
        return 0;
    }

    @Test
    public void testConditionalSetFPLT() {
        test("conditionalSetFPLT", 1.0f, 2.0f);
        test("conditionalSetFPLT", 3.0f, 2.0f);
        checkLIR("conditionalSetFPLT", predicate, 1);
    }

    /**
     * conditionalSet test for object equals condition.
     */
    public static int conditionalSetObjectEQ(Integer m, Integer n) {
        if (m == n) {
            return 1;
        }
        return 0;
    }

    @Test
    public void testConditionalSetObjectEQ() {
        test("conditionalSetObjectEQ", Integer.valueOf(1), Integer.valueOf(2));
        test("conditionalSetObjectEQ", Integer.valueOf(2), Integer.valueOf(2));
        checkLIR("conditionalSetObjectEQ", predicate, 1);
    }

    /**
     * conditionalSet test for null check condition.
     */
    public static int conditionalSetIsNull(Object obj) {
        if (obj == null) {
            return 1;
        }
        return 0;
    }

    @Test
    public void testConditionalSetIsNull() {
        Object obj = null;
        test("conditionalSetIsNull", obj);
        test("conditionalSetIsNull", Integer.valueOf(1));
        checkLIR("conditionalSetIsNull", predicate, 1);
    }

    /**
     * conditionalSet test when trueValue and falseValue need to be exchanged.
     */
    public static int conditionalSetSwap(int m, int n) {
        if (m == n) {
            return 0;
        }
        return 1;
    }

    @Test
    public void testConditionalSetSwap() {
        test("conditionalSetSwap", 1, 2);
        test("conditionalSetSwap", 2, 2);
        checkLIR("conditionalSetSwap", predicate, 1);
    }

    /**
     * conditionalSet test for result with long type.
     */
    public static long conditionalSetLong(int m, int n) {
        if (m == n) {
            return 1;
        }
        return 0;
    }

    @Test
    public void testConditionalSetLong() {
        test("conditionalSetLong", 1, 2);
        test("conditionalSetLong", 2, 2);
        checkLIR("conditionalSetLong", predicate, 1);
    }
}
