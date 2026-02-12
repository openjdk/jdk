/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
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
 * or visit https://www.oracle.com if you need additional information
 * or have any questions.
 */

/*
 * @test
 * @bug 8360123
 * @summary IR test to verify DecimalDigits.appendPair/appendQuad optimization.
 *          These methods now use append(char) calls which can be optimized
 *          by StringOpts to generate StoreB operations for Latin1 chars.
 * @requires vm.compiler2.enabled
 *
 * @library /test/lib /
 * @run driver compiler.stringopts.TestDecimalDigitsLatin1Optimization
 */

package compiler.stringopts;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.internal.util.DecimalDigits;

/**
 * This IR test verifies that DecimalDigits.appendPair and appendQuad
 * are optimized by the StringOpts pass.
 *
 * After the change to use append(char) instead of uncheckedNewStringWithLatin1Bytes:
 * - appendPair: two append(char) calls with Latin1 digits ('0'-'9')
 * - appendQuad: four append(char) calls with Latin1 digits ('0'-'9')
 *
 * The StringOpts optimization should detect these consecutive Latin1 chars
 * and generate adjacent StoreB operations, which MergeStores can then
 * combine into larger stores (StoreC/StoreI).
 *
 * Key insight: The digits '0'-'9' have values 0x30-0x39, which are all <= 0xFF.
 * The JIT can determine this through type propagation from the DIGITS array.
 */
public class TestDecimalDigitsLatin1Optimization {

    public static void main(String[] args) {
        TestFramework.run();
    }

    // ========================================
    // appendPair Tests
    // ========================================

    /**
     * Test appendPair with value 0 -> "00".
     * Should generate 2 StoreB operations after StringOpts.
     */
    @Test
    @IR(counts = {IRNode.STORE_B, ">= 2"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testAppendPairZero() {
        StringBuilder sb = new StringBuilder();
        DecimalDigits.appendPair(sb, 0);
        return sb.toString();
    }

    /**
     * Test appendPair with value 42 -> "42".
     */
    @Test
    @IR(counts = {IRNode.STORE_B, ">= 2"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testAppendPair42() {
        StringBuilder sb = new StringBuilder();
        DecimalDigits.appendPair(sb, 42);
        return sb.toString();
    }

    /**
     * Test appendPair with value 99 -> "99".
     */
    @Test
    @IR(counts = {IRNode.STORE_B, ">= 2"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testAppendPair99() {
        StringBuilder sb = new StringBuilder();
        DecimalDigits.appendPair(sb, 99);
        return sb.toString();
    }

    /**
     * Test two appendPair calls in sequence.
     * Should generate 4 StoreB operations (can be merged to 1 StoreI).
     */
    @Test
    @IR(counts = {IRNode.STORE_B, ">= 4"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testTwoAppendPair() {
        StringBuilder sb = new StringBuilder();
        DecimalDigits.appendPair(sb, 12);
        DecimalDigits.appendPair(sb, 34);
        return sb.toString();
    }

    // ========================================
    // appendQuad Tests
    // ========================================

    /**
     * Test appendQuad with value 0 -> "0000".
     * Should generate 4 StoreB operations after StringOpts.
     */
    @Test
    @IR(counts = {IRNode.STORE_B, ">= 4"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testAppendQuadZero() {
        StringBuilder sb = new StringBuilder();
        DecimalDigits.appendQuad(sb, 0);
        return sb.toString();
    }

    /**
     * Test appendQuad with value 1234 -> "1234".
     */
    @Test
    @IR(counts = {IRNode.STORE_B, ">= 4"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testAppendQuad1234() {
        StringBuilder sb = new StringBuilder();
        DecimalDigits.appendQuad(sb, 1234);
        return sb.toString();
    }

    /**
     * Test appendQuad with value 9999 -> "9999".
     */
    @Test
    @IR(counts = {IRNode.STORE_B, ">= 4"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testAppendQuad9999() {
        StringBuilder sb = new StringBuilder();
        DecimalDigits.appendQuad(sb, 9999);
        return sb.toString();
    }

    // ========================================
    // Combined Tests (appendQuad + appendPair)
    // ========================================

    /**
     * Test appendQuad followed by appendPair (common date/time pattern).
     * Should generate 6 StoreB operations.
     */
    @Test
    @IR(counts = {IRNode.STORE_B, ">= 6"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testQuadThenPair() {
        StringBuilder sb = new StringBuilder();
        DecimalDigits.appendQuad(sb, 2026);  // Year
        DecimalDigits.appendPair(sb, 2);     // Month
        return sb.toString();
    }

    /**
     * Test full date format: YYYY-MM-DD.
     * This is the common pattern in java.time classes.
     */
    @Test
    @IR(counts = {IRNode.STORE_B, ">= 8"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testFullDateFormat() {
        StringBuilder sb = new StringBuilder();
        DecimalDigits.appendQuad(sb, 2026);  // Year
        sb.append('-');
        DecimalDigits.appendPair(sb, 2);     // Month
        sb.append('-');
        DecimalDigits.appendPair(sb, 12);    // Day
        return sb.toString();
    }

    // ========================================
    // MergeStores Verification
    // ========================================

    /**
     * After MergeStores, two StoreB should become one StoreC.
     */
    @Test
    @IR(counts = {IRNode.STORE_C, ">= 1"},
        applyIfAnd = {"OptimizeStringConcat", "true", "MergeStores", "true"},
        phase = CompilePhase.AFTER_MERGE_STORES)
    public static String testMergeStoresPair() {
        StringBuilder sb = new StringBuilder();
        DecimalDigits.appendPair(sb, 42);
        return sb.toString();
    }

    /**
     * After MergeStores, four StoreB should become one StoreI.
     */
    @Test
    @IR(counts = {IRNode.STORE_I, ">= 1"},
        applyIfAnd = {"OptimizeStringConcat", "true", "MergeStores", "true"},
        phase = CompilePhase.AFTER_MERGE_STORES)
    public static String testMergeStoresQuad() {
        StringBuilder sb = new StringBuilder();
        DecimalDigits.appendQuad(sb, 1234);
        return sb.toString();
    }

    // ========================================
    // Correctness Verification
    // ========================================

    @Test
    public static void verifyAppendPairZero() {
        Asserts.assertEquals("00", testAppendPairZero(), "appendPair(0) should produce '00'");
    }

    @Test
    public static void verifyAppendPair42() {
        Asserts.assertEquals("42", testAppendPair42(), "appendPair(42) should produce '42'");
    }

    @Test
    public static void verifyAppendPair99() {
        Asserts.assertEquals("99", testAppendPair99(), "appendPair(99) should produce '99'");
    }

    @Test
    public static void verifyTwoAppendPair() {
        Asserts.assertEquals("1234", testTwoAppendPair(), "Two appendPair should produce '1234'");
    }

    @Test
    public static void verifyAppendQuadZero() {
        Asserts.assertEquals("0000", testAppendQuadZero(), "appendQuad(0) should produce '0000'");
    }

    @Test
    public static void verifyAppendQuad1234() {
        Asserts.assertEquals("1234", testAppendQuad1234(), "appendQuad(1234) should produce '1234'");
    }

    @Test
    public static void verifyAppendQuad9999() {
        Asserts.assertEquals("9999", testAppendQuad9999(), "appendQuad(9999) should produce '9999'");
    }

    @Test
    public static void verifyQuadThenPair() {
        Asserts.assertEquals("202602", testQuadThenPair(), "appendQuad+appendPair should produce '202602'");
    }

    @Test
    public static void verifyFullDateFormat() {
        Asserts.assertEquals("2026-02-12", testFullDateFormat(), "Full date should be '2026-02-12'");
    }

    // ========================================
    // No Call Verification
    // ========================================

    /**
     * Verify no call to StringBuilder.append(String) after optimization.
     * The old implementation used uncheckedNewStringWithLatin1Bytes which
     * created a String to append. The new implementation uses append(char)
     * which should be fully inlined.
     */
    @Test
    @IR(failOn = IRNode.CALL_OF_METHOD + "java/lang/StringBuilder.append:(Ljava/lang/String;)Ljava/lang/StringBuilder;",
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String verifyNoCallToAppendString() {
        StringBuilder sb = new StringBuilder();
        DecimalDigits.appendPair(sb, 42);
        DecimalDigits.appendQuad(sb, 1234);
        return sb.toString();
    }
}
