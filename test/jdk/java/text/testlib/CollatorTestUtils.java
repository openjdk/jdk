/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.text.CollationElementIterator;
import java.text.CollationKey;
import java.text.Collator;

/**
 * CollatorTestUtils is a utility class that provides methods that
 * Collator related tests can use.
 */
public final class CollatorTestUtils {

    // Utility class should not be instantiated
    private CollatorTestUtils() {}

    /*
     * These methods are utilities specific to the Collation tests..
     */

    /**
     * Compares two CollationElementIterators and throws an exception
     * with a message detailing which collation elements were not equal
     */
    public static void assertEqual(CollationElementIterator i1, CollationElementIterator i2) {
        int c1, c2, count = 0;
        do {
            c1 = i1.next();
            c2 = i2.next();
            if (c1 != c2) {
                throw new RuntimeException("    " + count + ": " + c1 + " != " + c2);
            }
            count++;
        } while (c1 != CollationElementIterator.NULLORDER);
    }

    // Replace non-printable characters with unicode escapes
    public static String prettify(String str) {
        StringBuilder result = new StringBuilder();

        String zero = "0000";

        for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (ch < 0x09 || (ch > 0x0A && ch < 0x20)|| (ch > 0x7E && ch < 0xA0) || ch > 0x100) {
                String hex = Integer.toString((int)ch,16);

                result.append("\\u").append(zero.substring(0, 4 - hex.length())).append(hex);
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    // Produce a printable representation of a CollationKey
    public static String prettify(CollationKey key) {
        StringBuilder result = new StringBuilder();
        byte[] bytes = key.toByteArray();

        for (int i = 0; i < bytes.length; i += 2) {
            int val = (bytes[i] << 8) + bytes[i+1];
            result.append(Integer.toString(val, 16)).append(" ");
        }
        return result.toString();
    }

    /*
     * Everything below here is boilerplate code that makes it possible
     * to add a new test by simply adding a function to an existing class
     */

    /**
     * Utility to test a collator with an array of test values.
     * See the other doTest() method for specific comparison details.
     */
    public static void doTest(Collator col, int strength,
                          String[] source, String[] target, int[] result) {
        if (source.length != target.length) {
            throw new RuntimeException("Data size mismatch: source = " +
                    source.length + ", target = " + target.length);
        }
        if (source.length != result.length) {
            throw new RuntimeException("Data size mismatch: source & target = " +
                    source.length + ", result = " + result.length);
        }

        col.setStrength(strength);
        for (int i = 0; i < source.length ; i++) {
            doTest(col, source[i], target[i], result[i]);
        }
    }

    /**
     * Test that a collator returns the correct relation result value when
     * comparing a source and target string. Also tests that the compare and collation
     * key results return the same value.
     */
    public static void doTest(Collator col,
                          String source, String target, int result) {
        char relation = '=';
        if (result <= -1) {
            relation = '<';
        } else if (result >= 1) {
            relation = '>';
        }

        int compareResult = col.compare(source, target);
        CollationKey sortKey1 = col.getCollationKey(source);
        CollationKey sortKey2 = col.getCollationKey(target);
        int keyResult = sortKey1.compareTo(sortKey2);
        if (compareResult != keyResult) {
            throw new RuntimeException("Compare and Collation Key results are different! Source = " +
                    source + " Target = " + target);
        }
        if (keyResult != result) {
            throw new RuntimeException("Collation Test failed! Source = " + source + " Target = " +
                    target + " result should be " + relation);
        }
    }
}
