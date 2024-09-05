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
import jdk.internal.vm.annotation.ForceInline;

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

    // MISMATCH_NATIVE_THRESHOLD must be a power of two and should be greater than 2^3
    private static final long MISMATCH_NATIVE_THRESHOLD = 1 << 20;

    @ForceInline
    public static long mismatch(AbstractMemorySegmentImpl src, long srcFromOffset, long srcToOffset,
                                AbstractMemorySegmentImpl dst, long dstFromOffset, long dstToOffset) {
        final long srcBytes = srcToOffset - srcFromOffset;
        final long dstBytes = dstToOffset - dstFromOffset;
        src.checkAccess(srcFromOffset, srcBytes, true);
        dst.checkAccess(dstFromOffset, dstBytes, true);

        final long bytes = Math.min(srcBytes, dstBytes);
        final boolean srcAndDstBytesDiffer = srcBytes != dstBytes;

        if (bytes == 0) {
            return srcAndDstBytesDiffer ? 0 : -1;
        } else if (bytes < MISMATCH_NATIVE_THRESHOLD) {
            final int limit = (int) (bytes & (MISMATCH_NATIVE_THRESHOLD - 8));
            int offset = 0;
            for (; offset < limit; offset += 8) {
                if (SCOPED_MEMORY_ACCESS.getLong(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset + offset) !=
                        SCOPED_MEMORY_ACCESS.getLong(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset + offset)) {
                    return mismatchSmall(src, srcFromOffset + offset, dst, dstFromOffset + offset, offset, 8, srcAndDstBytesDiffer);
                }
            }
            int remaining = (int) bytes - offset;
            // 0...0X00
            if (remaining >= 4) {
                if (SCOPED_MEMORY_ACCESS.getInt(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset + offset) !=
                        SCOPED_MEMORY_ACCESS.getInt(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset + offset)) {
                    return mismatchSmall(src, srcFromOffset + offset, dst, dstFromOffset + offset, offset, 4, srcAndDstBytesDiffer);
                }
                offset += 4;
                remaining -= 4;
            }
            // 0...00X0
            if (remaining >= 2) {
                if (SCOPED_MEMORY_ACCESS.getShort(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset + offset) !=
                        SCOPED_MEMORY_ACCESS.getShort(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset + offset)) {
                    return mismatchSmall(src, srcFromOffset + offset, dst, dstFromOffset + offset, offset, 2, srcAndDstBytesDiffer);
                }
                offset += 2;
                remaining -= 2;
            }
            // 0...000X
            if (remaining == 1) {
                if (SCOPED_MEMORY_ACCESS.getByte(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset + offset) !=
                        SCOPED_MEMORY_ACCESS.getByte(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset + offset)) {
                    return offset;
                }
            }
            return srcAndDstBytesDiffer ? bytes : -1;
            // We have now fully handled 0...0X...XXXX
        } else {
            long i;
            if (SCOPED_MEMORY_ACCESS.getByte(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset) !=
                    SCOPED_MEMORY_ACCESS.getByte(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset)) {
                return 0;
            }
            i = AbstractMemorySegmentImpl.vectorizedMismatchLargeForBytes(src.sessionImpl(), dst.sessionImpl(),
                    src.unsafeGetBase(), src.unsafeGetOffset() + srcFromOffset,
                    dst.unsafeGetBase(), dst.unsafeGetOffset() + dstFromOffset,
                    bytes);
            if (i >= 0) {
                return i;
            }
            final long remaining = ~i;
            assert remaining < 8 : "remaining greater than 7: " + remaining;
            i = bytes - remaining;
            return mismatchSmall(src, srcFromOffset + i, dst, dstFromOffset + i, i, (int) remaining, srcAndDstBytesDiffer);
        }
    }

    // This method is intended for 0 <= bytes < 7
    @ForceInline
    private static long mismatchSmall(AbstractMemorySegmentImpl src, long srcOffset,
                                      AbstractMemorySegmentImpl dst, long dstOffset,
                                      long offset, int bytes, boolean srcAndDstBytesDiffer) {
        for (int i = 0; i < bytes; i++) {
            if (SCOPED_MEMORY_ACCESS.getByte(src.sessionImpl(), src.unsafeGetBase(), src.unsafeGetOffset() + srcOffset + i) !=
                    SCOPED_MEMORY_ACCESS.getByte(dst.sessionImpl(), dst.unsafeGetBase(), dst.unsafeGetOffset() + dstOffset + i)) {
                return offset + i;
            }
        }
        return srcAndDstBytesDiffer ? bytes : -1;
    }

}
