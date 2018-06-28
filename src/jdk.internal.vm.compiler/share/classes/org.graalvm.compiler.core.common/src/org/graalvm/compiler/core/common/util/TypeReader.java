/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.common.util;

/**
 * Provides low-level read access for signed and unsigned values of size 1, 2, 4, and 8 bytes.
 */
public interface TypeReader {

    /** Returns the next byte index to be read. */
    long getByteIndex();

    /** Sets the next byte index to be read. */
    void setByteIndex(long byteIndex);

    /** Reads a signed 1 byte value. */
    int getS1();

    /** Reads an unsigned 1 byte value. */
    int getU1();

    /** Reads a signed 2 byte value. */
    int getS2();

    /** Reads an unsigned 2 byte value. */
    int getU2();

    /** Reads a signed 4 byte value. */
    int getS4();

    /** Reads an unsigned 4 byte value. */
    long getU4();

    /** Reads a signed 4 byte value. */
    long getS8();

    /**
     * Reads a signed value that has been written using {@link TypeWriter#putSV variable byte size
     * encoding}.
     */
    default long getSV() {
        long result = 0;
        int shift = 0;
        long b;
        do {
            b = getU1();
            result |= (b & 0x7f) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);

        if ((b & 0x40) != 0 && shift < 64) {
            result |= -1L << shift;
        }
        return result;
    }

    /**
     * Reads a signed variable byte size encoded value that is known to fit into the range of int.
     */
    default int getSVInt() {
        return TypeConversion.asS4(getSV());
    }

    /**
     * Reads an unsigned value that has been written using {@link TypeWriter#putSV variable byte
     * size encoding}.
     */
    default long getUV() {
        long result = 0;
        int shift = 0;
        long b;
        do {
            b = getU1();
            result |= (b & 0x7f) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);

        return result;
    }

    /**
     * Reads an unsigned variable byte size encoded value that is known to fit into the range of
     * int.
     */
    default int getUVInt() {
        return TypeConversion.asS4(getUV());
    }
}
