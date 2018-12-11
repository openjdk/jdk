/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.optimize;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 * Tests optimization of switches.
 */
public class Switch02 extends JTTTest {
    private static char staticCharVal = 0;
    private static short staticShortVal = 0;
    private static byte staticByteVal = 0;

    public static int test(int arg) {
        switch (arg) {
            case 1:
                return 2;
            default:
                return 1;
        }
    }

    public static int test2char(char arg) {
        int result = 392123;
        Object x = null;
        char val = staticCharVal != 0 ? staticCharVal : arg;
        switch (val) {
            case (char) 0xFFFF:
                result = 23212 / val;
                break;
            case (char) 0xFFFF - 3:
                result = 932991439 / val;
                break;
            case (char) 0xFFFF - 6:
                result = 47329561 / val;
                break;
            case (char) 0xFFFF - 9:
                result = 1950976984 / val;
                break;
            case (char) 0xFFFF - 10:
                result = 97105581 / val;
                switch (result) {
                    case 1:
                        result = 321;
                        break;
                    default:
                        result = 2391;
                        break;
                }
                break;
            case (char) 0xFFFF - 12:
                result = 99757362 / val;
                break;
            case (char) 0xFFFF - 15:
                result = 912573 / val;
                x = new LinkedList<>();
                break;
            case (char) 0xFFFF - 18:
                x = new HashSet<>();
                result = 876765 / val;
                break;
            case (char) 0xFFFF - 19:
                result = 75442917 / val;
                break;
            case (char) 0xFFFF - 21:
                result = 858112498 / val;
                x = new HashMap<>();
                break;
            default:
                result = 34324341 / val;
        }
        result = result + (x == null ? 0 : x.hashCode());
        return result;
    }

    public static int test2short(short arg) {
        int result = 392123;
        Object x = null;
        short val = staticShortVal != 0 ? staticShortVal : arg;
        switch (val) {
            case (short) -0x7FFF:
                result = 23212 / val;
                break;
            case (short) -0x7FFF + 3:
                result = 932991439 / val;
                break;
            case (short) -0x7FFF + 6:
                result = 47329561 / val;
                break;
            case (short) -0x7FFF + 9:
                result = 1950976984 / val;
                break;
            case (short) -0x7FFF + 10:
                result = 97105581 / val;
                switch (result) {
                    case 1:
                        result = 321;
                        break;
                    default:
                        result = 2391;
                        break;
                }
                break;
            case (short) -0x7FFF + 12:
                result = 99757362 / val;
                break;
            case (short) -0x7FFF + 15:
                result = 912573 / val;
                x = new LinkedList<>();
                break;
            case (short) -0x7FFF + 18:
                x = new HashSet<>();
                result = 876765 / val;
                break;
            case (short) -0x7FFF + 19:
                result = 75442917 / val;
                break;
            case (short) -0x7FFF + 21:
                result = 858112498 / val;
                x = new HashMap<>();
                break;
            default:
                result = 34324341 / val;
        }
        result = result + (x == null ? 0 : x.hashCode());
        return result;
    }

    public static int test2byte(byte arg) {
        int result = 392123;
        Object x = null;
        byte val = staticByteVal != 0 ? staticByteVal : arg;
        switch (val) {
            case (byte) -0x7F:
                result = 23212 / val;
                break;
            case (byte) -0x7F + 3:
                result = 932991439 / val;
                break;
            case (byte) -0x7F + 6:
                result = 47329561 / val;
                break;
            case (byte) -0x7F + 9:
                result = 1950976984 / val;
                break;
            case (byte) -0x7F + 10:
                result = 97105581 / val;
                switch (result) {
                    case 1:
                        result = 321;
                        break;
                    default:
                        result = 2391;
                        break;
                }
                break;
            case (byte) -0x7F + 12:
                result = 99757362 / val;
                break;
            case (byte) -0x7F + 15:
                result = 912573 / val;
                x = new LinkedList<>();
                break;
            case (byte) -0x7F + 18:
                x = new HashSet<>();
                result = 876765 / val;
                break;
            case (byte) -0x7F + 19:
                result = 75442917 / val;
                break;
            case (byte) -0x7F + 20:
                result = 856261268 / val;
                break;
            case (byte) -0x7F + 21:
                result = 858112498 / val;
                x = new HashMap<>();
                break;
            default:
                result = 34324341 / val;
        }
        result = result + (x == null ? 0 : x.hashCode());
        return result;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 0);
    }

    @Test
    public void run1() throws Throwable {
        runTest("test", 1);
    }

    @Test
    public void run2() throws Throwable {
        runTest("test2char", (char) (0x0));
        runTest("test2char", (char) (0xFFFF));
        runTest("test2char", (char) (0xFFFF - 21)); // miss
        runTest("test2char", (char) (0xFFFF - 22)); // hit
        runTest("test2char", (char) (0xFFFF - 23)); // miss (out of bound)

        staticCharVal = (char) 0xFFFF;
        runTest("test2char", (char) 0);
        staticCharVal = (char) (0xFFFF - 21);
        runTest("test2char", (char) 0xFFFF);
        staticCharVal = (char) (0xFFFF - 22);
        runTest("test2char", (char) 0xFFFF);
        staticCharVal = (char) (0xFFFF - 23);
        runTest("test2char", (char) 0xFFFF);
    }

    @Test
    public void run3() throws Throwable {
        runTest("test2short", (short) 0x0);
        runTest("test2short", (short) -0x7FFF);
        runTest("test2short", (short) (-0x7FFF + 21)); // Miss
        runTest("test2short", (short) (-0x7FFF + 22)); // hit
        runTest("test2short", (short) (-0x7FFF + 23)); // miss (out of bound)
        runTest("test2short", (short) 0x7FFF);         // miss (out of bound)

        staticShortVal = (short) -0x7FFF;
        runTest("test2short", (short) 0);
        staticShortVal = (short) (-0x7FFF + 21);
        runTest("test2short", (short) 0);
        staticShortVal = (short) (-0x7FFF + 22);
        runTest("test2short", (short) 0);
        staticShortVal = (short) (-0x7FFF + 23);
        runTest("test2short", (short) 0);
        staticShortVal = (short) 0x7FFF;
        runTest("test2short", (short) 0);
    }

    @Test
    public void run4() throws Throwable {
        runTest("test2byte", (byte) 0);
        runTest("test2byte", (byte) -0x7F);
        runTest("test2byte", (byte) (-0x7F + 21)); // Miss
        runTest("test2byte", (byte) (-0x7F + 22)); // hit
        runTest("test2byte", (byte) (-0x7F + 23)); // miss (out of bound)
        runTest("test2byte", (byte) 0x7F);         // miss (out of bound)

        staticByteVal = (byte) -0x7F;
        runTest("test2short", (short) 0);
        staticByteVal = (byte) (-0x7F + 21);
        runTest("test2short", (short) 0);
        staticByteVal = (byte) (-0x7F + 22);
        runTest("test2short", (short) 0);
        staticByteVal = (byte) (-0x7F + 23);
        runTest("test2short", (short) 0);
        staticByteVal = (byte) 0x7F;
        runTest("test2short", (short) 0);
    }
}
