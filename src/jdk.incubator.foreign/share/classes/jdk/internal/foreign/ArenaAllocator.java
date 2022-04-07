/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.SegmentAllocator;
import jdk.incubator.foreign.ResourceScope;

public final class ArenaAllocator implements SegmentAllocator {

    public static final long DEFAULT_BLOCK_SIZE = 4 * 1024;

    private MemorySegment segment;

    private long sp = 0L;
    private long size = 0;
    private final long blockSize;
    private final long arenaSize;
    private final ResourceScope scope;

    public ArenaAllocator(long blockSize, long arenaSize, ResourceScope scope) {
        this.blockSize = blockSize;
        this.arenaSize = arenaSize;
        this.scope = scope;
        this.segment = newSegment(blockSize, 1);
    }

    MemorySegment trySlice(long bytesSize, long bytesAlignment) {
        long min = segment.address().toRawLongValue();
        long start = Utils.alignUp(min + sp, bytesAlignment) - min;
        if (segment.byteSize() - start < bytesSize) {
            return null;
        } else {
            MemorySegment slice = segment.asSlice(start, bytesSize);
            sp = start + bytesSize;
            return slice;
        }
    }

    public ResourceScope scope() {
        return scope;
    }

    private MemorySegment newSegment(long bytesSize, long bytesAlignment) {
        long allocatedSize = Utils.alignUp(bytesSize, bytesAlignment);
        if (size + allocatedSize > arenaSize) {
            throw new OutOfMemoryError();
        }
        size += allocatedSize;
        return MemorySegment.allocateNative(bytesSize, bytesAlignment, scope);
    }

    @Override
    public MemorySegment allocate(long bytesSize, long bytesAlignment) {
        // try to slice from current segment first...
        MemorySegment slice = trySlice(bytesSize, bytesAlignment);
        if (slice != null) {
            return slice;
        } else {
            long maxPossibleAllocationSize = bytesSize + bytesAlignment - 1;
            if (maxPossibleAllocationSize > blockSize) {
                // too big
                return newSegment(bytesSize, bytesAlignment);
            } else {
                // allocate a new segment and slice from there
                sp = 0L;
                segment = newSegment(blockSize, 1L);
                slice = trySlice(bytesSize, bytesAlignment);
                return slice;
            }
        }
    }
}
