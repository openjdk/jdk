/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/**
 * inspired by java.security.SecureRandom.longToByteArray(long).
 *
 */
public class LongToSomethingArray01 extends JTTTest {

    public static byte[] longToByteArray(long arg) {
        long l = arg;
        byte[] ret = new byte[8];
        for (int i = 0; i < 8; i++) {
            ret[i] = (byte) (l & 0xff);
            l = l >> 8;
        }
        return ret;
    }

    @Test
    public void runB0() throws Throwable {
        runTest("longToByteArray", 0x1122_3344_5566_7788L);
    }

    public static short[] longToShortArray(long arg) {
        long l = arg;
        short[] ret = new short[4];
        for (int i = 0; i < 4; i++) {
            ret[i] = (short) (l & 0xffff);
            l = l >> 16;
        }
        return ret;
    }

    @Test
    public void runS0() throws Throwable {
        runTest("longToShortArray", 0x1122_3344_5566_7788L);
    }

    public static int[] longToIntArray(long arg) {
        long l = arg;
        int[] ret = new int[2];
        for (int i = 0; i < 2; i++) {
            ret[i] = (int) (l & 0xffff_ffff);
            l = l >> 32;
        }
        return ret;
    }

    @Test
    public void runI0() throws Throwable {
        runTest("longToIntArray", 0x1122_3344_5566_7788L);
    }

    public static long[] longToLongArray(long arg) {
        long l = arg;
        long[] ret = new long[1];
        for (int i = 0; i < 1; i++) {
            ret[i] = l & 0xffff_ffff_ffff_ffffL;
            l = l >> 64;
        }
        return ret;
    }

    @Test
    public void runL0() throws Throwable {
        runTest("longToLongArray", 0x1122_3344_5566_7788L);
    }
}
