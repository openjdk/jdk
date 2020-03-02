/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp.BinaryOp;
import org.junit.Test;

import java.util.function.Predicate;

public class AArch64BitwiseLogicalNotTest extends AArch64MatchRuleTest {
    private static final String BIC = AArch64ArithmeticOp.BIC.name();
    private static final String ORN = AArch64ArithmeticOp.ORN.name();
    private static final String EON = AArch64ArithmeticOp.EON.name();

    private void test(String methodName, String opName, Object... args) {
        test(methodName, args);
        Predicate<LIRInstruction> predicate = op -> {
            if (op instanceof BinaryOp && op.name().equalsIgnoreCase(opName)) {
                return true;
            }
            return false;
        };
        checkLIR(methodName, predicate, 1);
    }

    // Tests for and not.
    public int andNotInt1(int m, int n) {
        return n & (~m);
    }

    @Test
    public void testAndNotInt1() {
        test("andNotInt1", BIC, 5, 6);
    }

    public int andNotInt2(int m, int n) {
        return n & (m ^ 0XFFFFFFFF);
    }

    @Test
    public void testAndNotInt2() {
        test("andNotInt2", BIC, 325, -1);
    }

    public long andNotLong(long m, long n) {
        return m & (n ^ -1L);
    }

    @Test
    public void testAndNotLong() {
        test("andNotLong", BIC, 3L, 425L);
    }

    // Tests for or not.
    public int orNotInt(int m, int n) {
        return (n ^ 0XFFFFFFFF) | m;
    }

    @Test
    public void testOrNotInt() {
        test("orNotInt", ORN, -1, Integer.MAX_VALUE);
    }

    public long orNotLong(long m, long n) {
        return m | (~n);
    }

    @Test
    public void testOrNotLong() {
        test("orNotLong", ORN, 23L, -1L);
    }

    // Tests for xor not.
    public int xorNotInt(int m, int n) {
        return (~n) ^ m;
    }

    @Test
    public void testXorNotInt() {
        test("xorNotInt", EON, 4132, 24);
    }

    public long xorNotLong(long m, long n) {
        return m ^ (~n);
    }

    @Test
    public void testXorNotLong() {
        test("xorNotLong", EON, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    // Tests for not xor.
    public int notXorInt1(int m, int n) {
        return ~(m ^ n);
    }

    @Test
    public void testNotXorInt1() {
        test("notXorInt1", EON, 235, 98);
    }

    public int notXorInt2(int m, int n) {
        return (m ^ n) ^ 0XFFFFFFFF;
    }

    @Test
    public void testNotXorInt2() {
        test("notXorInt2", EON, 245, 34654);
    }

    public long notXorLong(long m, long n) {
        return ~(m ^ n);
    }

    @Test
    public void testNotXorLong() {
        test("notXorLong", EON, 324L, 235L);
    }
}
