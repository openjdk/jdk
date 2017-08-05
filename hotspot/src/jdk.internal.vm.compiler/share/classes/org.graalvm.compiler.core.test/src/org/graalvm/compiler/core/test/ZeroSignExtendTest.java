/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, Red Hat Inc. All rights reserved.
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
package org.graalvm.compiler.core.test;

import org.junit.Test;

/*
 * Test compilation of ZeroExtend and SignExtend nodes
 */

public class ZeroSignExtendTest extends GraalCompilerTest {

    int testSnippet1(char[] chars) {
        int x = 1;
        x += chars[0];
        x -= chars[1];
        x *= chars[2];
        x /= chars[3];
        x &= chars[4];
        x |= chars[5];
        x ^= chars[6];
        x <<= chars[7];
        x >>= (chars[8] - chars[0]);
        x >>>= (chars[9] - chars[0]);
        x += chars[1];
        return x;
    }

    long testSnippet2(char[] chars) {
        long y = 2;
        y += chars[0];
        y -= chars[1];
        y *= chars[2];
        y /= chars[3];
        y &= chars[4];
        y |= chars[5];
        y ^= chars[6];
        y <<= chars[7];
        y >>= (chars[8] - chars[0]);
        y >>>= (chars[9] - chars[0]);
        y += chars[1];
        return y;
    }

    int testSnippet3(short[] shorts) {
        int x = 1;
        x += shorts[0];
        x -= shorts[1];
        x *= shorts[2];
        x /= shorts[3];
        x &= shorts[4];
        x |= shorts[5];
        x ^= shorts[6];
        x <<= shorts[7];
        x >>= (shorts[8] - shorts[0]);
        x >>>= (shorts[9] - shorts[0]);
        x += shorts[1];
        return x;
    }

    long testSnippet4(short[] shorts) {
        long y = 2;
        y += shorts[0];
        y -= shorts[1];
        y *= shorts[2];
        y /= shorts[3];
        y &= shorts[4];
        y |= shorts[5];
        y ^= shorts[6];
        y <<= shorts[7];
        y >>= (shorts[8] - shorts[0]);
        y >>>= (shorts[9] - shorts[0]);
        y += shorts[1];
        return y;
    }

    int testSnippet5(byte[] bytes) {
        int x = 1;
        x += bytes[0];
        x -= bytes[1];
        x *= bytes[2];
        x /= bytes[3];
        x &= bytes[4];
        x |= bytes[5];
        x ^= bytes[6];
        x <<= bytes[7];
        x >>= (bytes[8] - bytes[0]);
        x >>>= (bytes[9] - bytes[0]);
        x += bytes[1];
        return x;
    }

    long testSnippet6(byte[] bytes) {
        long y = 2;
        y += bytes[0];
        y -= bytes[1];
        y *= bytes[2];
        y /= bytes[3];
        y &= bytes[4];
        y |= bytes[5];
        y ^= bytes[6];
        y <<= bytes[7];
        y >>= (bytes[8] - bytes[0]);
        y >>>= (bytes[9] - bytes[0]);
        y += bytes[1];
        return y;
    }

    @Test

    public void test() {
        char[] input1 = new char[]{'0', '1', '2', '3', '4', '5', '7', '8', '9', 'A'};
        char[] input2 = new char[]{'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K'};

        short[] input3 = new short[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        short[] input4 = new short[]{11, 12, 13, 14, 15, 16, 17, 18, 19, 20};

        byte[] input5 = new byte[]{21, 22, 23, 24, 25, 26, 27, 28, 29, 30};
        byte[] input6 = new byte[]{11, 12, 13, 14, 15, 16, 17, 18, 19, 40};

        test("testSnippet1", input1);
        test("testSnippet2", input2);
        test("testSnippet3", input3);
        test("testSnippet4", input4);
        test("testSnippet5", input5);
        test("testSnippet6", input6);
    }
}
