/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.foreign.MemorySession;
import java.lang.foreign.SegmentAllocator;

public final class ArenaAllocator implements SegmentAllocator {

    public static final long DEFAULT_BLOCK_SIZE = 4 * 1024;

    private MemorySegment segment;

    private long sp = 0L;
    private long size = 0;
    private final long blockSize;
    private final long arenaSize;
    private final MemorySession session;

    public ArenaAllocator(long blockSize, long arenaSize, MemorySession session) {
        this.blockSize = blockSize;
        this.arenaSize = arenaSize;
        this.session = session;
        this.segment = newSegment(blockSize, 1);
    }

    MemorySegment trySlice(long byteSize, long byteAlignment) {
        long min = segment.address();
        long start = Utils.alignUp(min + sp, byteAlignment) - min;
        if (segment.byteSize() - start < byteSize) {
            return null;
        } else {
            MemorySegment slice = segment.asSlice(start, byteSize);
            sp = start + byteSize;
            return slice;
        }
    }

    private MemorySegment newSegment(long byteSize, long byteAlignment) {
        long allocatedSize = Utils.alignUp(byteSize, byteAlignment);
        if (size + allocatedSize > arenaSize) {
            throw new OutOfMemoryError();
        }
        size += allocatedSize;
        return session.allocate(byteSize, byteAlignment);
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
        MemorySessionImpl.toSessionImpl(session).checkValidState();
        // try to slice from current segment first...
        MemorySegment slice = trySlice(byteSize, byteAlignment);
        if (slice != null) {
            return slice;
        } else {
            long maxPossibleAllocationSize = byteSize + byteAlignment - 1;
            if (maxPossibleAllocationSize < 0) {
                throw new OutOfMemoryError();
            } else if (maxPossibleAllocationSize > blockSize) {
                // too big
                return newSegment(byteSize, byteAlignment);
            } else {
                // allocate a new segment and slice from there
                sp = 0L;
                segment = newSegment(blockSize, 1L);
                slice = trySlice(byteSize, byteAlignment);
                return slice;
            }
        }
    }
}
