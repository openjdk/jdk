/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * Provides low-level sequential write access for signed and unsigned values of size 1, 2, 4, and 8
 * bytes.
 */
public interface TypeWriter {

    /**
     * Returns the number of bytes that have been written, i.e., the byte index of the next byte to
     * be written.
     */
    long getBytesWritten();

    /** Writes a signed 1 byte value. */
    void putS1(long value);

    /** Writes an unsigned 1 byte value. */
    void putU1(long value);

    /** Writes a signed 2 byte value. */
    void putS2(long value);

    /** Writes an unsigned 2 byte value. */
    void putU2(long value);

    /** Writes a signed 4 byte value. */
    void putS4(long value);

    /** Writes an unsigned 4 byte value. */
    void putU4(long value);

    /** Writes a signed 8 byte value. */
    void putS8(long value);

    /**
     * Writes a signed value in a variable byte size encoding.
     */
    default void putSV(long value) {
        long cur = value;
        while (true) {
            if (cur >= -64 && cur < 64) {
                putU1(cur & 0x7f);
                return;
            }
            putU1(0x80 | (cur & 0x7f));
            cur = cur >> 7;
        }
    }

    /**
     * Writes an unsigned value in a variable byte size encoding.
     */
    default void putUV(long value) {
        long cur = value;
        while (true) {
            assert cur >= 0;
            if (cur < 128) {
                putU1(cur & 0x7f);
                return;
            }
            putU1(0x80 | (cur & 0x7f));
            cur = cur >> 7;
        }
    }
}
