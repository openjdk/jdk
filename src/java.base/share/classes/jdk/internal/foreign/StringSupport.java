/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.util.Architecture;
import jdk.internal.util.ArraysSupport;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.MemorySegment;
import java.nio.charset.Charset;

import static java.lang.foreign.ValueLayout.*;

/**
 * Miscellaneous functions to read and write strings, in various charsets.
 */
public final class StringSupport {

    private static final JavaLangAccess JAVA_LANG_ACCESS = SharedSecrets.getJavaLangAccess();
    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();
    private static final long LONG_MASK = ~7L; // The last three bits are zero

    private StringSupport() {}

    @ForceInline
    public static String read(AbstractMemorySegmentImpl segment, long offset, Charset charset) {
        return switch (CharsetKind.of(charset)) {
            case SINGLE_BYTE -> readByte(segment, offset, charset);
            case DOUBLE_BYTE -> readShort(segment, offset, charset);
            case QUAD_BYTE -> readInt(segment, offset, charset);
        };
    }

    @ForceInline
    public static void write(AbstractMemorySegmentImpl segment, long offset, Charset charset, String string) {
        switch (CharsetKind.of(charset)) {
            case SINGLE_BYTE -> writeByte(segment, offset, charset, string);
            case DOUBLE_BYTE -> writeShort(segment, offset, charset, string);
            case QUAD_BYTE -> writeInt(segment, offset, charset, string);
        }
    }

    @ForceInline
    private static String readByte(AbstractMemorySegmentImpl segment, long offset, Charset charset) {
        final int len = strlenByte(segment, offset, segment.byteSize());
        final byte[] bytes = new byte[len];
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, len);
        return new String(bytes, charset);
    }

    @ForceInline
    private static void writeByte(AbstractMemorySegmentImpl segment, long offset, Charset charset, String string) {
        int bytes = copyBytes(string, segment, charset, offset);
        segment.set(JAVA_BYTE, offset + bytes, (byte)0);
    }

    @ForceInline
    private static String readShort(AbstractMemorySegmentImpl segment, long offset, Charset charset) {
        int len = strlenShort(segment, offset, segment.byteSize());
        byte[] bytes = new byte[len];
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, len);
        return new String(bytes, charset);
    }

    @ForceInline
    private static void writeShort(AbstractMemorySegmentImpl segment, long offset, Charset charset, String string) {
        int bytes = copyBytes(string, segment, charset, offset);
        segment.set(JAVA_SHORT_UNALIGNED, offset + bytes, (short)0);
    }

    @ForceInline
    private static String readInt(AbstractMemorySegmentImpl segment, long offset, Charset charset) {
        int len = strlenInt(segment, offset, segment.byteSize());
        byte[] bytes = new byte[len];
        MemorySegment.copy(segment, JAVA_BYTE, offset, bytes, 0, len);
        return new String(bytes, charset);
    }

    @ForceInline
    private static void writeInt(AbstractMemorySegmentImpl segment, long offset, Charset charset, String string) {
        int bytes = copyBytes(string, segment, charset, offset);
        segment.set(JAVA_INT_UNALIGNED, offset + bytes, 0);
    }

    /**
     * {@return the index of the first zero byte beginning at the provided
     *          {@code fromOffset} to the encountering of a zero byte in the provided
     *          {@code segment} checking bytes before the {@code toOffset}}
     * <p>
     * The method is using a heuristic method to determine if a long word contains a
     * zero byte. The method might have false positives but never false negatives.
     * <p>
     * This method is inspired by the `glibc/string/strlen.c` implementation
     *
     * @param segment    to examine
     * @param fromOffset from where examination shall begin (inclusive)
     * @param toOffset   to where examination shall end (exclusive)
     * @throws IllegalArgumentException if the examined region contains no zero bytes
     *                                  within a length that can be accepted by a String
     */
    @ForceInline
    public static int strlenByte(final AbstractMemorySegmentImpl segment,
                                 final long fromOffset,
                                 final long toOffset) {
        final long length = toOffset - fromOffset;
        segment.checkBounds(fromOffset, length);
        if (length == 0) {
            // The state has to be checked explicitly for zero-length segments
            segment.scope.checkValidState();
            throw nullNotFound(segment, fromOffset, toOffset);
        }
        final long longBytes = length & LONG_MASK;
        final long longLimit = fromOffset + longBytes;
        long offset = fromOffset;
        for (; offset < longLimit; offset += Long.BYTES) {
            long val = SCOPED_MEMORY_ACCESS.getLongUnaligned(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + offset, !Architecture.isLittleEndian());
            if (mightContainZeroByte(val)) {
                for (int j = 0; j < Long.BYTES; j++) {
                    if (SCOPED_MEMORY_ACCESS.getByte(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + offset + j) == 0) {
                        return requireWithinStringSize(offset + j - fromOffset, segment, fromOffset, toOffset);
                    }
                }
            }
        }
        // Handle the tail
        for (; offset < toOffset; offset++) {
            byte val = SCOPED_MEMORY_ACCESS.getByte(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + offset);
            if (val == 0) {
                return requireWithinStringSize(offset - fromOffset, segment, fromOffset, toOffset);
            }
        }
        throw nullNotFound(segment, fromOffset, toOffset);
    }

    @ForceInline
    public static int strlenShort(final AbstractMemorySegmentImpl segment,
                                  final long fromOffset,
                                  final long toOffset) {
        final long length = toOffset - fromOffset;
        segment.checkBounds(fromOffset, length);
        if (length == 0) {
            segment.scope.checkValidState();
            throw nullNotFound(segment, fromOffset, toOffset);
        }
        final long longBytes = length & LONG_MASK;
        final long longLimit = fromOffset + longBytes;
        long offset = fromOffset;
        for (; offset < longLimit; offset += Long.BYTES) {
            long val = SCOPED_MEMORY_ACCESS.getLongUnaligned(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + offset, !Architecture.isLittleEndian());
            if (mightContainZeroShort(val)) {
                for (int j = 0; j < Long.BYTES; j += Short.BYTES) {
                    if (SCOPED_MEMORY_ACCESS.getShortUnaligned(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + offset + j, !Architecture.isLittleEndian()) == 0) {
                        return requireWithinStringSize(offset + j - fromOffset, segment, fromOffset, toOffset);
                    }
                }
            }
        }
        // Handle the tail
        // Prevent over scanning as we step by 2
        final long endScan = toOffset & ~1; // The last bit is zero
        for (; offset < endScan; offset += Short.BYTES) {
            short val = SCOPED_MEMORY_ACCESS.getShortUnaligned(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + offset, !Architecture.isLittleEndian());
            if (val == 0) {
                return requireWithinStringSize(offset - fromOffset, segment, fromOffset, toOffset);
            }
        }
        throw nullNotFound(segment, fromOffset, toOffset);
    }

    @ForceInline
    public static int strlenInt(final AbstractMemorySegmentImpl segment,
                                final long fromOffset,
                                final long toOffset) {
        final long length = toOffset - fromOffset;
        segment.checkBounds(fromOffset, length);
        if (length == 0) {
            segment.scope.checkValidState();
            throw nullNotFound(segment, fromOffset, toOffset);
        }
        final long longBytes = length & LONG_MASK;
        final long longLimit = fromOffset + longBytes;
        long offset = fromOffset;
        for (; offset < longLimit; offset += Long.BYTES) {
            long val = SCOPED_MEMORY_ACCESS.getLongUnaligned(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + offset, !Architecture.isLittleEndian());
            if (mightContainZeroInt(val)) {
                for (int j = 0; j < Long.BYTES; j += Integer.BYTES) {
                    if (SCOPED_MEMORY_ACCESS.getIntUnaligned(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + offset + j, !Architecture.isLittleEndian()) == 0) {
                        return requireWithinStringSize(offset + j - fromOffset, segment, fromOffset, toOffset);
                    }
                }
            }
        }
        // Handle the tail
        // Prevent over scanning as we step by 4
        final long endScan = toOffset & ~3; // The last two bit are zero
        for (; offset < endScan; offset += Integer.BYTES) {
            int val = SCOPED_MEMORY_ACCESS.getIntUnaligned(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + offset, !Architecture.isLittleEndian());
            if (val == 0) {
                return requireWithinStringSize(offset - fromOffset, segment, fromOffset, toOffset);
            }
        }
        throw nullNotFound(segment, fromOffset, toOffset);
    }

    /*
    Bits 63 and N * 8 (N = 1..7) of this number are zero.  Call these bits
    the "holes".  Note that there is a hole just to the left of
    each byte, with an extra at the end:

    bits:  01111110 11111110 11111110 11111110 11111110 11111110 11111110 11111111
    bytes: AAAAAAAA BBBBBBBB CCCCCCCC DDDDDDDD EEEEEEEE FFFFFFFF GGGGGGGG HHHHHHHH

    The 1-bits make sure that carries propagate to the next 0-bit.
    The 0-bits provide holes for carries to fall into.
    */
    private static final long HIMAGIC_FOR_BYTES = 0x8080_8080_8080_8080L;
    private static final long LOMAGIC_FOR_BYTES = 0x0101_0101_0101_0101L;

    private static boolean mightContainZeroByte(long l) {
        return ((l - LOMAGIC_FOR_BYTES) & (~l) & HIMAGIC_FOR_BYTES) != 0;
    }

    private static final long HIMAGIC_FOR_SHORTS = 0x8000_8000_8000_8000L;
    private static final long LOMAGIC_FOR_SHORTS = 0x0001_0001_0001_0001L;

    static boolean mightContainZeroShort(long l) {
        return ((l - LOMAGIC_FOR_SHORTS) & (~l) & HIMAGIC_FOR_SHORTS) != 0;
    }

    private static final long HIMAGIC_FOR_INTS = 0x8000_0000_8000_0000L;
    private static final long LOMAGIC_FOR_INTS = 0x0000_0001_0000_0001L;

    static boolean mightContainZeroInt(long l) {
        return ((l - LOMAGIC_FOR_INTS) & (~l) & HIMAGIC_FOR_INTS) != 0;
    }


    private static int requireWithinStringSize(long size,
                                               AbstractMemorySegmentImpl segment,
                                               long fromOffset,
                                               long toOffset) {
        if (size > ArraysSupport.SOFT_MAX_ARRAY_LENGTH) {
            throw stringTooLarge(segment, fromOffset, toOffset);
        }
        return (int) size;
    }

    private static IllegalArgumentException stringTooLarge(AbstractMemorySegmentImpl segment,
                                                           long fromOffset,
                                                           long toOffset) {
        return new IllegalArgumentException("String too large: " + exceptionInfo(segment, fromOffset, toOffset));
    }

    private static IndexOutOfBoundsException nullNotFound(AbstractMemorySegmentImpl segment,
                                                          long fromOffset,
                                                          long toOffset) {
        return new IndexOutOfBoundsException("No null terminator found: " + exceptionInfo(segment, fromOffset, toOffset));
    }

    private static String exceptionInfo(AbstractMemorySegmentImpl segment,
                                        long fromOffset,
                                        long toOffset) {
        return segment + " using region [" + fromOffset + ", " + toOffset + ")";
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
}
