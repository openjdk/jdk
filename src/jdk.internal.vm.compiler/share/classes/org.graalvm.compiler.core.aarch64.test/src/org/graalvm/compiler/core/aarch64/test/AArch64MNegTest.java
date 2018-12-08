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

import java.util.function.Predicate;
import org.graalvm.compiler.lir.LIRInstruction;
import org.junit.Test;

public class AArch64MNegTest extends AArch64MatchRuleTest {
    private static final Predicate<LIRInstruction> predicate = op -> (op.name().toUpperCase().equals("MNEG"));

    private void testAndCheckLIR(String methodName, int insCount, Object... args) {
        assert args.length >= 2;
        test(methodName, args);
        checkLIR(methodName, predicate, insCount);
    }

    /**
     * mneg rule test with int type.
     */
    private static int mulNegInt(int x, int y) {
        return ((x + 1) * 2 + 3) * (-y);
    }

    private static int mulZeroSubInt(int x, int y) {
        return x * (0 - y);
    }

    private static int mulSubZeroInt(int x, int y) {
        return x * (y - 0);
    }

    public static int multiplyNegateInt(int x, int y) {
        return mulNegInt(x, y) | mulZeroSubInt(y, x) | mulSubZeroInt(x, y);
    }

    @Test
    public void testMultiplyNegateInt() {
        testAndCheckLIR("multiplyNegateInt", 2, 7, -1);
    }

    public static int negMulInt(int x, int y) {
        return (-((x + 1) * y));
    }

    @Test
    public void testNegateMultiplyInt() {
        testAndCheckLIR("negMulInt", 1, 7, -1);
    }

    /**
     * mneg rule test with long type.
     */
    private static long mulNegLong(long x, long y) {
        return ((x + 1) * 2 + 3) * (-y);
    }

    private static long mulZeroSubLong(long x, long y) {
        return x * (0 - y);
    }

    private static long mulSubZeroLong(long x, long y) {
        return x * (y - 0);
    }

    public static long multiplyNegateLong(long x, long y) {
        return mulNegLong(x, y) | mulZeroSubLong(y, x) | mulSubZeroLong(x, y);
    }

    @Test
    public void testMultiplyNegateLong() {
        testAndCheckLIR("multiplyNegateLong", 2, 7L, -1L);
    }

    public static long negMulLong(long x, long y) {
        return (-((x + 1) * y));
    }

    @Test
    public void testNegateMultiplyLong() {
        testAndCheckLIR("negMulLong", 1, 7L, -1L);
    }

    /**
     * check the case that mneg rule does not work at the reason of multi-use.
     */
    public static int multiplyNegateNoMatch(int x, int y) {
        return mulNegInt(x, y) + mulZeroSubInt(x, y) + mulSubZeroInt(x, y);
    }

    @Test
    public void testMultiplyNegateNoMatch() {
        testAndCheckLIR("multiplyNegateNoMatch", 0, 7, -1);
    }

    /**
     * check the case that mneg rule does not work at the reason of data type.
     */
    private static float mulNegFloat(float x, float y) {
        return ((x + 1) * 2 + 3) * (-y);
    }

    private static float mulZeroSubFloat(float x, float y) {
        return x * (0 - y);
    }

    private static float mulSubZeroFloat(float x, float y) {
        return x * (y - 0);
    }

    public static float multiplyNegateFloat(float x, float y) {
        return mulNegFloat(x, y) + mulZeroSubFloat(y, x) + mulSubZeroFloat(x, y);
    }

    @Test
    public void testMultiplyNegateFloat() {
        testAndCheckLIR("multiplyNegateFloat", 0, 7F, -1F);
    }
}
