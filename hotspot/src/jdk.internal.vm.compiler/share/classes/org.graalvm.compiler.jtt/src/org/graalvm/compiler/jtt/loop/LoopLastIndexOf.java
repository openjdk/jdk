/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.loop;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 * see java.lang.String.lastIndexOf(char[], int, int, char[], int ,int, int)
 */
public class LoopLastIndexOf extends JTTTest {

    private static final char[] v1 = new char[]{'a', 'b', 'c', 'd', 'a', 'b', 'c', 'd', 'a', 'b', 'c', 'd'};
    private static final char[] v2 = new char[]{'d', 'a'};
    private static final char[] v3 = new char[]{'d', 'b', 'c'};
    private static final char[] v4 = new char[]{'z', 'a', 'b', 'c'};

    public static int test(char[] source, int sourceOffset, int sourceCount, char[] target, int targetOffset, int targetCount, int fromIndexParam) {
        int rightIndex = sourceCount - targetCount;
        int fromIndex = fromIndexParam;
        if (fromIndex < 0) {
            return -1;
        }
        if (fromIndex > rightIndex) {
            fromIndex = rightIndex;
        }
        /* Empty string always matches. */
        if (targetCount == 0) {
            return fromIndex;
        }

        int strLastIndex = targetOffset + targetCount - 1;
        char strLastChar = target[strLastIndex];
        int min = sourceOffset + targetCount - 1;
        int i = min + fromIndex;

        startSearchForLastChar: while (true) {
            while (i >= min && source[i] != strLastChar) {
                i--;
            }
            if (i < min) {
                return -1;
            }
            int j = i - 1;
            int start = j - (targetCount - 1);
            int k = strLastIndex - 1;

            while (j > start) {
                if (source[j--] != target[k--]) {
                    i--;
                    continue startSearchForLastChar;
                }
            }
            return start - sourceOffset + 1;
        }
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", v1, 0, v1.length, v2, 0, v2.length, 10);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", v1, 0, v1.length, v3, 0, v3.length, 10);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test", v1, 0, v1.length, v4, 0, v4.length, 10);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test", v1, 1, v1.length - 1, v3, 0, v3.length, 10);
    }

    @Test
    // (expected = ArrayIndexOutOfBoundsException.class)
    public void run4() throws Throwable {
        runTest("test", v1, 1, v1.length, v3, 0, v3.length, 10);
    }
}
