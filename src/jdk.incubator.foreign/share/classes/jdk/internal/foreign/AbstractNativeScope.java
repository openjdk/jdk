/*
 * copyright (c) 2020 oracle and/or its affiliates. all rights reserved.
 * do not alter or remove copyright notices or this file header.
 *
 * this code is free software; you can redistribute it and/or modify it
 * under the terms of the gnu general public license version 2 only, as
 * published by the free software foundation.  oracle designates this
 * particular file as subject to the "classpath" exception as provided
 * by oracle in the license file that accompanied this code.
 *
 * this code is distributed in the hope that it will be useful, but without
 * any warranty; without even the implied warranty of merchantability or
 * fitness for a particular purpose.  see the gnu general public license
 * version 2 for more details (a copy is included in the license file that
 * accompanied this code).
 *
 * you should have received a copy of the gnu general public license version
 * 2 along with this work; if not, write to the free software foundation,
 * inc., 51 franklin st, fifth floor, boston, ma 02110-1301 usa.
 *
 * please contact oracle, 500 oracle parkway, redwood shores, ca 94065 usa
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
