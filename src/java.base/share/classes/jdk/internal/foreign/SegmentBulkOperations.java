/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

    // All the threshold values below MUST be a power of two and should preferably be
    // greater or equal to 2^3.

    // Update the FILL value for Aarch64 once 8338975 is fixed.
    private static final int NATIVE_THRESHOLD_FILL = powerOfPropertyOr("fill", Architecture.isAARCH64() ? 10 : 5);
    private static final int NATIVE_THRESHOLD_MISMATCH = powerOfPropertyOr("mismatch", 6);
    private static final int NATIVE_THRESHOLD_COPY = powerOfPropertyOr("copy", 6);

    @ForceInline
    public static MemorySegment fill(AbstractMemorySegmentImpl dst, byte value) {
        dst.checkReadOnly(false);
        if (dst.length == 0) {
            // Implicit state check
            dst.checkValidState();
        } else if (dst.length < NATIVE_THRESHOLD_FILL) {
            // 0 <= length < FILL_NATIVE_LIMIT : 0...0X...XXXX

            // Handle smaller segments directly without transitioning to native code
            final long u = Byte.toUnsignedLong(value);
            final long longValue = u << 56 | u << 48 | u << 40 | u << 32 | u << 24 | u << 16 | u << 8 | u;

            int offset = 0;
            // 0...0X...X000
            final int limit = (int) (dst.length & (NATIVE_THRESHOLD_FILL - 8));
            for (; offset < limit; offset += 8) {
                SCOPED_MEMORY_ACCESS.putLongUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + offset, longValue, !Architecture.isLittleEndian());
            }
            int remaining = (int) dst.length - limit;
            // 0...0X00
            if (remaining >= 4) {
                SCOPED_MEMORY_ACCESS.putIntUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + offset, (int) longValue, !Architecture.isLittleEndian());
                offset += 4;
                remaining -= 4;
            }
            // 0...00X0
            if (remaining >= 2) {
                SCOPED_MEMORY_ACCESS.putShortUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + offset, (short) longValue, !Architecture.isLittleEndian());
                offset += 2;
                remaining -= 2;
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
            final int limit = (int) (size & (NATIVE_THRESHOLD_COPY - 8));
            int offset = 0;
            for (; offset < limit; offset += 8) {
                final long v = SCOPED_MEMORY_ACCESS.getLongUnaligned(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcOffset + offset, !Architecture.isLittleEndian());
                SCOPED_MEMORY_ACCESS.putLongUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstOffset + offset, v, !Architecture.isLittleEndian());
            }
            int remaining = (int) size - offset;
            // 0...0X00
            if (remaining >= 4) {
                final int v = SCOPED_MEMORY_ACCESS.getIntUnaligned(src.sessionImpl(), src.unsafeGetBase(),src.unsafeGetOffset() + srcOffset + offset, !Architecture.isLittleEndian());
                SCOPED_MEMORY_ACCESS.putIntUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstOffset + offset, v, !Architecture.isLittleEndian());
                offset += 4;
                remaining -= 4;
            }
            // 0...00X0
            if (remaining >= 2) {
                final short v = SCOPED_MEMORY_ACCESS.getShortUnaligned(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcOffset + offset, !Architecture.isLittleEndian());
                SCOPED_MEMORY_ACCESS.putShortUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstOffset + offset, v, !Architecture.isLittleEndian());
                offset += 2;
                remaining -=2;
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
        for (; offset < limit; offset += 8) {
            final long s = SCOPED_MEMORY_ACCESS.getLongUnaligned(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset + offset, false);
            final long d = SCOPED_MEMORY_ACCESS.getLongUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset + offset, false);
            if (s != d) {
                return start + offset + mismatch(s, d);
            }
        }
        int remaining = length - offset;

        // 0...0X00
        if (remaining >= 4) {
            final int s = SCOPED_MEMORY_ACCESS.getIntUnaligned(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset + offset, false);
            final int d = SCOPED_MEMORY_ACCESS.getIntUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset + offset, false);
            if (s != d) {
                return start + offset + mismatch(s, d);
            }
            offset += 4;
            remaining -= 4;
        }
        // 0...00X0
        if (remaining >= 2) {
            final short s = SCOPED_MEMORY_ACCESS.getShortUnaligned(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset + offset, false);
            final short d = SCOPED_MEMORY_ACCESS.getShortUnaligned(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset + offset, false);
            if (s != d) {
                return start + offset + mismatch(s, d);
            }
            offset += 2;
            remaining -= 2;
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
