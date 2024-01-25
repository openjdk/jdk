/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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

/**
 * @test
 * @bug 8283408
 * @summary Fill a byte array with Java Unsafe API
 * @run main/othervm -XX:+OptimizeFill compiler.loopopts.FillArrayWithUnsafe
 */

package compiler.loopopts;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

public class FillArrayWithUnsafe {

    private static Unsafe unsafe;

    public static void main(String[] args) throws Exception {
        Class klass = Unsafe.class;
        Field field = klass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        unsafe = (Unsafe) field.get(null);

        byte[] buffer;
        // Make sure method newByteArray is compiled by C2
        for (int i = 0; i < 50000; i++) {
            buffer = newByteArray(100, (byte) 0x80);
        }
    }

    public static byte[] newByteArray(int size, byte val) {
        byte[] arr = new byte[size];
        int offset = unsafe.arrayBaseOffset(byte[].class);
        for (int i = offset; i < offset + size; i++) {
             unsafe.putByte(arr, i, val);
        }
        return arr;
    }
}

