/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.foreign.abi;

import jdk.internal.foreign.SlicingAllocator;
import jdk.internal.misc.CarrierThreadLocal;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.concurrent.locks.ReentrantLock;

public class BufferStack {
    private final long size;

    public BufferStack(long size) {
        this.size = size;
    }

    private final ThreadLocal<PerThread> tl = new CarrierThreadLocal<>() {
        @Override
        protected PerThread initialValue() {
            return new PerThread(size);
        }
    };

    @ForceInline
    public Arena pushFrame(long size, long byteAlignment) {
        return tl.get().pushFrame(size, byteAlignment);
    }

    private static final class PerThread {
        private final ReentrantLock lock = new ReentrantLock();
        private final SlicingAllocator stack;

        public PerThread(long size) {
            this.stack = new SlicingAllocator(Arena.ofAuto().allocate(size));
        }

        @ForceInline
        public Arena pushFrame(long size, long byteAlignment) {
            boolean needsLock = Thread.currentThread().isVirtual() && !lock.isHeldByCurrentThread();
            if (needsLock && !lock.tryLock()) {
                // Rare: another virtual thread on the same carrier competed for acquisition.
                return Arena.ofConfined();
            }
            if (!stack.canAllocate(size, byteAlignment)) {
                if (needsLock) lock.unlock();
                return Arena.ofConfined();
            }

            return new Frame(needsLock, size, byteAlignment);
        }

        private class Frame implements Arena {
            private final boolean locked;
            private final long parentOffset;
            private final long topOfStack;
            private final Arena scope = Arena.ofConfined();
            private final SegmentAllocator frame;

            @SuppressWarnings("restricted")
            public Frame(boolean locked, long byteSize, long byteAlignment) {
                this.locked = locked;

                parentOffset = stack.currentOffset();
                MemorySegment frameSegment = stack.allocate(byteSize, byteAlignment);
                topOfStack = stack.currentOffset();
                frame = new SlicingAllocator(frameSegment.reinterpret(scope, null));
            }

            private void assertOrder() {
                if (topOfStack != stack.currentOffset())
                    throw new IllegalStateException("Out of order access: frame not top-of-stack");
            }

            @Override
            @SuppressWarnings("restricted")
            public MemorySegment allocate(long byteSize, long byteAlignment) {
                return frame.allocate(byteSize, byteAlignment);
            }

            @Override
            public MemorySegment.Scope scope() {
                return scope.scope();
            }

            @Override
            public void close() {
                assertOrder();
                scope.close();
                stack.resetTo(parentOffset);
                if (locked) {
                    lock.unlock();
                }
            }
        }
    }
}