/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8054307 8311906 8321514
 * @summary test String chars() and codePoints()
 * @run main/othervm Chars
 */

import java.util.Arrays;
import java.util.Random;

public class Chars {

    public static void main(String[] args) {
        Random r = new Random();
        for (int i = 0; i < 10; i++) {
            int n = 100 + r.nextInt(100);
            char[] cc = new char[n];
            int[]  ccExp = new int[n];
            int[]  cpExp = new int[n];
            // latin1
            for (int j = 0; j < n; j++) {
                cc[j] = (char)(ccExp[j] = cpExp[j] = r.nextInt(0x80));
            }
            testChars(cc, ccExp);
            testCharsSubrange(cc, ccExp);
            testIntsSubrange(ccExp);
            testCPs(cc, cpExp);

            // bmp without surrogates
            for (int j = 0; j < n; j++) {
                cc[j] = (char)(ccExp[j] = cpExp[j] = r.nextInt(0x8000));
            }
            testChars(cc, ccExp);
            testCharsSubrange(cc, ccExp);
            testCPs(cc, cpExp);

            // bmp with surrogates
            int k = 0;
            for (int j = 0; j < n; j++) {
                if (j % 9 ==  5 && j + 1 < n) {
                    int cp = 0x10000 + r.nextInt(2000);
                    cpExp[k++] = cp;
                    Character.toChars(cp, cc, j);
                    ccExp[j] = cc[j];
                    ccExp[j + 1] = cc[j + 1];
                    j++;
                } else {
                    cc[j] = (char)(ccExp[j] = cpExp[k++] = r.nextInt(0x8000));
                }
            }
            cpExp = Arrays.copyOf(cpExp, k);
            testChars(cc, ccExp);
            testCharsSubrange(cc, ccExp);
            testIntsSubrange(ccExp);
            testCPs(cc, cpExp);
        }
    }

    static void testChars(char[] cc, int[] expected) {
        String str = new String(cc);
        if (!Arrays.equals(expected, str.chars().toArray())) {
            throw new RuntimeException("testChars failed!");
        }
    }

    static void testCharsSubrange(char[] cc, int[] expected) {
        int[] offsets = { 7, 31 };   // offsets to test
        int LENGTH = 13;
        for (int i = 0; i < offsets.length; i++) {
            int offset = Math.max(0, offsets[i]);       // confine to the input array
            int count = Math.min(LENGTH, cc.length - offset);
            String str = new String(cc, offset, count);
            int[] actual = str.chars().toArray();
            int errOffset = Arrays.mismatch(actual, 0, actual.length,
                    expected, offset, offset + count);
            if (errOffset >= 0) {
                System.err.printf("expected[%d] (%d) != actual[%d] (%d)%n",
                        offset + errOffset, expected[offset + errOffset],
                        errOffset, actual[errOffset]);
                System.err.println("expected: " + Arrays.toString(expected));
                System.err.println("actual: " + Arrays.toString(actual));
                throw new RuntimeException("testCharsSubrange failed!");
            }
        }
    }

    static void testIntsSubrange(int[] expected) {
        int[] offsets = { 7, 31 };   // offsets to test
        int LENGTH = 13;
        for (int i = 0; i < offsets.length; i++) {
            int offset = Math.max(0, offsets[i]);       // confine to the input array
            int count = Math.min(LENGTH, expected.length - offset);
            String str = new String(expected, offset, count);
            int[] actual = str.chars().toArray();
            int errOffset = Arrays.mismatch(actual, 0, actual.length,
                    expected, offset, offset + count);
            if (errOffset >= 0) {
                System.err.printf("expected[%d] (%d) != actual[%d] (%d)%n",
                        offset + errOffset, expected[offset + errOffset],
                        errOffset, actual[errOffset]);
                System.err.println("expected: " + Arrays.toString(expected));
                System.err.println("actual: " + Arrays.toString(actual));
                throw new RuntimeException("testIntsSubrange failed!");
            }
        }
    }

    static void testCPs(char[] cc, int[] expected) {
        String str = new String(cc);
        if (!Arrays.equals(expected, str.codePoints().toArray())) {
            throw new RuntimeException("testCPs failed!");
        }
    }
}
