/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8395868
 * @summary Test optimizations for constant string comparisons
 * @key randomness
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.stringopts;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;
import java.util.function.Function;

public class TestStringConstantEquals {
    private static final String STRING_0 = generateString(0);
    private static final String STRING_1 = generateString(1);
    private static final String STRING_2 = generateString(2);
    private static final String STRING_3 = generateString(3);
    private static final String STRING_4 = generateString(4);
    private static final String STRING_5 = generateString(5);
    private static final String STRING_6 = generateString(6);
    private static final String STRING_7 = generateString(7);
    private static final String STRING_8 = generateString(8);
    private static final String STRING_9 = generateString(9);
    private static final String STRING_10 = generateString(10);
    private static final String STRING_11 = generateString(11);
    private static final String STRING_12 = generateString(12);
    private static final String STRING_13 = generateString(13);
    private static final String STRING_14 = generateString(14);
    private static final String STRING_15 = generateString(15);
    private static final String STRING_16 = generateString(16);
    private static final String STRING_17 = generateString(17);

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+UseCompactObjectHeaders");
        TestFramework.runWithFlags("-XX:-UseCompactObjectHeaders");
    }

    private static String generateString(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            char c = (char) ('a' + (RunInfo.getRandom().nextInt(2)));
            sb.append(c);
        }

        return sb.toString();
    }

    @DontCompile
    private static void runAndTest(String constString, Function<String, Boolean> method) {
        runAndTest(constString, 0, method);
    }

    @DontCompile
    private static void runAndTestSwap(String constString, Function<String, Boolean> method) {
        // Don't generate nulls for swapped comparisons to prevent NPE
        runAndTest(constString, 1, method);
    }

    @DontCompile
    private static void runAndTest(String constString, int choiceOffset, Function<String, Boolean> method) {
        for (int i = 0; i < 1024; i++) {
            int choice = RunInfo.getRandom().nextInt(10);
            String str = switch (choice + choiceOffset) {
                case 0  -> null;
                case 1  -> generateString(Math.max(0, constString.length() + RunInfo.getRandom().nextInt(10) - 5));
                default -> generateString(constString.length());
            };

            boolean result = method.apply(str);

            Asserts.assertEquals(constString.equals(str), result);
        }
    }

    @Run(test = {"testString0", "testString1", "testString2", "testString3",
                 "testString4", "testString5", "testString6", "testString7",
                 "testString8", "testString9", "testString10", "testString11",
                 "testString12", "testString13", "testString14", "testString15",
                 "testString16", "testString17"})
    public void testStrings() {
        runAndTest(STRING_0, this::testString0);
        runAndTest(STRING_1, this::testString1);
        runAndTest(STRING_2, this::testString2);
        runAndTest(STRING_3, this::testString3);
        runAndTest(STRING_4, this::testString4);
        runAndTest(STRING_5, this::testString5);
        runAndTest(STRING_6, this::testString6);
        runAndTest(STRING_7, this::testString7);
        runAndTest(STRING_8, this::testString8);
        runAndTest(STRING_9, this::testString9);
        runAndTest(STRING_10, this::testString10);
        runAndTest(STRING_11, this::testString11);
        runAndTest(STRING_12, this::testString12);
        runAndTest(STRING_13, this::testString13);
        runAndTest(STRING_14, this::testString14);
        runAndTest(STRING_15, this::testString15);
        runAndTest(STRING_16, this::testString16);
        runAndTest(STRING_17, this::testString17);
    }

    @Run(test = {"testString0Swap", "testString8Swap", "testString16Swap", "testString17Swap"})
    public void testStringsSwap() {
        runAndTestSwap(STRING_0, this::testString0Swap);
        runAndTestSwap(STRING_8, this::testString8Swap);
        runAndTestSwap(STRING_16, this::testString16Swap);
        runAndTestSwap(STRING_17, this::testString17Swap);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "1"})
    @IR(counts = {IRNode.CMP_U, "1"})
    public boolean testString0(String s) {
        return STRING_0.equals(s);
    }

    // Before any constant strings are expanded, two CmpI nodes are created for the coder check and the length check.
    private static final int BASE_CMP_I_COUNT = 2;

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 1)})
    public boolean testString1(String s) {
        return STRING_1.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 1)})
    public boolean testString2(String s) {
        return STRING_2.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 2)})
    public boolean testString3(String s) {
        return STRING_3.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 1)})
    public boolean testString4(String s) {
        return STRING_4.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 2)})
    public boolean testString5(String s) {
        return STRING_5.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 2)})
    public boolean testString6(String s) {
        return STRING_6.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 3)})
    public boolean testString7(String s) {
        return STRING_7.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 2)}, applyIf = {"UseCompactObjectHeaders", "true"})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT)}, applyIf = {"UseCompactObjectHeaders", "false"})
    @IR(counts = {IRNode.CMP_L, "" + 1}, applyIf = {"UseCompactObjectHeaders", "false"})
    public boolean testString8(String s) {
        return STRING_8.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 3)}, applyIf = {"UseCompactObjectHeaders", "true"})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 1)}, applyIf = {"UseCompactObjectHeaders", "false"})
    @IR(counts = {IRNode.CMP_L, "" + 1}, applyIf = {"UseCompactObjectHeaders", "false"})
    public boolean testString9(String s) {
        return STRING_9.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 3)}, applyIf = {"UseCompactObjectHeaders", "true"})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 1)}, applyIf = {"UseCompactObjectHeaders", "false"})
    @IR(counts = {IRNode.CMP_L, "" + 1}, applyIf = {"UseCompactObjectHeaders", "false"})
    public boolean testString10(String s) {
        return STRING_10.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 4)}, applyIf = {"UseCompactObjectHeaders", "true"})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 2)}, applyIf = {"UseCompactObjectHeaders", "false"})
    @IR(counts = {IRNode.CMP_L, "" + 1}, applyIf = {"UseCompactObjectHeaders", "false"})
    public boolean testString11(String s) {
        return STRING_11.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 1)})
    @IR(counts = {IRNode.CMP_L, "" + 1})
    public boolean testString12(String s) {
        return STRING_12.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 2)})
    @IR(counts = {IRNode.CMP_L, "" + 1})
    public boolean testString13(String s) {
        return STRING_13.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 2)})
    @IR(counts = {IRNode.CMP_L, "" + 1})
    public boolean testString14(String s) {
        return STRING_14.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 3)})
    @IR(counts = {IRNode.CMP_L, "" + 1})
    public boolean testString15(String s) {
        return STRING_15.equals(s);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 2)}, applyIf = {"UseCompactObjectHeaders", "true"})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT)}, applyIf = {"UseCompactObjectHeaders", "false"})
    @IR(counts = {IRNode.CMP_L, "" + 2}, applyIf = {"UseCompactObjectHeaders", "false"})
    @IR(counts = {IRNode.CMP_L, "" + 1}, applyIf = {"UseCompactObjectHeaders", "true"})
    public boolean testString16(String s) {
        return STRING_16.equals(s);
    }

    // No constant folding expected here

    @Test
    @IR(counts = {IRNode.STR_EQUALS, "1"})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 1)})
    public boolean testString17(String s) {
        return STRING_17.equals(s);
    }

    // Test some swapped versions

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "1"})
    @IR(counts = {IRNode.CMP_U, "1"})
    public boolean testString0Swap(String s) {
        return s.equals(STRING_0);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 2)}, applyIf = {"UseCompactObjectHeaders", "true"})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT)}, applyIf = {"UseCompactObjectHeaders", "false"})
    @IR(counts = {IRNode.CMP_L, "" + 1}, applyIf = {"UseCompactObjectHeaders", "false"})
    public boolean testString8Swap(String s) {
        return s.equals(STRING_8);
    }

    @Test
    @IR(failOn = {IRNode.STR_EQUALS})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT + 2)}, applyIf = {"UseCompactObjectHeaders", "true"})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT)}, applyIf = {"UseCompactObjectHeaders", "false"})
    @IR(counts = {IRNode.CMP_L, "" + 2}, applyIf = {"UseCompactObjectHeaders", "false"})
    @IR(counts = {IRNode.CMP_L, "" + 1}, applyIf = {"UseCompactObjectHeaders", "true"})
    public boolean testString16Swap(String s) {
        return s.equals(STRING_16);
    }

    @Test
    @IR(counts = {IRNode.STR_EQUALS, "1"})
    @IR(counts = {IRNode.CMP_I, "" + (BASE_CMP_I_COUNT)})
    public boolean testString17Swap(String s) {
        return s.equals(STRING_17);
    }
}
