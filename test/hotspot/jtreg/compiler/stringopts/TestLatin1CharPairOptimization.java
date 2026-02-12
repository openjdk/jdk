/*
 * Copyright (c) 2025 Oracle and/or its affiliates. All rights reserved.
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
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit https://www.oracle.com if you need any additional information
 * or have any questions.
 */

/*
 * @test
 * @bug 8360123
 * @summary Test that consecutive Latin1 char appends are optimized
 * @requires vm.compiler2.enabled
 *
 * @run main/othervm -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   -XX:+OptimizeStringConcat -XX:+PrintOptimizeStringConcat
 *                   compiler.stringopts.TestLatin1CharPairOptimization
 * @run main/othervm -XX:-BackgroundCompilation -XX:-UseOnStackReplacement
 *                   -XX:+OptimizeStringConcat
 *                   compiler.stringopts.TestLatin1CharPairOptimization
 */

package compiler.stringopts;

/**
 * Test that consecutive Latin1 char append operations in StringBuilder
 * are optimized correctly.
 */
public class TestLatin1CharPairOptimization {

    /**
     * Test method with consecutive Latin1 char appends.
     * The optimizer should combine pairs of Latin1 chars.
     */
    public static String testLatin1Pair() {
        // All these chars are Latin1 (<= 0xFF)
        // The optimizer should combine consecutive pairs
        StringBuilder sb = new StringBuilder();
        sb.append('a').append('b');  // Latin1 pair - should be optimized
        sb.append('c').append('d');  // Latin1 pair - should be optimized
        sb.append('e').append('f');  // Latin1 pair - should be optimized
        return sb.toString();
    }

    /**
     * Test method with mixed Latin1 and non-Latin1 chars.
     */
    public static String testMixedChars() {
        StringBuilder sb = new StringBuilder();
        sb.append('a').append('b');           // Latin1 pair
        sb.append('\u0100');                   // Non-Latin1 char
        sb.append('c').append('d');           // Latin1 pair
        return sb.toString();
    }

    /**
     * Test method with all non-Latin1 chars.
     */
    public static String testNonLatin1() {
        StringBuilder sb = new StringBuilder();
        sb.append('\u0100').append('\u0200');  // Both non-Latin1
        return sb.toString();
    }

    /**
     * Test method with constant Latin1 chars that should be inlined.
     */
    public static String testConstantLatin1() {
        // Using constant chars directly
        return new StringBuilder()
            .append('A').append('B')
            .append('C').append('D')
            .toString();
    }

    /**
     * Test with a longer sequence of Latin1 chars.
     */
    public static String testLongLatin1Sequence() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append('x').append('y');
        }
        return sb.toString();
    }

    /**
     * Test correctness with edge cases.
     */
    public static String testEdgeCases() {
        StringBuilder sb = new StringBuilder();
        // Test with chars at Latin1 boundaries
        sb.append('\u0000').append('\u0001');  // Very low Latin1
        sb.append('\u007F').append('\u0080');  // Around 127/128
        sb.append('\u00FE').append('\u00FF');  // Max Latin1
        return sb.toString();
    }

    public static void main(String[] args) {
        // Warm up to trigger JIT compilation
        for (int i = 0; i < 20000; i++) {
            testLatin1Pair();
            testMixedChars();
            testNonLatin1();
            testConstantLatin1();
            testLongLatin1Sequence();
            testEdgeCases();
        }

        // Verify correctness
        String result1 = testLatin1Pair();
        if (!result1.equals("abcdef")) {
            throw new AssertionError("Expected 'abcdef' but got '" + result1 + "'");
        }

        String result2 = testMixedChars();
        if (!result2.equals("ab\u0100cd")) {
            throw new AssertionError("Expected 'ab\u0100cd' but got '" + result2 + "'");
        }

        String result3 = testNonLatin1();
        if (!result3.equals("\u0100\u0200")) {
            throw new AssertionError("Expected '\\u0100\\u0200' but got '" + result3 + "'");
        }

        String result4 = testConstantLatin1();
        if (!result4.equals("ABCD")) {
            throw new AssertionError("Expected 'ABCD' but got '" + result4 + "'");
        }

        String result5 = testLongLatin1Sequence();
        String expected5 = "xyxyxyxyxyxyxyxyxyxy";
        if (!result5.equals(expected5)) {
            throw new AssertionError("Expected '" + expected5 + "' but got '" + result5 + "'");
        }

        String result6 = testEdgeCases();
        String expected6 = "\u0000\u0001\u007F\u0080\u00FE\u00FF";
        if (!result6.equals(expected6)) {
            throw new AssertionError("Expected edge case string but got '" + result6 + "'");
        }

        System.out.println("All tests passed!");
        System.out.println("testLatin1Pair: " + result1);
        System.out.println("testMixedChars: " + result2);
        System.out.println("testConstantLatin1: " + result4);
        System.out.println("testLongLatin1Sequence: " + result5);
        System.out.println("testEdgeCases length: " + result6.length());
    }
}
