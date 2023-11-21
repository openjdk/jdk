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

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.abi.SharedUtils;
import jdk.internal.util.ArraysSupport;
import sun.security.action.GetPropertyAction;

import java.lang.foreign.MemorySegment;
import java.nio.charset.Charset;

import static java.lang.foreign.ValueLayout.*;

/**
 * Miscellaneous functions to read and write strings, in various charsets.
 */
public final class StringSupport {

    static final JavaLangAccess JAVA_LANG_ACCESS = SharedSecrets.getJavaLangAccess();

    private StringSupport() {}

    public static String read(MemorySegment segment, long offset, Charset charset) {
        return switch (CharsetKind.of(charset)) {
            case SINGLE_BYTE -> readByte(segment, offset, charset);
            case DOUBLE_BYTE -> readShort(segment, offset, charset);
            case QUAD_BYTE -> readInt(segment, offset, charset);
        };
    }

    public static void write(MemorySegment segment, long offset, Charset charset, String string) {
        switch (CharsetKind.of(charset)) {
            case SINGLE_BYTE -> writeByte(segment, offset, charset, string);
            case DOUBLE_BYTE -> writeShort(segment, offset, charset, string);
            case QUAD_BYTE -> writeInt(segment, offset, charset, string);
        }
    }

    private static String readByte(MemorySegment segment, long offset, Charset charset) {
        long len = chunkedStrlenByte(segment, offset);
        byte[] bytes = new byte[(int)len];
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, (int)len);
        return new String(bytes, charset);
    }

    private static void writeByte(MemorySegment segment, long offset, Charset charset, String string) {
        int bytes = copyBytes(string, segment, charset, offset);
        segment.set(JAVA_BYTE, offset + bytes, (byte)0);
    }

    private static String readShort(MemorySegment segment, long offset, Charset charset) {
        long len = chunkedStrlenShort(segment, offset);
        byte[] bytes = new byte[(int)len];
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, (int)len);
        return new String(bytes, charset);
    }

    private static void writeShort(MemorySegment segment, long offset, Charset charset, String string) {
        int bytes = copyBytes(string, segment, charset, offset);
        segment.set(JAVA_SHORT, offset + bytes, (short)0);
    }

    private static String readInt(MemorySegment segment, long offset, Charset charset) {
        long len = strlenInt(segment, offset);
        byte[] bytes = new byte[(int)len];
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, (int)len);
        return new String(bytes, charset);
    }

    private static void writeInt(MemorySegment segment, long offset, Charset charset, String string) {
        int bytes = copyBytes(string, segment, charset, offset);
        segment.set(JAVA_INT, offset + bytes, 0);
    }

    /**
     * {@return the shortest distance beginning at the provided {@code start}
     *  to the encountering of a zero byte in the provided {@code segment}}
     * <p>
     * The method divides the region of interest into three distinct regions:
     * <ul>
     *     <li>head (access made on a byte-by-byte basis) (if any)</li>
     *     <li>body (access made with eight bytes at a time at physically 64-bit-aligned memory) (if any)</li>
     *     <li>tail (access made on a byte-by-byte basis) (if any)</li>
     * </ul>
     * <p>
     * The body is using a heuristic method to determine if a long word
     * contains a zero byte. The method might have false positives but
     * never false negatives.
     * <p>
     * This method is inspired by the `glibc/string/strlen.c` implementation
     *
     * @param segment to examine
     * @param start   from where examination shall begin
     * @throws IllegalArgumentException if the examined region contains no zero bytes
     *                                  within a length that can be accepted by a String
     */
    public static int chunkedStrlenByte(MemorySegment segment, long start) {

        // Handle the first unaligned "head" bytes separately
        int headCount = (int)SharedUtils.remainsToAlignment(segment.address() + start, Long.BYTES);

        int offset = 0;
        for (; offset < headCount; offset++) {
            byte curr = segment.get(JAVA_BYTE, start + offset);
            if (curr == 0) {
                return offset;
            }
        }

        // We are now on a long-aligned boundary so this is the "body"
        int bodyCount = bodyCount(segment.byteSize() - start - headCount);

        for (; offset < bodyCount; offset += Long.BYTES) {
            // We know we are `long` aligned so, we can save on alignment checking here
            long curr = segment.get(JAVA_LONG_UNALIGNED, start + offset);
            // Is this a candidate?
            if (mightContainZeroByte(curr)) {
                for (int j = 0; j < 8; j++) {
                    if (segment.get(JAVA_BYTE, start + offset + j) == 0) {
                        return offset + j;
                    }
                }
            }
        }

        // Handle the "tail"
        return requireWithinArraySize((long) offset + strlenByte(segment, start + offset));
    }

    /* Bits 63 and N * 8 (N = 1..7) of this number are zero.  Call these bits
       the "holes".  Note that there is a hole just to the left of
       each byte, with an extra at the end:

       bits:  01111110 11111110 11111110 11111110 11111110 11111110 11111110 11111111
       bytes: AAAAAAAA BBBBBBBB CCCCCCCC DDDDDDDD EEEEEEEE FFFFFFFF GGGGGGGG HHHHHHHH

       The 1-bits make sure that carries propagate to the next 0-bit.
       The 0-bits provide holes for carries to fall into.
    */
    private static final long HIMAGIC_FOR_BYTES = 0x8080_8080_8080_8080L;
    private static final long LOMAGIC_FOR_BYTES = 0x0101_0101_0101_0101L;

    static boolean mightContainZeroByte(long l) {
        return ((l - LOMAGIC_FOR_BYTES) & (~l) & HIMAGIC_FOR_BYTES) != 0;
    }

    private static final long HIMAGIC_FOR_SHORTS = 0x8000_8000_8000_8000L;
    private static final long LOMAGIC_FOR_SHORTS = 0x0001_0001_0001_0001L;

    static boolean mightContainZeroShort(long l) {
        return ((l - LOMAGIC_FOR_SHORTS) & (~l) & HIMAGIC_FOR_SHORTS) != 0;
    }

    static int requireWithinArraySize(long size) {
        if (size > ArraysSupport.SOFT_MAX_ARRAY_LENGTH) {
            throw newIaeStringTooLarge();
        }
        return (int) size;
    }

    static int bodyCount(long remaining) {
        return (int) Math.min(
                // Make sure we do not wrap around
                Integer.MAX_VALUE - Long.BYTES,
                // Remaining bytes to consider
                remaining)
                & -Long.BYTES; // Mask 0xFFFFFFF8
    }

    private static int strlenByte(MemorySegment segment, long start) {
        for (int offset = 0; offset < ArraysSupport.SOFT_MAX_ARRAY_LENGTH; offset += 1) {
            byte curr = segment.get(JAVA_BYTE, start + offset);
            if (curr == 0) {
                return offset;
            }
        }
        throw newIaeStringTooLarge();
    }

    /**
     * {@return the shortest distance beginning at the provided {@code start}
     *  to the encountering of a zero short in the provided {@code segment}}
     * <p>
     * Note: The inspected region must be short aligned.
     *
     * @see #chunkedStrlenByte(MemorySegment, long) for more information
     *
     * @param segment to examine
     * @param start   from where examination shall begin
     * @throws IllegalArgumentException if the examined region contains no zero shorts
     *                                  within a length that can be accepted by a String
     */
    public static int chunkedStrlenShort(MemorySegment segment, long start) {

        // Handle the first unaligned "head" bytes separately
        int headCount = (int)SharedUtils.remainsToAlignment(segment.address() + start, Long.BYTES);

        int offset = 0;
        for (; offset < headCount; offset += Short.BYTES) {
            short curr = segment.get(JAVA_SHORT, start + offset);
            if (curr == 0) {
                return offset;
            }
        }

        // We are now on a long-aligned boundary so this is the "body"
        int bodyCount = bodyCount(segment.byteSize() - start - headCount);

        for (; offset < bodyCount; offset += Long.BYTES) {
            // We know we are `long` aligned so, we can save on alignment checking here
            long curr = segment.get(JAVA_LONG_UNALIGNED, start + offset);
            // Is this a candidate?
            if (mightContainZeroShort(curr)) {
                for (int j = 0; j < Long.BYTES; j += Short.BYTES) {
                    if (segment.get(JAVA_SHORT_UNALIGNED, start + offset + j) == 0) {
                        return offset + j;
                    }
                }
            }
        }

        // Handle the "tail"
        return requireWithinArraySize((long) offset + strlenShort(segment, start + offset));
    }

    private static int strlenShort(MemorySegment segment, long start) {
        for (int offset = 0; offset < ArraysSupport.SOFT_MAX_ARRAY_LENGTH; offset += Short.BYTES) {
            short curr = segment.get(JAVA_SHORT_UNALIGNED, start + offset);
            if (curr == (short)0) {
                return offset;
            }
        }
        throw newIaeStringTooLarge();
    }

    // The gain of using `long` wide operations for `int` is lower than for the two other `byte` and `short` variants
    // so, there is only one method for ints.
    public static int strlenInt(MemorySegment segment, long start) {
        for (int offset = 0; offset < ArraysSupport.SOFT_MAX_ARRAY_LENGTH; offset += Integer.BYTES) {
            // We are guaranteed to be aligned here so, we can use unaligned access.
            int curr = segment.get(JAVA_INT_UNALIGNED, start + offset);
            if (curr == 0) {
                return offset;
            }
        }
        throw newIaeStringTooLarge();
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
            // Comparing the charset to specific internal implementations avoids loading the class `StandardCharsets`
            if        (charset == sun.nio.cs.UTF_8.INSTANCE ||
                       charset == sun.nio.cs.ISO_8859_1.INSTANCE ||
                       charset == sun.nio.cs.US_ASCII.INSTANCE) {
                return SINGLE_BYTE;
            } else if (charset instanceof sun.nio.cs.UTF_16LE ||
                       charset instanceof sun.nio.cs.UTF_16BE ||
                       charset instanceof sun.nio.cs.UTF_16) {
                return DOUBLE_BYTE;
            } else if (charset instanceof sun.nio.cs.UTF_32LE ||
                       charset instanceof sun.nio.cs.UTF_32BE ||
                       charset instanceof sun.nio.cs.UTF_32) {
                return QUAD_BYTE;
            } else {
                throw new IllegalArgumentException("Unsupported charset: " + charset);
            }
        }
    }

    public static boolean bytesCompatible(String string, Charset charset) {
        return JAVA_LANG_ACCESS.bytesCompatible(string, charset);
    }

    public static int copyBytes(String string, MemorySegment segment, Charset charset, long offset) {
        if (bytesCompatible(string, charset)) {
            copyToSegmentRaw(string, segment, offset);
            return string.length();
        } else {
            byte[] bytes = string.getBytes(charset);
            MemorySegment.copy(bytes, 0, segment, JAVA_BYTE, offset, bytes.length);
            return bytes.length;
        }
    }

    public static void copyToSegmentRaw(String string, MemorySegment segment, long offset) {
        JAVA_LANG_ACCESS.copyToSegmentRaw(string, segment, offset);
    }

    private static IllegalArgumentException newIaeStringTooLarge() {
        return new IllegalArgumentException("String too large");
    }

}
