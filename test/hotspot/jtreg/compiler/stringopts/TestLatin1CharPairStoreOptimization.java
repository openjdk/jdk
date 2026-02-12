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
 * @summary IR test to verify consecutive Latin1 char optimization at the IR level.
 *          This optimization works in conjunction with MergeStores to combine
 *          multiple StoreB operations into larger stores (StoreC/StoreI/StoreL).
 * @requires vm.compiler2.enabled
 *
 * @library /test/lib /
 * @run driver compiler.stringopts.TestLatin1CharPairStoreOptimization
 */

package compiler.stringopts;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/**
 * This IR test verifies that the StringOpts optimization correctly transforms
 * consecutive Latin1 char appends into optimized byte stores.
 *
 * The optimization works in two phases:
 * 1. StringOpts: Consecutive append(char) with Latin1 chars (value <= 0xFF)
 *    generate adjacent StoreB operations:
 *    - 4 consecutive Latin1 chars -> 4 StoreB (grouped together)
 *    - 2 consecutive Latin1 chars -> 2 StoreB (fallback when < 4)
 * 2. MergeStores: The StoreB operations are merged into larger stores:
 *    - 2 StoreB -> StoreC (16-bit)
 *    - 4 StoreB -> StoreI (32-bit)
 *    - 8 StoreB -> StoreL (64-bit)
 *
 * The key optimization happens in PhaseStringOpts::replace_string_concat() in
 * stringopts.cpp, where:
 * 1. First, check for 4 consecutive CharMode arguments with type range <= 0xFF
 * 2. Then, check for 2 consecutive CharMode arguments with type range <= 0xFF
 * 3. Generate adjacent StoreB operations for each group
 * 4. MergeStores combines adjacent StoreB into larger stores
 *
 * The optimization triggers when the char type's upper bound (_hi) is <= 0xFF,
 * which covers both constant Latin1 chars and variables with known Latin1 range
 * (e.g., digits '0'-'9' from DecimalDigits.DIGITS array).
 */
public class TestLatin1CharPairStoreOptimization {

    public static void main(String[] args) {
        TestFramework.run();
    }

    // ========================================
    // IR Verification Tests - After StringOpts
    // ========================================

    /**
     * Test with two Latin1 chars.
     * Should generate 2 StoreB operations (2-char group).
     */
    @Test
    @IR(counts = {IRNode.STORE_B, "2"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testTwoLatin1Chars() {
        return new StringBuilder().append('a').append('b').toString();
    }

    /**
     * Test with four Latin1 chars.
     * Should generate 4 StoreB operations (one 4-char group).
     */
    @Test
    @IR(counts = {IRNode.STORE_B, "4"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testFourLatin1Chars() {
        return new StringBuilder()
            .append('a').append('b')
            .append('c').append('d')
            .toString();
    }

    /**
     * Test with six Latin1 chars.
     * Should generate 6 StoreB operations (one 4-char group + one 2-char group).
     */
    @Test
    @IR(counts = {IRNode.STORE_B, "6"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testSixLatin1Chars() {
        return new StringBuilder()
            .append('a').append('b')
            .append('c').append('d')
            .append('e').append('f')
            .toString();
    }

    /**
     * Test with eight Latin1 chars.
     * Should generate 8 StoreB operations (two 4-char groups).
     */
    @Test
    @IR(counts = {IRNode.STORE_B, "8"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testEightLatin1Chars() {
        return new StringBuilder()
            .append('a').append('b')
            .append('c').append('d')
            .append('e').append('f')
            .append('g').append('h')
            .toString();
    }

    // ========================================
    // IR Verification Tests - After MergeStores
    // ========================================

    /**
     * After MergeStores: 2 StoreB -> 1 StoreC (16-bit).
     */
    @Test
    @IR(counts = {IRNode.STORE_C, ">= 1"},
        applyIfAnd = {"OptimizeStringConcat", "true", "MergeStores", "true"},
        phase = CompilePhase.AFTER_MERGE_STORES)
    public static String testMergeStoresTwoChars() {
        return new StringBuilder().append('a').append('b').toString();
    }

    /**
     * After MergeStores: 4 StoreB -> 1 StoreI (32-bit).
     */
    @Test
    @IR(counts = {IRNode.STORE_I, ">= 1"},
        applyIfAnd = {"OptimizeStringConcat", "true", "MergeStores", "true"},
        phase = CompilePhase.AFTER_MERGE_STORES)
    public static String testMergeStoresFourChars() {
        return new StringBuilder()
            .append('a').append('b')
            .append('c').append('d')
            .toString();
    }

    /**
     * After MergeStores: 8 StoreB -> 1 StoreL (64-bit) or 2 StoreI.
     */
    @Test
    @IR(counts = {IRNode.STORE_I, ">= 1"},
        applyIfAnd = {"OptimizeStringConcat", "true", "MergeStores", "true"},
        phase = CompilePhase.AFTER_MERGE_STORES)
    public static String testMergeStoresEightChars() {
        return new StringBuilder()
            .append('a').append('b')
            .append('c').append('d')
            .append('e').append('f')
            .append('g').append('h')
            .toString();
    }

    // ========================================
    // Boundary Tests
    // ========================================

    /**
     * Test with Latin1 chars at the minimum boundary (0x00, 0x01).
     */
    @Test
    @IR(counts = {IRNode.STORE_B, "2"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testMinLatin1Boundary() {
        return new StringBuilder().append('\u0000').append('\u0001').toString();
    }

    /**
     * Test with Latin1 chars at the maximum boundary (0xFE, 0xFF).
     */
    @Test
    @IR(counts = {IRNode.STORE_B, "2"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testMaxLatin1Boundary() {
        return new StringBuilder().append('\u00FE').append('\u00FF').toString();
    }

    /**
     * Test with four Latin1 chars at boundaries.
     */
    @Test
    @IR(counts = {IRNode.STORE_B, "4"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testFourCharsBoundary() {
        return new StringBuilder()
            .append('\u0000').append('\u007F')
            .append('\u0080').append('\u00FF')
            .toString();
    }

    /**
     * Test with mixed content: string + Latin1 char pair.
     */
    @Test
    @IR(counts = {IRNode.STORE_B, "2"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testStringPlusLatin1Pair() {
        return new StringBuilder("XY").append('a').append('b').toString();
    }

    /**
     * Test with string + four Latin1 chars.
     */
    @Test
    @IR(counts = {IRNode.STORE_B, "4"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testStringPlusFourLatin1() {
        return new StringBuilder("XY")
            .append('a').append('b')
            .append('c').append('d')
            .toString();
    }

    // ========================================
    // No Call Verification
    // ========================================

    /**
     * Verify that there are no CALL nodes for StringBuilder.append after optimization.
     */
    @Test
    @IR(failOn = IRNode.CALL_OF_METHOD + "java/lang/StringBuilder.append:(C)Ljava/lang/StringBuilder;",
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String verifyNoCallToAppendChar() {
        return new StringBuilder()
            .append('a').append('b')
            .append('c').append('d')
            .append('e').append('f')
            .append('g').append('h')
            .toString();
    }

    /**
     * Verify that there are no CALL nodes for the StringBuilder constructor.
     */
    @Test
    @IR(failOn = IRNode.CALL_OF_METHOD + "java/lang/StringBuilder.<init>",
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String verifyNoCallToConstructor() {
        return new StringBuilder()
            .append('a').append('b')
            .append('c').append('d')
            .toString();
    }

    // ========================================
    // Variable Latin1 Char Tests
    // ========================================

    /**
     * Test with variable chars that are known to be Latin1 (digits '0'-'9').
     * This verifies the optimization works for non-constant chars when
     * the type range is known to be <= 0xFF.
     */
    @Test
    @IR(counts = {IRNode.STORE_B, ">= 2"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testVariableLatin1Pair(int digit) {
        // digit is clamped to 0-9, so the chars are always Latin1
        int d = Math.max(0, Math.min(9, digit));
        char c1 = (char)('0' + d);
        char c2 = (char)('0' + (9 - d));
        return new StringBuilder().append(c1).append(c2).toString();
    }

    /**
     * Test with four variable Latin1 chars.
     */
    @Test
    @IR(counts = {IRNode.STORE_B, ">= 4"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testVariableLatin1Quad(int digit) {
        int d = Math.max(0, Math.min(9, digit));
        char c1 = (char)('0' + d);
        char c2 = (char)('0' + ((d + 1) % 10));
        char c3 = (char)('0' + ((d + 2) % 10));
        char c4 = (char)('0' + ((d + 3) % 10));
        return new StringBuilder()
            .append(c1).append(c2)
            .append(c3).append(c4)
            .toString();
    }

    // ========================================
    // Correctness Verification Tests
    // ========================================

    @Test
    public static void verifyTwoCharsCorrectness() {
        String result = testTwoLatin1Chars();
        Asserts.assertEquals("ab", result, "Two Latin1 chars should produce 'ab'");
    }

    @Test
    public static void verifyFourCharsCorrectness() {
        String result = testFourLatin1Chars();
        Asserts.assertEquals("abcd", result, "Four Latin1 chars should produce 'abcd'");
    }

    @Test
    public static void verifySixCharsCorrectness() {
        String result = testSixLatin1Chars();
        Asserts.assertEquals("abcdef", result, "Six Latin1 chars should produce 'abcdef'");
    }

    @Test
    public static void verifyEightCharsCorrectness() {
        String result = testEightLatin1Chars();
        Asserts.assertEquals("abcdefgh", result, "Eight Latin1 chars should produce 'abcdefgh'");
    }

    @Test
    public static void verifyMinBoundaryCorrectness() {
        String result = testMinLatin1Boundary();
        Asserts.assertEquals(2, result.length(), "Should have 2 chars");
        Asserts.assertEquals('\u0000', result.charAt(0), "First char should be \\u0000");
        Asserts.assertEquals('\u0001', result.charAt(1), "Second char should be \\u0001");
    }

    @Test
    public static void verifyMaxBoundaryCorrectness() {
        String result = testMaxLatin1Boundary();
        Asserts.assertEquals(2, result.length(), "Should have 2 chars");
        Asserts.assertEquals('\u00FE', result.charAt(0), "First char should be \\u00FE");
        Asserts.assertEquals('\u00FF', result.charAt(1), "Second char should be \\u00FF");
    }

    @Test
    public static void verifyFourCharsBoundaryCorrectness() {
        String result = testFourCharsBoundary();
        Asserts.assertEquals("\u0000\u007F\u0080\u00FF", result,
            "Four boundary chars should be preserved");
    }

    @Test
    public static void verifyStringPlusPairCorrectness() {
        String result = testStringPlusLatin1Pair();
        Asserts.assertEquals("XYab", result, "Should produce 'XYab'");
    }

    @Test
    public static void verifyStringPlusFourCorrectness() {
        String result = testStringPlusFourLatin1();
        Asserts.assertEquals("XYabcd", result, "Should produce 'XYabcd'");
    }

    // ========================================
    // Negative Tests - Non-Latin1 chars
    // ========================================

    /**
     * Test with non-Latin1 chars (> 0xFF).
     * These should use UTF16 encoding, not Latin1 StoreB.
     */
    @Test
    @IR(counts = {IRNode.STORE_C, ">= 2"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testNonLatin1Chars() {
        return new StringBuilder().append('\u0100').append('\u0200').toString();
    }

    @Test
    public static void verifyNonLatin1Correctness() {
        String result = testNonLatin1Chars();
        Asserts.assertEquals("\u0100\u0200", result, "Non-Latin1 chars should work correctly");
    }

    /**
     * Test with mixed Latin1 and non-Latin1 chars.
     * The non-Latin1 char forces UTF16 encoding.
     */
    @Test
    public static String testMixedLatin1AndNonLatin1() {
        return new StringBuilder()
            .append('a')      // Latin1
            .append('\u0100') // Non-Latin1
            .append('b')      // Latin1
            .toString();
    }

    @Test
    public static void verifyMixedCorrectness() {
        String result = testMixedLatin1AndNonLatin1();
        Asserts.assertEquals("a\u0100b", result, "Mixed chars should work correctly");
    }

    /**
     * Test with odd number of Latin1 chars (3 chars).
     * Should use 2-char group + 1 single char.
     */
    @Test
    @IR(counts = {IRNode.STORE_B, "3"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testThreeLatin1Chars() {
        return new StringBuilder()
            .append('a').append('b').append('c')
            .toString();
    }

    @Test
    public static void verifyThreeCharsCorrectness() {
        String result = testThreeLatin1Chars();
        Asserts.assertEquals("abc", result, "Three Latin1 chars should produce 'abc'");
    }

    /**
     * Test with five Latin1 chars.
     * Should use 4-char group + 2-char group (wait, only 1 left -> single).
     * Actually: 4-char group + 1 single.
     */
    @Test
    @IR(counts = {IRNode.STORE_B, "5"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testFiveLatin1Chars() {
        return new StringBuilder()
            .append('a').append('b')
            .append('c').append('d')
            .append('e')
            .toString();
    }

    @Test
    public static void verifyFiveCharsCorrectness() {
        String result = testFiveLatin1Chars();
        Asserts.assertEquals("abcde", result, "Five Latin1 chars should produce 'abcde'");
    }

    /**
     * Test with seven Latin1 chars.
     * Should use 4-char group + 2-char group + 1 single.
     */
    @Test
    @IR(counts = {IRNode.STORE_B, "7"},
        applyIf = {"OptimizeStringConcat", "true"},
        phase = CompilePhase.AFTER_STRINGOPTS)
    public static String testSevenLatin1Chars() {
        return new StringBuilder()
            .append('a').append('b')
            .append('c').append('d')
            .append('e').append('f')
            .append('g')
            .toString();
    }

    @Test
    public static void verifySevenCharsCorrectness() {
        String result = testSevenLatin1Chars();
        Asserts.assertEquals("abcdefg", result, "Seven Latin1 chars should produce 'abcdefg'");
    }
}
