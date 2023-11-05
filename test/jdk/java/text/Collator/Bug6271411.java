/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6271411
 * @summary Confirm that three JCK testcases for CollationElementIterator pass.
 * @run junit Bug6271411
 */

import java.text.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/*
 * Based on JCK-runtime-15/tests/api/java_text/CollationElementIterator/ColltnElmtIterTests.java.
 */
public class Bug6271411 {

    /*
     * Rule for RuleBasedCollator
     */
    static final String rule = "< c, C < d; D";

    /*
     * Textdata
     */
    static final String[] values = {
        "", "c", "cH522Yd", "Hi, high school", "abcchCHidD"
    };


    /*
     * Confirm that setOffset() throws IllegalArgumentException
     * (not IndexOutOfBoundsException) if the given offset is invalid.
     * Use CollationElementIterator.setText(String).
     */
    @Test
    public void Test_CollationElementIterator0007() throws Exception {
        int[] offsets = {
            Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -10000, -2, -1,
            100, 101, // These two are customized for every test data later.
            12345, Integer.MAX_VALUE - 1, Integer.MAX_VALUE
        };
        boolean err = false;

        RuleBasedCollator rbc = new RuleBasedCollator(rule);
        CollationElementIterator iterator = rbc.getCollationElementIterator("");

        for (int i = 0; i < values.length; i++) {
            String source = values[i];
            iterator.setText(source);

            int len = source.length();
            offsets[5] = len + 1;
            offsets[6] = len + 2;

            for (int j = 0; j < offsets.length; j++) {
                try {
                    iterator.setOffset(offsets[j]);
                    System.out.println("IllegalArgumentException should be thrown for setOffset(" +
                                       offsets[j] + ") for <" + source + ">.");
                    err = true;
                }
                catch (IllegalArgumentException e) {
                }
            }
        }

        if (err) {
            fail("CollationElementIterator.setOffset() didn't throw an expected IllegalArguemntException.");
        }
    }

    /*
     * Confirm that setText() doesn't throw an exception and setOffset() throws
     * IllegalArgumentException if the given offset is invalid.
     * Use CollationElementIterator.setText(CharacterIterator).
     */
    @Test
    public void Test_CollationElementIterator0010() throws Exception {
        String prefix = "xyz abc";
        String suffix = "1234567890";
        int begin = prefix.length();
        int[] offsets = {
            Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -10000,
            -2, -1, 0, 1, begin - 2, begin - 1, 9, 10, 11, 12, 13, 14,
            15, 12345, Integer.MAX_VALUE - 1, Integer.MAX_VALUE
        };
        boolean err = false;

        RuleBasedCollator rbc = new RuleBasedCollator(rule);
        CollationElementIterator iterator = rbc.getCollationElementIterator("");

        for (int i = 0; i < values.length; i++) {
            String str = prefix + values[i] + suffix;
            int len = str.length();
            int end = len - suffix.length();

            CharacterIterator source =
                new StringCharacterIterator(str, begin, end, begin);
            iterator.setText(source);

            offsets[9] = end + 1;
            offsets[10] = end + 2;
            offsets[11] = (end + len) / 2;
            offsets[12] = len - 1;
            offsets[13] = len;
            offsets[14] = len + 1;
            offsets[15] = len + 2;

            for (int j = 0; j < offsets.length; j++) {
                try {
                    iterator.setOffset(offsets[j]);

                    System.out.println("IllegalArgumentException should be thrown for setOffset(" +
                                       offsets[j] + ") for <" + str + ">.");
                    err = true;
                }
                catch (IllegalArgumentException e) {
                }
            }
        }

        if (err) {
            fail("CollationElementIterator.setOffset() didn't throw an expected IllegalArguemntException.");
        }
    }

    /*
     * Confirm that setText() doesn't throw an exception and setOffset() sets
     * an offset as expected.
     * Use CollationElementIterator.setText(CharacterIterator).
     */
    @Test
    public void Test_CollationElementIterator0011() throws Exception {
        String prefix = "xyz abc";
        String suffix = "1234567890";
        int begin = prefix.length();
        int[] offsets = { begin, begin + 1, 2, 3, 4 };

        RuleBasedCollator rbc = new RuleBasedCollator(rule);
        CollationElementIterator iterator = rbc.getCollationElementIterator("");

        for (int i = 0; i < values.length; i++) {
            String str = prefix + values[i] + suffix;
            int len = str.length();
            int end = len - suffix.length();
            CharacterIterator source =
                new StringCharacterIterator(str, begin, end, begin);
            iterator.setText(source);

            offsets[2] = (end + len) / 2;
            offsets[3] = len - 1;
            offsets[4] = len;

            for (int j = 0; j < offsets.length; j++) {
                int offset = offsets[j];

                if (offset < begin || offset > end) {
                    break;
                }

                iterator.setOffset(offset);
                int newOffset = iterator.getOffset();

                if (newOffset != offset) {
                    throw new RuntimeException("setOffset() didn't set a correct offset. Got: " +
                    newOffset + " Expected: " + offset + " for <" + str + ">.");
                }
            }
        }
    }
}
