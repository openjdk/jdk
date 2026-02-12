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
 * @summary IR test for StringBuilder.append(char) coalescing optimization
 * @library /test/lib /
 * @run driver compiler.stringopts.TestStringBuilderCharCoalescingIR
 */

package compiler.stringopts;

import compiler.lib.ir_framework.*;

public class TestStringBuilderCharCoalescingIR {

    public static void main(String[] args) {
        TestFramework.run(TestStringBuilderCharCoalescingIR.class);
    }

    // Setup methods to provide char values
    @Setup
    static Object[] setupTwoChars() {
        return new Object[] { 'H', 'i' };
    }

    @Setup
    static Object[] setupFourChars() {
        return new Object[] { 'T', 'e', 's', 't' };
    }

    @Setup
    static Object[] setupSixChars() {
        return new Object[] { '1', '2', '3', '4', '5', '6' };
    }

    @Setup
    static Object[] setupEightChars() {
        return new Object[] { 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H' };
    }

    @Setup
    static Object[] setupStringAndTwoChars() {
        return new Object[] { "He", 'l', 'l' };
    }

    @Setup
    static Object[] setupTwoCharsAndString() {
        return new Object[] { 'H', 'i', "!" };
    }

    // Test: two consecutive char appends should be coalesced into CharPairMode,
    // triggering StoreC (MergeStore converts 2 StoreB -> 1 StoreC)
    @Test
    @Arguments(setup = "setupTwoChars")
    @IR(counts = {IRNode.STORE_C_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)", ">=1"},
        applyIf = {"MergeStores", "true"},
        applyIfPlatform = {"little-endian", "true"})
    public static String testPair(char c1, char c2) {
        return new StringBuilder().append(c1).append(c2).toString();
    }

    // Test: four consecutive char appends should be coalesced into CharQuadMode,
    // triggering StoreI (MergeStore converts 4 StoreB -> 1 StoreI)
    @Test
    @Arguments(setup = "setupFourChars")
    @IR(counts = {IRNode.STORE_I_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)", ">=1"},
        applyIf = {"MergeStores", "true"},
        applyIfPlatform = {"little-endian", "true"})
    public static String testQuad(char c1, char c2, char c3, char c4) {
        return new StringBuilder().append(c1).append(c2).append(c3).append(c4).toString();
    }

    // Test: eight consecutive char appends should become two CharQuadMode,
    // resulting in two StoreI operations
    @Test
    @Arguments(setup = "setupEightChars")
    @IR(counts = {IRNode.STORE_I_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)", ">=2"},
        applyIf = {"MergeStores", "true"},
        applyIfPlatform = {"little-endian", "true"})
    public static String testEightChars(char c1, char c2, char c3, char c4,
                                        char c5, char c6, char c7, char c8) {
        return new StringBuilder()
            .append(c1).append(c2).append(c3).append(c4)
            .append(c5).append(c6).append(c7).append(c8)
            .toString();
    }

    // Test: six consecutive char appends should become one CharQuadMode + one CharPairMode,
    // resulting in one StoreI and one StoreC
    @Test
    @Arguments(setup = "setupSixChars")
    @IR(counts = {IRNode.STORE_I_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)", ">=1",
                  IRNode.STORE_C_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)", ">=1"},
        applyIf = {"MergeStores", "true"},
        applyIfPlatform = {"little-endian", "true"})
    public static String testSixChars(char c1, char c2, char c3, char c4,
                                      char c5, char c6) {
        return new StringBuilder()
            .append(c1).append(c2).append(c3).append(c4)
            .append(c5).append(c6)
            .toString();
    }

    // Test: string followed by two chars - the two chars should still be coalesced
    @Test
    @Arguments(setup = "setupStringAndTwoChars")
    @IR(counts = {IRNode.STORE_C_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)", ">=1"},
        applyIf = {"MergeStores", "true"},
        applyIfPlatform = {"little-endian", "true"})
    public static String testStringThenPair(String s, char c1, char c2) {
        return new StringBuilder().append(s).append(c1).append(c2).toString();
    }

    // Test: two chars followed by string - the two chars should be coalesced
    @Test
    @Arguments(setup = "setupTwoCharsAndString")
    @IR(counts = {IRNode.STORE_C_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)", ">=1"},
        applyIf = {"MergeStores", "true"},
        applyIfPlatform = {"little-endian", "true"})
    public static String testPairThenString(char c1, char c2, String s) {
        return new StringBuilder().append(c1).append(c2).append(s).toString();
    }

    // Test: Verify that without MergeStores, we get individual StoreB operations instead
    @Test
    @Arguments(setup = "setupTwoChars")
    @IR(failOn = {IRNode.STORE_C_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)"},
        applyIf = {"MergeStores", "false"})
    public static String testPairNoMergeStore(char c1, char c2) {
        return new StringBuilder().append(c1).append(c2).toString();
    }

    // Test: Verify that without MergeStores, we get individual StoreB operations for quad
    @Test
    @Arguments(setup = "setupFourChars")
    @IR(failOn = {IRNode.STORE_I_OF_CLASS, "byte\\[int:>=0] \\(java/lang/Cloneable,java/io/Serializable\\)"},
        applyIf = {"MergeStores", "false"})
    public static String testQuadNoMergeStore(char c1, char c2, char c3, char c4) {
        return new StringBuilder().append(c1).append(c2).append(c3).append(c4).toString();
    }
}
