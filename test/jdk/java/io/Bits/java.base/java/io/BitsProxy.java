/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

/**
 * Class to allow public access to package-private methods.
 */
public final class BitsProxy {

    public static boolean getBoolean(byte[] b, int off) {
        return Bits.getBoolean(b, off);
    }

    public static char getChar(byte[] b, int off) {
        return Bits.getChar(b, off);
    }

    public static short getShort(byte[] b, int off) {
        return Bits.getShort(b, off);
    }

    public static int getInt(byte[] b, int off) {
        return Bits.getInt(b, off);
    }

    public static float getFloat(byte[] b, int off) {
        return Bits.getFloat(b, off);
    }

    public static long getLong(byte[] b, int off) {
        return Bits.getLong(b, off);
    }

    public static double getDouble(byte[] b, int off) {
        return Bits.getDouble(b, off);
    }


    public static void putBoolean(byte[] b, int off, boolean val) {
        Bits.putBoolean(b, off, val);
    }

    public static void putChar(byte[] b, int off, char val) {
        Bits.putChar(b, off, val);
    }

    public static void putShort(byte[] b, int off, short val) {
        Bits.putShort(b, off, val);
    }

    public static void putInt(byte[] b, int off, int val) {
        Bits.putInt(b, off, val);
    }

    public static void putFloat(byte[] b, int off, float val) {
        Bits.putFloat(b, off, val);
    }

    public static void putLong(byte[] b, int off, long val) {
        Bits.putLong(b, off, val);
    }

    public static void putDouble(byte[] b, int off, double val) {
        Bits.putDouble(b, off, val);
    }

}
