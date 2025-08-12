/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.util.Architecture;
import jdk.internal.util.ArraysSupport;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.MemorySegment;

/**
 * This class contains optimized bulk operation methods that operate on one or several
 * memory segments.
 * <p>
 * Generally, the methods attempt to work with as-large-as-possible units of memory at
 * a time.
 * <p>
 * It should be noted that when invoking scoped memory access get/set operations, it
 * is imperative from a performance perspective to convey the sharp types from the
 * call site in order for the compiler to pick the correct Unsafe access variant.
 */
public final class SegmentBulkOperations {

    private SegmentBulkOperations() {}

    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();
    private static final long LONG_MASK = ~7L; // The last three bits are zero

    // All the threshold values below MUST be a power of two and should preferably be
    // greater or equal to 2^3.
    private static final int NATIVE_THRESHOLD_FILL = powerOfPropertyOr("fill", 5);
    private static final int NATIVE_THRESHOLD_MISMATCH = powerOfPropertyOr("mismatch", 6);
    private static final int NATIVE_THRESHOLD_COPY = powerOfPropertyOr("copy", 6);

    @ForceInline
    public static MemorySegment fill(AbstractMemorySegmentImpl dst, byte value) {
        dst.checkReadOnly(false);
        if (dst.length == 0) {
            // Implicit state check
            dst.sessionImpl().checkValidState();
        } else if (dst.length < NATIVE_THRESHOLD_FILL) {
            // 0 <= length < FILL_NATIVE_LIMIT : 0...0X...XXXX

            // Handle smaller segments directly without transitioning to native code
            final long u = Byte.toUnsignedLong(value);
            final long longValue = u << 56 | u << 48 | u << 40 | u << 32 | u << 24 | u << 16 | u << 8 | u;

            int offset = 0;
            // 0...0X...X000
            final int limit = (int) (dst.length & (NATIVE_THRESHOLD_FILL - 8));
            for (; offset < limit; offset += Long.BYTES) {
                SCOPED_MEMORY_ACCESS.putLongUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + offset, longValue, !Architecture.isLittleEndian());
            }
            int remaining = (int) dst.length - limit;
            // 0...0X00
            if (remaining >= Integer.BYTES) {
                SCOPED_MEMORY_ACCESS.putIntUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + offset, (int) longValue, !Architecture.isLittleEndian());
                offset += Integer.BYTES;
                remaining -= Integer.BYTES;
            }
            // 0...00X0
            if (remaining >= Short.BYTES) {
                SCOPED_MEMORY_ACCESS.putShortUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + offset, (short) longValue, !Architecture.isLittleEndian());
                offset += Short.BYTES;
                remaining -= Short.BYTES;
            }
            // 0...000X
            if (remaining == 1) {
                SCOPED_MEMORY_ACCESS.putByte(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + offset, value);
            }
            // We have now fully handled 0...0X...XXXX
        } else {
            // Handle larger segments via native calls
            SCOPED_MEMORY_ACCESS.setMemory(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset(), dst.length, value);
        }
        return dst;
    }

    @ForceInline
    public static void copy(AbstractMemorySegmentImpl src, long srcOffset,
                            AbstractMemorySegmentImpl dst, long dstOffset,
                            long size) {

        Utils.checkNonNegativeIndex(size, "size");
        // Implicit null check for src and dst
        src.checkAccess(srcOffset, size, true);
        dst.checkAccess(dstOffset, size, false);

        if (size <= 0) {
            // Do nothing
        } else if (size < NATIVE_THRESHOLD_COPY && !src.overlaps(dst)) {
            // 0 < size < FILL_NATIVE_LIMIT : 0...0X...XXXX
            //
            // Strictly, we could check for !src.asSlice(srcOffset, size).overlaps(dst.asSlice(dstOffset, size) but
            // this is a bit slower and it likely very unusual there is any difference in the outcome. Also, if there
            // is an overlap, we could tolerate one particular direction of overlap (but not the other).

            // 0...0X...X000
            final int limit = (int) (size & (NATIVE_THRESHOLD_COPY - Long.BYTES));
            int offset = 0;
            for (; offset < limit; offset += Long.BYTES) {
                final long v = SCOPED_MEMORY_ACCESS.getLongUnaligned(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcOffset + offset, !Architecture.isLittleEndian());
                SCOPED_MEMORY_ACCESS.putLongUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstOffset + offset, v, !Architecture.isLittleEndian());
            }
            int remaining = (int) size - offset;
            // 0...0X00
            if (remaining >= Integer.BYTES) {
                final int v = SCOPED_MEMORY_ACCESS.getIntUnaligned(src.sessionImpl(), src.unsafeGetBase(),src.unsafeGetOffset() + srcOffset + offset, !Architecture.isLittleEndian());
                SCOPED_MEMORY_ACCESS.putIntUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstOffset + offset, v, !Architecture.isLittleEndian());
                offset += Integer.BYTES;
                remaining -= Integer.BYTES;
            }
            // 0...00X0
            if (remaining >= Short.BYTES) {
                final short v = SCOPED_MEMORY_ACCESS.getShortUnaligned(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcOffset + offset, !Architecture.isLittleEndian());
                SCOPED_MEMORY_ACCESS.putShortUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstOffset + offset, v, !Architecture.isLittleEndian());
                offset += Short.BYTES;
                remaining -= Short.BYTES;
            }
            // 0...000X
            if (remaining == 1) {
                final byte v = SCOPED_MEMORY_ACCESS.getByte(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcOffset + offset);
                SCOPED_MEMORY_ACCESS.putByte(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstOffset + offset, v);
            }
            // We have now fully handled 0...0X...XXXX
        } else {
            // For larger sizes, the transition to native code pays off
            SCOPED_MEMORY_ACCESS.copyMemory(src.sessionImpl(), dst.sessionImpl(),
                    src.unsafeGetBase(), src.unsafeGetOffset() + srcOffset,
                    dst.unsafeGetBase(), dst.unsafeGetOffset() + dstOffset, size);
        }
    }

    private static final @Stable int[] POWERS_OF_31 = new int[]{
            0x0000001f, 0x000003c1, 0x0000745f, 0x000e1781,
            0x01b4d89f, 0x34e63b41, 0x67e12cdf, 0x94446f01};

    /**
     * {@return a 32-bit hash value calculated from the content in the provided
     *          {@code segment} between the provided offsets}
     * <p>
     * The method is implemented as a 32-bit polynomial hash function equivalent to:
     * {@snippet lang=java :
     *     final long length = toOffset - fromOffset;
     *     segment.checkBounds(fromOffset, length);
     *     int result = 1;
     *     for (long i = fromOffset; i < toOffset; i++) {
     *         result = 31 * result + segment.get(JAVA_BYTE, i);
     *     }
     *     return result;
     * }
     * but is potentially more performant.
     *
     * @param segment    from which a content hash should be computed
     * @param fromOffset starting offset (inclusive) in the segment
     * @param toOffset   ending offset (non-inclusive) in the segment
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code srcSegment.isAccessibleBy(T) == false}
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with {@code segment} is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws IndexOutOfBoundsException if either {@code fromOffset} or {@code toOffset}
     *                                   are {@code > segment.byteSize}
     * @throws IndexOutOfBoundsException if either {@code fromOffset} or {@code toOffset}
     *                                   are {@code < 0}
     * @throws IndexOutOfBoundsException if {@code toOffset - fromOffset} is {@code < 0}
     */
    @ForceInline
    public static int contentHash(AbstractMemorySegmentImpl segment, long fromOffset, long toOffset) {
        final long length = toOffset - fromOffset;
        segment.checkBounds(fromOffset, length);
        if (length == 0) {
            // The state has to be checked explicitly for zero-length segments
            segment.scope.checkValidState();
            return 1;
        }
        int result = 1;
        final long longBytes = length & LONG_MASK;
        final long limit = fromOffset + longBytes;
        for (; fromOffset < limit; fromOffset += Long.BYTES) {
            long val = SCOPED_MEMORY_ACCESS.getLongUnaligned(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + fromOffset, !Architecture.isLittleEndian());
            result = result * POWERS_OF_31[7]
                    + ((byte) (val >>> 56)) * POWERS_OF_31[6]
                    + ((byte) (val >>> 48)) * POWERS_OF_31[5]
                    + ((byte) (val >>> 40)) * POWERS_OF_31[4]
                    + ((byte) (val >>> 32)) * POWERS_OF_31[3]
                    + ((byte) (val >>> 24)) * POWERS_OF_31[2]
                    + ((byte) (val >>> 16)) * POWERS_OF_31[1]
                    + ((byte) (val >>> 8)) * POWERS_OF_31[0]
                    + ((byte) val);
        }
        int remaining = (int) (length - longBytes);
        // 0...0X00
        if (remaining >= Integer.BYTES) {
            int val = SCOPED_MEMORY_ACCESS.getIntUnaligned(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + fromOffset, !Architecture.isLittleEndian());
            result = result * POWERS_OF_31[3]
                    + ((byte) (val >>> 24)) * POWERS_OF_31[2]
                    + ((byte) (val >>> 16)) * POWERS_OF_31[1]
                    + ((byte) (val >>> 8)) * POWERS_OF_31[0]
                    + ((byte) val);
            fromOffset += Integer.BYTES;
            remaining -= Integer.BYTES;
        }
        // 0...00X0
        if (remaining >= Short.BYTES) {
            short val = SCOPED_MEMORY_ACCESS.getShortUnaligned(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + fromOffset, !Architecture.isLittleEndian());
            result = result * POWERS_OF_31[1]
                    + ((byte) (val >>> 8)) * POWERS_OF_31[0]
                    + ((byte) val);
            fromOffset += Short.BYTES;
            remaining -= Short.BYTES;
        }
        // 0...000X
        if (remaining == 1) {
            byte val = SCOPED_MEMORY_ACCESS.getByte(segment.sessionImpl(), segment.unsafeGetBase(), segment.unsafeGetOffset() + fromOffset);
            result = result * POWERS_OF_31[0]
                    + val;
        }
        return result;
    }

    @ForceInline
    public static long mismatch(AbstractMemorySegmentImpl src, long srcFromOffset, long srcToOffset,
                                AbstractMemorySegmentImpl dst, long dstFromOffset, long dstToOffset) {
        final long srcBytes = srcToOffset - srcFromOffset;
        final long dstBytes = dstToOffset - dstFromOffset;
        src.checkAccess(srcFromOffset, srcBytes, true);
        dst.checkAccess(dstFromOffset, dstBytes, true);

        final long length = Math.min(srcBytes, dstBytes);
        final boolean srcAndDstBytesDiffer = srcBytes != dstBytes;

        if (length == 0) {
            return srcAndDstBytesDiffer ? 0 : -1;
        } else if (length < NATIVE_THRESHOLD_MISMATCH) {
            return mismatch(src, srcFromOffset, dst, dstFromOffset, 0, (int) length, srcAndDstBytesDiffer);
        } else {
            long i;
            if (SCOPED_MEMORY_ACCESS.getByte(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset) !=
                    SCOPED_MEMORY_ACCESS.getByte(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset)) {
                return 0;
            }
            i = vectorizedMismatchLargeForBytes(src.sessionImpl(), dst.sessionImpl(),
                    src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset,
                    dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset,
                    length);
            if (i >= 0) {
                return i;
            }
            final long remaining = ~i;
            assert remaining < 8 : "remaining greater than 7: " + remaining;
            i = length - remaining;
            return mismatch(src, srcFromOffset + i, dst, dstFromOffset + i, i, (int) remaining, srcAndDstBytesDiffer);
        }
    }

    // Mismatch is handled in chunks of 64 (unroll of eight 8s), 8, 4, 2, and 1 byte(s).
    @ForceInline
    private static long mismatch(AbstractMemorySegmentImpl src, long srcFromOffset,
                                 AbstractMemorySegmentImpl dst, long dstFromOffset,
                                 long start, int length, boolean srcAndDstBytesDiffer) {
        int offset = 0;
        final int limit = length & (NATIVE_THRESHOLD_MISMATCH - 8);
        for (; offset < limit; offset += Long.BYTES) {
            final long s = SCOPED_MEMORY_ACCESS.getLongUnaligned(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset + offset, false);
            final long d = SCOPED_MEMORY_ACCESS.getLongUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset + offset, false);
            if (s != d) {
                return start + offset + mismatch(s, d);
            }
        }
        int remaining = length - offset;

        // 0...0X00
        if (remaining >= Integer.BYTES) {
            final int s = SCOPED_MEMORY_ACCESS.getIntUnaligned(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset + offset, false);
            final int d = SCOPED_MEMORY_ACCESS.getIntUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset + offset, false);
            if (s != d) {
                return start + offset + mismatch(s, d);
            }
            offset += Integer.BYTES;
            remaining -= Integer.BYTES;
        }
        // 0...00X0
        if (remaining >= Short.BYTES) {
            final short s = SCOPED_MEMORY_ACCESS.getShortUnaligned(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset + offset, false);
            final short d = SCOPED_MEMORY_ACCESS.getShortUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset + offset, false);
            if (s != d) {
                return start + offset + mismatch(s, d);
            }
            offset += Short.BYTES;
            remaining -= Short.BYTES;
        }
        // 0...000X
        if (remaining == 1) {
            final byte s = SCOPED_MEMORY_ACCESS.getByte(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset + offset);
            final byte d = SCOPED_MEMORY_ACCESS.getByte(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset + offset);
            if (s != d) {
                return start + offset;
            }
        }
        return srcAndDstBytesDiffer ? (start + length) : -1;
        // We have now fully handled 0...0X...XXXX
    }

    @ForceInline
    private static int mismatch(long first, long second) {
        final long x = first ^ second;
        return Long.numberOfTrailingZeros(x) / 8;
    }

    @ForceInline
    private static int mismatch(int first, int second) {
        final int x = first ^ second;
        return Integer.numberOfTrailingZeros(x) / 8;
    }

    @ForceInline
    private static int mismatch(short first, short second) {
        return ((0xff & first) == (0xff & second)) ? 1 : 0;
    }

    /**
     * Mismatch over long lengths.
     */
    private static long vectorizedMismatchLargeForBytes(MemorySessionImpl aSession, MemorySessionImpl bSession,
                                                        Object a, long aOffset,
                                                        Object b, long bOffset,
                                                        long length) {
        long off = 0;
        long remaining = length;
        int i, size;
        boolean lastSubRange = false;
        while (remaining > 7 && !lastSubRange) {
            if (remaining > Integer.MAX_VALUE) {
                size = Integer.MAX_VALUE;
            } else {
                size = (int) remaining;
                lastSubRange = true;
            }
            i = SCOPED_MEMORY_ACCESS.vectorizedMismatch(aSession, bSession,
                    a, aOffset + off,
                    b, bOffset + off,
                    size, ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE);
            if (i >= 0)
                return off + i;

            i = size - ~i;
            off += i;
            remaining -= i;
        }
        return ~remaining;
    }

    static final String PROPERTY_PATH = "java.lang.foreign.native.threshold.power.";

    // The returned value is in the interval [0, 2^30]
    static int powerOfPropertyOr(String name, int defaultPower) {
        final int power = Integer.getInteger(PROPERTY_PATH + name, defaultPower);
        return 1 << Math.clamp(power, 0, Integer.SIZE - 2);
    }

}
