/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.foreign.SegmentAllocator;

public final class SlicingAllocator implements SegmentAllocator {

    private final MemorySegment segment;

    private long sp = 0L;

    public SlicingAllocator(MemorySegment segment) {
        this.segment = segment;
    }

    public long currentOffset() {
        return sp;
    }

    public void resetTo(long offset) {
        if (offset < 0 || offset > sp)
            throw new IllegalArgumentException(String.format("offset %d should be in [0, %d] ", offset, sp));
        this.sp = offset;
    }

    public boolean canAllocate(long byteSize, long byteAlignment) {
        long min = segment.address();
        long start = Utils.alignUp(min + sp, byteAlignment) - min;
        return start + byteSize <= segment.byteSize();
    }

    MemorySegment trySlice(long byteSize, long byteAlignment) {
        long min = segment.address();
        long start = Utils.alignUp(min + sp, byteAlignment) - min;
        MemorySegment slice = segment.asSlice(start, byteSize, byteAlignment);
        sp = start + byteSize;
        return slice;
    }

    @Override
    public MemorySegment allocate(long byteSize, long byteAlignment) {
        Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
        // try to slice from current segment first...
        return trySlice(byteSize, byteAlignment);
    }
}
