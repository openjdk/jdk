/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * 2 along with this work; if not write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have
 * any questions.
 */

/**
 * @test
 * @summary Test StringBuilder char coalescing optimization
 * @library /test/lib /
 * @run main/othervm -XX:-TieredCompilation
 *                   compiler.stringopts.TestCharCoalescing
 */

package compiler.stringopts;

public class TestCharCoalescing {

    // Test case: two consecutive char appends
    public static String testPair(char c1, char c2) {
        return new StringBuilder().append(c1).append(c2).toString();
    }

    // Test case: four consecutive char appends
    public static String testQuad(char c1, char c2, char c3, char c4) {
        return new StringBuilder().append(c1).append(c2).append(c3).append(c4).toString();
    }

    // Test case: mixed - string then two chars
    public static String testMixedPair(String s, char c1, char c2) {
        return new StringBuilder().append(s).append(c1).append(c2).toString();
    }

    // Test case: mixed - two chars then string
    public static String testMixedPair2(char c1, char c2, String s) {
        return new StringBuilder().append(c1).append(c2).append(s).toString();
    }

    // Test case: eight consecutive char appends (should become two quads)
    public static String testEightChars(char c1, char c2, char c3, char c4,
                                         char c5, char c6, char c7, char c8) {
        return new StringBuilder()
            .append(c1).append(c2).append(c3).append(c4)
            .append(c5).append(c6).append(c7).append(c8)
            .toString();
    }

    // Test case: three chars (should become one pair + one single)
    public static String testThreeChars(char c1, char c2, char c3) {
        return new StringBuilder().append(c1).append(c2).append(c3).toString();
    }

    // Test case: six chars (should become one quad + one pair)
    public static String testSixChars(char c1, char c2, char c3, char c4,
                                       char c5, char c6) {
        return new StringBuilder()
            .append(c1).append(c2).append(c3).append(c4)
            .append(c5).append(c6)
            .toString();
    }

    public static void main(String[] args) {
        // Warmup
        for (int i = 0; i < 20000; i++) {
            testPair('a', 'b');
            testQuad('a', 'b', 'c', 'd');
            testMixedPair("test", 'e', 'f');
            testMixedPair2('g', 'h', "test");
            testEightChars('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h');
            testThreeChars('a', 'b', 'c');
            testSixChars('a', 'b', 'c', 'd', 'e', 'f');
        }

        // Verification
        String result;

        result = testPair('H', 'i');
        if (!result.equals("Hi")) {
            throw new AssertionError("Expected 'Hi', got '" + result + "'");
        }

        result = testQuad('T', 'e', 's', 't');
        if (!result.equals("Test")) {
            throw new AssertionError("Expected 'Test', got '" + result + "'");
        }

        result = testMixedPair("He", 'l', 'l');
        if (!result.equals("Hell")) {
            throw new AssertionError("Expected 'Hell', got '" + result + "'");
        }

        result = testMixedPair2('H', 'i', "!");
        if (!result.equals("Hi!")) {
            throw new AssertionError("Expected 'Hi!', got '" + result + "'");
        }

        result = testEightChars('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H');
        if (!result.equals("ABCDEFGH")) {
            throw new AssertionError("Expected 'ABCDEFGH', got '" + result + "'");
        }

        result = testThreeChars('X', 'Y', 'Z');
        if (!result.equals("XYZ")) {
            throw new AssertionError("Expected 'XYZ', got '" + result + "'");
        }

        result = testSixChars('1', '2', '3', '4', '5', '6');
        if (!result.equals("123456")) {
            throw new AssertionError("Expected '123456', got '" + result + "'");
        }

        // Test with Latin1 boundary chars (0-255)
        result = testQuad((char)0, (char)1, (char)127, (char)255);
        String expected = new String(new char[]{0, 1, 127, 255});
        if (!result.equals(expected)) {
            throw new AssertionError("Expected '" + expected + "', got '" + result + "'");
        }

        System.out.println("All tests passed!");
    }
}
