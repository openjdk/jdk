/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Arm Limited. All rights reserved.
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
import org.graalvm.compiler.lir.aarch64.AArch64ControlFlow;
import org.junit.Test;

import java.util.function.Predicate;

public class AArch64CbzTest extends AArch64MatchRuleTest {
    private static final Predicate<LIRInstruction> predicate = op -> (op instanceof AArch64ControlFlow.CompareBranchZeroOp);

    public static int equalsTo(int x) {
        if (x == 0) {
            return 1;
        } else {
            return x - 1;
        }
    }

    public static int notEqualsTo(int x) {
        if (x != 0) {
            return x + 2;
        } else {
            return 3;
        }
    }

    public static String isNull(String s) {
        if (s == null) {
            return "abc";
        } else {
            return s + "abc";
        }
    }

    public static String isNotNull(String s) {
        if (s != null) {
            return s + "abc";
        } else {
            return "abc";
        }
    }

    public static String objectEqualsNull(String s1, String s2) {
        if (s1.equals(null)) {
            return s1 + "abc";
        } else {
            return s2 + "abd";
        }
    }

    public static String objectEquals(String s1, String s2) {
        if (s1.equals(s2)) {
            return s1 + "abc";
        } else {
            return s2 + "abd";
        }
    }

    @Test
    public void testEqualsTo() {
        test("equalsTo", 0);
        test("equalsTo", 1);
        checkLIR("equalsTo", predicate, 1);
    }

    @Test
    public void testNotEqualsTo() {
        test("notEqualsTo", 0);
        test("notEqualsTo", 1);
        checkLIR("notEqualsTo", predicate, 1);
    }

    @Test
    public void testIsNull() {
        test("isNull", new Object[]{null});
        test("isNull", "abc");
        checkLIR("isNull", predicate, 1);
    }

    @Test
    public void testIsNotNull() {
        test("isNotNull", new Object[]{null});
        test("isNotNull", "abc");
        checkLIR("isNotNull", predicate, 1);
    }

    @Test
    public void testObjectEqualsNull() {
        test("objectEqualsNull", "ab", "ac");
        test("objectEqualsNull", "abc", "abc");
        checkLIR("objectEqualsNull", predicate, 1);
    }

    @Test
    public void testObjectEquals() {
        test("objectEquals", "ab", "ac");
        test("objectEquals", "abc", "abc");
        checkLIR("objectEquals", predicate, 0);
    }
}
