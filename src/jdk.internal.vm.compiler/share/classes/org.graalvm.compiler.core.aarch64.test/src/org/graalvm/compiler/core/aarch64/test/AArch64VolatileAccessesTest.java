/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import org.graalvm.compiler.core.test.MatchRuleTest;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.aarch64.AArch64Move;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Predicate;

import static org.junit.Assume.assumeTrue;

public class AArch64VolatileAccessesTest extends MatchRuleTest {
    private static volatile byte volatileByteField;
    private static volatile short volatileShortField;
    private static volatile int volatileIntField;
    private static volatile long volatileLongField;
    private static volatile float volatileFloatField;
    private static volatile double volatileDoubleField;

    private static Predicate<LIRInstruction> storePredicate(AArch64Kind kind) {
        return op -> (op instanceof AArch64Move.VolatileStoreOp && ((AArch64Move.VolatileStoreOp) op).getKind() == kind);
    }

    private static Predicate<LIRInstruction> loadPredicate(AArch64Kind kind) {
        return op -> (op instanceof AArch64Move.VolatileLoadOp && ((AArch64Move.VolatileLoadOp) op).getKind() == kind);
    }

    @Before
    public void checkAArch64() {
        assumeTrue("skipping AArch64 specific test", getTarget().arch instanceof AArch64);
    }

    public static byte volatileByteFieldLoad() {
        return volatileByteField;
    }

    @Test
    public void test01() {
        checkLIR("volatileByteFieldLoad", loadPredicate(AArch64Kind.BYTE), 1);
        volatileByteField = 42;
        test("volatileByteFieldLoad");
    }

    public static short volatileShortFieldLoad() {
        return volatileShortField;
    }

    @Test
    public void test02() {
        checkLIR("volatileShortFieldLoad", loadPredicate(AArch64Kind.WORD), 1);
        volatileShortField = 42;
        test("volatileShortFieldLoad");
    }

    public static int volatileIntFieldLoad() {
        return volatileIntField;
    }

    @Test
    public void test03() {
        checkLIR("volatileIntFieldLoad", loadPredicate(AArch64Kind.DWORD), 1);
        volatileIntField = 42;
        test("volatileIntFieldLoad");
    }

    public static long volatileLongFieldLoad() {
        return volatileLongField;
    }

    @Test
    public void test04() {
        checkLIR("volatileLongFieldLoad", loadPredicate(AArch64Kind.QWORD), 1);
        volatileLongField = 42;
        test("volatileLongFieldLoad");
    }

    public static float volatileFloatFieldLoad() {
        return volatileFloatField;
    }

    @Test
    public void test05() {
        checkLIR("volatileFloatFieldLoad", loadPredicate(AArch64Kind.SINGLE), 1);
        volatileFloatField = 42;
        test("volatileFloatFieldLoad");
    }

    public static double volatileDoubleFieldLoad() {
        return volatileDoubleField;
    }

    @Test
    public void test06() {
        checkLIR("volatileDoubleFieldLoad", loadPredicate(AArch64Kind.DOUBLE), 1);
        volatileDoubleField = 42;
        test("volatileDoubleFieldLoad");
    }

    public static void volatileByteFieldStore(byte v) {
        volatileByteField = v;
    }

    @Test
    public void test07() {
        checkLIR("volatileByteFieldStore", storePredicate(AArch64Kind.BYTE), 1);
        executeActual(getResolvedJavaMethod("volatileByteFieldStore"), (byte) 0x42);
        Assert.assertEquals(volatileByteField, 0x42);
    }

    public static void volatileShortFieldStore(short v) {
        volatileShortField = v;
    }

    @Test
    public void test08() {
        checkLIR("volatileShortFieldStore", storePredicate(AArch64Kind.WORD), 1);
        executeActual(getResolvedJavaMethod("volatileShortFieldStore"), (short) 0x42);
        Assert.assertEquals(volatileShortField, 0x42);
    }

    public static void volatileIntFieldStore(int v) {
        volatileIntField = v;
    }

    @Test
    public void test09() {
        checkLIR("volatileIntFieldStore", storePredicate(AArch64Kind.DWORD), 1);
        executeActual(getResolvedJavaMethod("volatileIntFieldStore"), 0x42);
        Assert.assertEquals(volatileIntField, 0x42);
    }

    public static void volatileLongFieldStore(int v) {
        volatileLongField = v;
    }

    @Test
    public void test10() {
        checkLIR("volatileLongFieldStore", storePredicate(AArch64Kind.QWORD), 1);
        executeActual(getResolvedJavaMethod("volatileLongFieldStore"), 0x42);
        Assert.assertEquals(volatileLongField, 0x42);
    }

    public static void volatileFloatFieldStore(float v) {
        volatileFloatField = v;
    }

    @Test
    public void test11() {
        checkLIR("volatileFloatFieldStore", storePredicate(AArch64Kind.SINGLE), 1);
        executeActual(getResolvedJavaMethod("volatileFloatFieldStore"), (float) 0x42);
        Assert.assertEquals(volatileFloatField, 0x42, 0);
    }

    public static void volatileDoubleFieldStore(double v) {
        volatileDoubleField = v;
    }

    @Test
    public void test12() {
        checkLIR("volatileDoubleFieldStore", storePredicate(AArch64Kind.DOUBLE), 1);
        executeActual(getResolvedJavaMethod("volatileDoubleFieldStore"), (double) 0x42);
        Assert.assertEquals(volatileDoubleField, 0x42, 0);
    }
}
