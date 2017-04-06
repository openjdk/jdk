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
package org.graalvm.compiler.jtt.bytecode;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class BC_arraylength extends JTTTest {

    static byte[] array0 = {1, 2};
    static char[] array1 = {'a', 'b', 'c', 'd'};
    static short[] array2 = {1, 2, 3, 4, 5, 6};
    static int[] array3 = {1, 2, 3};
    static long[] array4 = {1L, 2L, 3L, 4L};
    static float[] array5 = {0.1f, 0.2f};
    static double[] array6 = {0.1, 0.2, 0.3, 0.4};
    static Object[] array7 = new Object[5];
    static boolean[] array8 = {false, true, false};

    public static int testByte(byte[] arg) {
        return arg.length;
    }

    public static int testChar(char[] arg) {
        return arg.length;
    }

    public static int testShort(short[] arg) {
        return arg.length;
    }

    public static int testInt(int[] arg) {
        return arg.length;
    }

    public static int testLong(long[] arg) {
        return arg.length;
    }

    public static int testFloat(float[] arg) {
        return arg.length;
    }

    public static int testDouble(double[] arg) {
        return arg.length;
    }

    public static int testObject(Object[] arg) {
        return arg.length;
    }

    public static int testBoolean(boolean[] arg) {
        return arg.length;
    }

    @Test
    public void run0() throws Throwable {
        runTest("testByte", array0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("testChar", array1);
    }

    @Test
    public void run2() throws Throwable {
        runTest("testShort", array2);
    }

    @Test
    public void run3() throws Throwable {
        runTest("testInt", array3);
    }

    @Test
    public void run4() throws Throwable {
        runTest("testLong", array4);
    }

    @Test
    public void run5() throws Throwable {
        runTest("testFloat", array5);
    }

    @Test
    public void run6() throws Throwable {
        runTest("testDouble", array6);
    }

    @Test
    public void run7() throws Throwable {
        runTest("testObject", new Object[]{array7});
    }

    @Test
    public void run8() throws Throwable {
        runTest("testBoolean", array8);
    }

}
