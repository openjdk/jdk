/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc.jnilibelf;

import jdk.internal.misc.Unsafe;

import static jdk.tools.jaotc.jnilibelf.UnsafeAccess.UNSAFE;

public class Pointer {

    private final long address;

    public Pointer(long val) {
        address = val;
    }

    /**
     * Put (i.e., copy) content of byte array at consecutive addresses beginning at this Pointer.
     *
     * @param src source byte array
     */
    public void put(byte[] src) {
        UNSAFE.copyMemory(src, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, address, src.length);
    }

    /**
     * Get (i.e., copy) content at this Pointer to the given byte array.
     *
     * @param dst destination byte array
     */
    public void get(byte[] dst) {
        UNSAFE.copyMemory(null, address, dst, Unsafe.ARRAY_BYTE_BASE_OFFSET, dst.length);
    }

    /**
     * Read {@code readSize} number of bytes to copy them starting at {@code startIndex} of
     * {@code byteArray}
     *
     * @param byteArray target array to copy bytes
     * @param readSize number of bytes to copy
     * @param startIndex index of the array to start copy at
     */
    public void copyBytesTo(byte[] byteArray, int readSize, int startIndex) {
        long end = (long)startIndex + (long)readSize;
        if (end > byteArray.length) {
            throw new IllegalArgumentException("writing beyond array bounds");
        }
        UNSAFE.copyMemory(null, address, byteArray, Unsafe.ARRAY_BYTE_BASE_OFFSET+startIndex, readSize);
    }

}
