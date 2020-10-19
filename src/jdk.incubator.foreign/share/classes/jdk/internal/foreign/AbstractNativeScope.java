/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
import jdk.incubator.foreign.NativeScope;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

public abstract class AbstractNativeScope implements NativeScope {

    private final List<MemorySegment> segments = new ArrayList<>();
    private final Thread ownerThread;

    private static final int SCOPE_MASK = MemorySegment.READ | MemorySegment.WRITE; // no terminal operations allowed

    AbstractNativeScope(Thread ownerThread) {
        this.ownerThread = ownerThread;
    }

    @Override
    public Thread ownerThread() {
        return ownerThread;
    }

    @Override
    public void close() {
        segments.forEach(MemorySegment::close);
    }

    void checkOwnerThread() {
        if (Thread.currentThread() != ownerThread()) {
            throw new IllegalStateException("Attempt to access scope from different thread");
        }
    }

    MemorySegment newSegment(long size, long align) {
        MemorySegment segment = MemorySegment.allocateNative(size, align);
        segments.add(segment);
        return segment;
    }

    MemorySegment newSegment(long size) {
        return newSegment(size, size);
    }

    public void register(MemorySegment segment) {
        segments.add(segment);
    }

    public static class UnboundedNativeScope extends AbstractNativeScope {

        private static final long BLOCK_SIZE = 4 * 1024;
        private static final long MAX_ALLOC_SIZE = BLOCK_SIZE / 2;

        private MemorySegment segment;
        private long sp = 0L;
        private long size = 0L;

        @Override
        public OptionalLong byteSize() {
            return OptionalLong.empty();
        }

        @Override
        public long allocatedBytes() {
            return size;
        }

        public UnboundedNativeScope() {
            super(Thread.currentThread());
            this.segment = newSegment(BLOCK_SIZE);
        }

        @Override
        public MemorySegment allocate(long bytesSize, long bytesAlignment) {
            checkOwnerThread();
            if (bytesSize > MAX_ALLOC_SIZE) {
                MemorySegment segment = newSegment(bytesSize, bytesAlignment);
                return segment.withAccessModes(SCOPE_MASK);
            }
            for (int i = 0; i < 2; i++) {
                long min = segment.address().toRawLongValue();
                long start = Utils.alignUp(min + sp, bytesAlignment) - min;
                try {
                    MemorySegment slice = segment.asSlice(start, bytesSize)
                            .withAccessModes(SCOPE_MASK);
                    sp = start + bytesSize;
                    size += Utils.alignUp(bytesSize, bytesAlignment);
                    return slice;
                } catch (IndexOutOfBoundsException ex) {
                    sp = 0L;
                    segment = newSegment(BLOCK_SIZE, 1L);
                }
            }
            throw new AssertionError("Cannot get here!");
        }
    }

    public static class BoundedNativeScope extends AbstractNativeScope {
        private final MemorySegment segment;
        private long sp = 0L;

        @Override
        public OptionalLong byteSize() {
            return OptionalLong.of(segment.byteSize());
        }

        @Override
        public long allocatedBytes() {
            return sp;
        }

        public BoundedNativeScope(long size) {
            super(Thread.currentThread());
            this.segment = newSegment(size, 1);
        }

        @Override
        public MemorySegment allocate(long bytesSize, long bytesAlignment) {
            checkOwnerThread();
            long min = segment.address().toRawLongValue();
            long start = Utils.alignUp(min + sp, bytesAlignment) - min;
            try {
                MemorySegment slice = segment.asSlice(start, bytesSize)
                        .withAccessModes(SCOPE_MASK);
                sp = start + bytesSize;
                return slice;
            } catch (IndexOutOfBoundsException ex) {
                throw new OutOfMemoryError("Not enough space left to allocate");
            }
        }
    }
}
