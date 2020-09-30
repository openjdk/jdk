/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.format;

import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertEquals;

/*
 * @test
 * @summary Check ArrayDiff formatting
 * @library /test/lib
 * @run testng jdk.test.lib.format.ArrayDiffTest
 */
public class ArrayDiffTest {

    @Test
    public void testEqualArrays() {
        char[] first = new char[]  {'a', 'b', 'c', 'd', 'e', 'f', 'g'};
        char[] second = new char[] {'a', 'b', 'c', 'd', 'e', 'f', 'g'};

        assertTrue(ArrayDiff.of(first, second).areEqual());
    }

    @Test
    public void testOutputFitsWidth() {
        byte[] first = new byte[]  {7, 8, 9, 10,  11, 12, 13};
        byte[] second = new byte[] {7, 8, 9, 10, 125, 12, 13};

        ArrayDiff diff = ArrayDiff.of(first, second);
        String expected = String.format(
                "Arrays differ starting from [index: 4]:%n" +
                "[7, 8, 9, 10,  11, 12, 13]%n" +
                "[7, 8, 9, 10, 125, 12, 13]%n" +
                "             ^^^^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testIntegers() {
        int[] first = new int[]  {7, 8, 10, 11, 12};
        int[] second = new int[] {7, 8, 9, 10, 11, 12, 13};

        ArrayDiff diff = ArrayDiff.of(first, second);
        String expected = String.format(
                "Arrays differ starting from [index: 2]:%n" +
                "[7, 8, 10, 11, 12]%n" +
                "[7, 8,  9, 10, 11, 12, 13]%n" +
                "      ^^^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testLongs() {
        long[] first = new long[]  {1, 2, 3, 4};
        long[] second = new long[] {1, 2, 3, 10};

        ArrayDiff diff = ArrayDiff.of(first, second);
        String expected = String.format(
                "Arrays differ starting from [index: 3]:%n" +
                "[1, 2, 3,  4]%n" +
                "[1, 2, 3, 10]%n" +
                "         ^^^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testFirstElementIsWrong() {
        byte[] first = new byte[]  {122};
        byte[] second = new byte[] {7, 8, 9, 10, 125, 12, 13};

        ArrayDiff diff = ArrayDiff.of(first, second);
        String expected = String.format(
                "Arrays differ starting from [index: 0]:%n" +
                "[122]%n" +
                "[  7, 8, 9, 10, 125, 12, 13]%n" +
                " ^^^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testOneElementIsEmpty() {
        byte[] first = new byte[]  {7, 8, 9, 10, 125, 12, 13};
        byte[] second = new byte[] {};

        ArrayDiff diff = ArrayDiff.of(first, second);
        String expected = String.format(
                "Arrays differ starting from [index: 0]:%n" +
                "[7, 8, 9, 10, 125, 12, 13]%n" +
                "[]%n" +
                " ^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testOutputDoesntFitWidth() {
        char[] first = new char[]  {'1', '2', '3', '4', '5', '6', '7'};
        char[] second = new char[] {'1', 'F', '3', '4', '5', '6', '7'};

        ArrayDiff diff = ArrayDiff.of(first, second, 20, Integer.MAX_VALUE);
        String expected = String.format(
                "Arrays differ starting from [index: 1]:%n" +
                "[1, 2, 3, 4, 5, ...%n" +
                "[1, F, 3, 4, 5, ...%n" +
                "   ^^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testVariableElementWidthOutputDoesntFitWidth() {
        byte[] first = new byte[]  {1,   2, 3, 4, 5, 6, 7};
        byte[] second = new byte[] {1, 112, 3, 4, 5, 6, 7};

        ArrayDiff diff = ArrayDiff.of(first, second, 20, Integer.MAX_VALUE);
        String expected = String.format(
                "Arrays differ starting from [index: 1]:%n" +
                "[1,   2, 3, 4, 5, ...%n" +
                "[1, 112, 3, 4, 5, ...%n" +
                "   ^^^^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testContextBefore() {
        char[] first = new char[]  {'1', '2', '3', '4', '5', '6', '7'};
        char[] second = new char[] {'1', '2', '3', '4', 'F', '6', '7'};

        ArrayDiff diff = ArrayDiff.of(first, second, 20, 2);
        String expected = String.format(
                "Arrays differ starting from [index: 4]:%n" +
                "... 3, 4, 5, 6, 7]%n" +
                "... 3, 4, F, 6, 7]%n" +
                "         ^^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testBoundedBytesWithDifferentWidth() {
        byte[] first = new byte[]  {0, 1, 2, 3, 125, 5, 6, 7};
        byte[] second = new byte[] {0, 1, 2, 3,   4, 5, 6, 7};

        ArrayDiff diff = ArrayDiff.of(first, second, 24, 2);
        String expected = String.format(
                "Arrays differ starting from [index: 4]:%n" +
                "... 2, 3, 125, 5, 6, 7]%n" +
                "... 2, 3,   4, 5, 6, 7]%n" +
                "         ^^^^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testBoundedFirstElementIsWrong() {
        byte[] first = new byte[] {101, 102, 103, 104, 105, 110};
        byte[] second = new byte[] {2};

        ArrayDiff diff = ArrayDiff.of(first, second, 25, 2);
        String expected = String.format(
                "Arrays differ starting from [index: 0]:%n" +
                "[101, 102, 103, 104, ...%n" +
                "[  2]%n" +
                " ^^^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testBoundedOneArchiveIsEmpty() {
        char[] first = new char[] {'a', 'b', 'c', 'd', 'e'};
        char[] second = new char[] {};

        ArrayDiff diff = ArrayDiff.of(first, second, 10, 2);
        String expected = String.format(
                "Arrays differ starting from [index: 0]:%n" +
                "[a, b, ...%n" +
                "[]%n" +
                " ^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testUnboundedOneArchiveIsEmpty() {
        char[] first = new char[] {'a', 'b', 'c', 'd', 'e'};
        char[] second = new char[] {};

        ArrayDiff diff = ArrayDiff.of(first, second);
        String expected = String.format(
                "Arrays differ starting from [index: 0]:%n" +
                "[a, b, c, d, e]%n" +
                "[]%n" +
                " ^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testUnprintableCharFormatting() {
        char[] first = new char[]  {0, 1, 2, 3, 4, 5, 6,   7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        char[] second = new char[] {0, 1, 2, 3, 4, 5, 6, 125, 8, 9, 10, 11, 12, 13, 14, 15, 16};

        ArrayDiff diff = ArrayDiff.of(first, second);

        // Lines in the code look like aren't aligned due to slashes taking more space than spaces.
        var nl = System.lineSeparator();
        String expected = "Arrays differ starting from [index: 7]:" + nl +
                "... \\u0005, \\u0006, \\u0007, \\u0008, \\u0009, \\n, \\u000B, \\u000C, \\r, \\u000E, ..." + nl +
                "... \\u0005, \\u0006,      }, \\u0008, \\u0009, \\n, \\u000B, \\u000C, \\r, \\u000E, ..." + nl +
                "                   ^^^^^^^";
        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testStringElements() {
        String[] first = new String[]  {"first", "second", "third", "u\nprintable"};
        String[] second = new String[] {"first", "second", "incorrect", "u\nprintable"};

        ArrayDiff diff = ArrayDiff.of(first, second);
        String expected = String.format(
                "Arrays differ starting from [index: 2]:%n" +
                "[\"first\", \"second\",     \"third\", \"u\\nprintable\"]%n" +
                "[\"first\", \"second\", \"incorrect\", \"u\\nprintable\"]%n" +
                "                   ^^^^^^^^^^^^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

    @Test
    public void testToStringableObjects() {
        class StrObj {
            private final String value;
            public boolean equals(Object another) { return ((StrObj)another).value.equals(value); }
            public StrObj(String value) { this.value = value; }
            public String toString() { return value; }
        }

        StrObj[] first = new StrObj[]  {new StrObj("1"), new StrObj("Unp\rintable"), new StrObj("5")};
        StrObj[] second = new StrObj[]  {new StrObj("1"), new StrObj("2"), new StrObj("5")};

        ArrayDiff diff = ArrayDiff.of(first, second);
        String expected = String.format(
                "Arrays differ starting from [index: 1]:%n" +
                "[1, Unp\\rintable, 5]%n" +
                "[1,            2, 5]%n" +
                "   ^^^^^^^^^^^^^");

        assertFalse(diff.areEqual());
        assertEquals(diff.format(), expected);
    }

}
