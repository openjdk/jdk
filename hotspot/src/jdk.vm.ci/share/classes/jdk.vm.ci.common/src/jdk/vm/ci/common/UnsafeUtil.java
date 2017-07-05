/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.common;

import jdk.internal.misc.Unsafe;

/**
 * Utilities for operating on raw memory with {@link Unsafe}.
 */
public class UnsafeUtil {

    /**
     * Copies the contents of a {@link String} to a native memory buffer as a {@code '\0'}
     * terminated C string. The native memory buffer is allocated via
     * {@link Unsafe#allocateMemory(long)}. The caller is responsible for releasing the buffer when
     * it is no longer needed via {@link Unsafe#freeMemory(long)}.
     *
     * @return the native memory pointer of the C string created from {@code s}
     */
    public static long createCString(Unsafe unsafe, String s) {
        return writeCString(unsafe, s, unsafe.allocateMemory(s.length() + 1));
    }

    /**
     * Reads a {@code '\0'} terminated C string from native memory and converts it to a
     * {@link String}.
     *
     * @return a Java string
     */
    public static String readCString(Unsafe unsafe, long address) {
        if (address == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0;; i++) {
            char c = (char) unsafe.getByte(address + i);
            if (c == 0) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Writes the contents of a {@link String} to a native memory buffer as a {@code '\0'}
     * terminated C string. The caller is responsible for ensuring the buffer is at least
     * {@code s.length() + 1} bytes long. The caller is also responsible for releasing the buffer
     * when it is no longer.
     *
     * @return the value of {@code buf}
     */
    public static long writeCString(Unsafe unsafe, String s, long buf) {
        int size = s.length();
        for (int i = 0; i < size; i++) {
            unsafe.putByte(buf + i, (byte) s.charAt(i));
        }
        unsafe.putByte(buf + size, (byte) '\0');
        return buf;
    }
}
