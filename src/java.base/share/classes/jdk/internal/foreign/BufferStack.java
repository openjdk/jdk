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

package jdk.internal.foreign;

import jdk.internal.misc.CarrierThreadLocal;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.ref.Reference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * A buffer stack that allows efficient reuse of memory segments. This is useful in cases
 * where temporary memory is needed.
 * <p>
 * Use the factories {@code BufferStack.of(...)} to create new instances of this class.
 * <p>
 * Note: The reused segments are neither zeroed out before nor after re-use.
 */
public final class BufferStack {

    private final long byteSize;
    private final long byteAlignment;
    private final CarrierThreadLocal<PerThread> tl;

    private BufferStack(long byteSize, long byteAlignment) {
        this.byteSize = byteSize;
        this.byteAlignment = byteAlignment;
        this.tl = new CarrierThreadLocal<>() {
            @Override
            protected BufferStack.PerThread initialValue() {
                return BufferStack.PerThread.of(byteSize, byteAlignment);
            }
        };
    }

    /**
     * {@return a new Arena that tries to provide {@code byteSize} and {@code byteAlignment}
     *          allocations by recycling the BufferStack's internal memory}
     *
     * @param byteSize      to be reserved from this BufferStack's internal memory
     * @param byteAlignment to be used for reservation
     */
    @ForceInline
    public Arena pushFrame(long byteSize, long byteAlignment) {
        return tl.get().pushFrame(byteSize, byteAlignment);
    }

    /**
     * {@return a new Arena that tries to provide {@code byteSize}
     *          allocations by recycling the BufferStack's internal memory}
     *
     * @param byteSize      to be reserved from this BufferStack's internal memory
     */
    @ForceInline
    public Arena pushFrame(long byteSize) {
        return pushFrame(byteSize, 1);
    }

    /**
     * {@return a new Arena that tries to provide {@code layout}
     *          allocations by recycling the BufferStack's internal memory}
     *
     * @param layout for which to reserve internal memory
     */
    @ForceInline
    public Arena pushFrame(MemoryLayout layout) {
        return pushFrame(layout.byteSize(), layout.byteAlignment());
    }

    @Override
    public String toString() {
        return "BufferStack[byteSize=" + byteSize + ", byteAlignment=" + byteAlignment + "]";
    }

    private record PerThread(ReentrantLock lock,
                             Arena arena,
                             SlicingAllocator stack,
                             CleanupAction cleanupAction) {

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

        static PerThread of(long byteSize, long byteAlignment) {
            final Arena arena = Arena.ofAuto();
            return new PerThread(new ReentrantLock(),
                    arena,
                    new SlicingAllocator(arena.allocate(byteSize, byteAlignment)),
                    new CleanupAction(arena));
        }

        private record CleanupAction(Arena arena) implements Consumer<MemorySegment> {
            @Override
            public void accept(MemorySegment memorySegment) {
                Reference.reachabilityFence(arena);
            }
        }

        private final class Frame implements Arena {

            private final boolean locked;
            private final long parentOffset;
            private final long topOfStack;
            private final Arena confinedArena;
            private final SegmentAllocator frame;

            @SuppressWarnings("restricted")
            @ForceInline
            public Frame(boolean locked, long byteSize, long byteAlignment) {
                this.locked = locked;
                this.parentOffset = stack.currentOffset();
                final MemorySegment frameSegment = stack.allocate(byteSize, byteAlignment);
                this.topOfStack = stack.currentOffset();
                this.confinedArena = Arena.ofConfined();
                // The cleanup action will keep the original automatic `arena` (from which
                // the reusable segment is first allocated) alive even if this Frame
                // becomes unreachable but there are reachable segments still alive.
                this.frame = new SlicingAllocator(frameSegment.reinterpret(confinedArena, cleanupAction));
            }

            @ForceInline
            private void assertOrder() {
                if (topOfStack != stack.currentOffset())
                    throw new IllegalStateException("Out of order access: frame not top-of-stack");
            }

            @ForceInline
            @Override
            @SuppressWarnings("restricted")
            public MemorySegment allocate(long byteSize, long byteAlignment) {
                // Make sure we are on the right thread and not closed
                MemorySessionImpl.toMemorySession(confinedArena).checkValidState();
                return frame.allocate(byteSize, byteAlignment);
            }

            @ForceInline
            @Override
            public MemorySegment.Scope scope() {
                return confinedArena.scope();
            }

            @ForceInline
            @Override
            public void close() {
                assertOrder();
                // the Arena::close method is called "early" as it checks thread
                // confinement and crucially before any mutation of the internal
                // state takes place.
                confinedArena.close();
                stack.resetTo(parentOffset);
                if (locked) {
                    lock.unlock();
                }
            }
        }
    }

    public static BufferStack of(long byteSize, long byteAlignment) {
        if (byteSize < 0) {
            throw new IllegalArgumentException("Negative byteSize: " + byteSize);
        }
        if (byteAlignment < 0) {
            throw new IllegalArgumentException("Negative byteAlignment: " + byteAlignment);
        }
        return new BufferStack(byteSize, byteAlignment);
    }

    public static BufferStack of(long byteSize) {
        return new BufferStack(byteSize, 1);
    }

    public static BufferStack of(MemoryLayout layout) {
        // Implicit null check
        return of(layout.byteSize(), layout.byteAlignment());
    }
}
