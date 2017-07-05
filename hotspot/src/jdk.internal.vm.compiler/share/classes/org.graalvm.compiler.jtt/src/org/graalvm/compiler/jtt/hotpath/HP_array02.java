/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.jtt.hotpath;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class HP_array02 extends JTTTest {

    public static byte[] b = new byte[40];
    public static char[] c = new char[40];
    public static short[] s = new short[40];
    public static int[] iArray = new int[40];
    public static long[] l = new long[40];
    public static float[] f = new float[40];
    public static double[] d = new double[40];

    public static int test(int count) {
        int sum = 0;
        for (int x = 0; x < count; x++) {
            b[x] = (byte) x;
            c[x] = (char) x;
            s[x] = (short) x;
            iArray[x] = x;
            l[x] = x;
            f[x] = x;
            d[x] = x;
            sum += b[x] + c[x] + s[x] + iArray[x] + l[x] + f[x] + d[x];
        }
        return sum;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 40);
    }

}
