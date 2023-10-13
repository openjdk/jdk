/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.foreign;

import java.lang.foreign.MemorySegment;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Miscellaneous functions to read and write strings, in various charsets.
 */
public class StringSupport {
    public static String read(MemorySegment segment, long offset, Charset charset) {
        return switch (CharsetKind.of(charset)) {
            case SINGLE_BYTE -> readFast_byte(segment, offset, charset);
            case DOUBLE_BYTE -> readFast_short(segment, offset, charset);
            case QUAD_BYTE -> readFast_int(segment, offset, charset);
        };
    }

    public static void write(MemorySegment segment, long offset, Charset charset, String string) {
        switch (CharsetKind.of(charset)) {
            case SINGLE_BYTE -> writeFast_byte(segment, offset, charset, string);
            case DOUBLE_BYTE -> writeFast_short(segment, offset, charset, string);
            case QUAD_BYTE -> writeFast_int(segment, offset, charset, string);
        }
    }
    private static String readFast_byte(MemorySegment segment, long offset, Charset charset) {
        long len = strlen_byte(segment, offset);
        byte[] bytes = new byte[(int)len];
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, (int)len);
        return new String(bytes, charset);
    }

    private static void writeFast_byte(MemorySegment segment, long offset, Charset charset, String string) {
        byte[] bytes = string.getBytes(charset);
        MemorySegment.copy(bytes, 0, segment, JAVA_BYTE, offset, bytes.length);
        segment.set(JAVA_BYTE, offset + bytes.length, (byte)0);
    }

    private static String readFast_short(MemorySegment segment, long offset, Charset charset) {
        long len = strlen_short(segment, offset);
        byte[] bytes = new byte[(int)len];
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, (int)len);
        return new String(bytes, charset);
    }

    private static void writeFast_short(MemorySegment segment, long offset, Charset charset, String string) {
        byte[] bytes = string.getBytes(charset);
        MemorySegment.copy(bytes, 0, segment, JAVA_BYTE, offset, bytes.length);
        segment.set(JAVA_SHORT, offset + bytes.length, (short)0);
    }

    private static String readFast_int(MemorySegment segment, long offset, Charset charset) {
        long len = strlen_int(segment, offset);
        byte[] bytes = new byte[(int)len];
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, (int)len);
        return new String(bytes, charset);
    }

    private static void writeFast_int(MemorySegment segment, long offset, Charset charset, String string) {
        byte[] bytes = string.getBytes(charset);
        MemorySegment.copy(bytes, 0, segment, JAVA_BYTE, offset, bytes.length);
        segment.set(JAVA_INT, offset + bytes.length, 0);
    }

    private static int strlen_byte(MemorySegment segment, long start) {
        // iterate until overflow (String can only hold a byte[], whose length can be expressed as an int)
        for (int offset = 0; offset >= 0; offset++) {
            byte curr = segment.get(JAVA_BYTE, start + offset);
            if (curr == 0) {
                return offset;
            }
        }
        throw new IllegalArgumentException("String too large");
    }

    private static int strlen_short(MemorySegment segment, long start) {
        // iterate until overflow (String can only hold a byte[], whose length can be expressed as an int)
        for (int offset = 0; offset >= 0; offset += 2) {
            short curr = segment.get(JAVA_SHORT, start + offset);
            if (curr == 0) {
                return offset;
            }
        }
        throw new IllegalArgumentException("String too large");
    }

    private static int strlen_int(MemorySegment segment, long start) {
        // iterate until overflow (String can only hold a byte[], whose length can be expressed as an int)
        for (int offset = 0; offset >= 0; offset += 4) {
            int curr = segment.get(JAVA_INT, start + offset);
            if (curr == 0) {
                return offset;
            }
        }
        throw new IllegalArgumentException("String too large");
    }

    public enum CharsetKind {
        SINGLE_BYTE(1),
        DOUBLE_BYTE(2),
        QUAD_BYTE(4);

        final int terminatorCharSize;

        CharsetKind(int terminatorCharSize) {
            this.terminatorCharSize = terminatorCharSize;
        }

        public int terminatorCharSize() {
            return terminatorCharSize;
        }

        public static CharsetKind of(Charset charset) {
            if (charset == StandardCharsets.UTF_8 || charset == StandardCharsets.ISO_8859_1 || charset == StandardCharsets.US_ASCII) {
                return CharsetKind.SINGLE_BYTE;
            } else if (charset == StandardCharsets.UTF_16LE || charset == StandardCharsets.UTF_16BE || charset == StandardCharsets.UTF_16) {
                return CharsetKind.DOUBLE_BYTE;
            } else if (charset == StandardCharsets.UTF_32LE || charset == StandardCharsets.UTF_32BE || charset == StandardCharsets.UTF_32) {
                return CharsetKind.QUAD_BYTE;
            } else {
                throw new IllegalArgumentException("Unsupported charset: " + charset);
            }
        }
    }
}
