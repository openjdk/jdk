/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

// This class is not thread safe and does not need to be as it, by definition, is only
// operated on using a distict thread. However, the close action can be run by
// another thread.
final class ThreadConfinedSegmentPool implements AutoCloseable {

    private static final String PROPERTY_PATH = "java.lang.foreign.native.confined.arena.";

    // We are using a `long` for keeping track of slot used so, we can only handle
    // a maximum of 64 slots.
    private static final int POOL_SLOTS =
            Math.min(64, Integer.getInteger(PROPERTY_PATH + "pool-slots", 2));
    private static final long POOL_SLOT_ALIGNMENT =
            SegmentBulkOperations.powerOfPropertyOr(PROPERTY_PATH + "power.pool-slot-alignment", 4);
    private static final long POOL_SLOT_SIZE = SegmentBulkOperations.powerOfPropertyOr(PROPERTY_PATH + "power.pool-slot-size", 6);

    private final ArenaImpl backingArena;
    @Stable
    private final SlicingAllocator[] allocators;
    // An optimized bit set in the form of a long
    // 0 means acquired, 1 means free
    // Set all available pool slots to 1
    private long allocatorSet = POOL_SLOTS == 64
            ? -1L
            : (1L << POOL_SLOTS) - 1;

    private ThreadConfinedSegmentPool(Thread thread) {
        final ArenaImpl backingArena = MemorySessionImpl.createConfined(thread).asArena();
        this.backingArena = backingArena;
        final SlicingAllocator[] allocators = new SlicingAllocator[POOL_SLOTS];
        this.allocators = allocators;
        super();
    }

    static ThreadConfinedSegmentPool of(Thread thread) {
        if (VM.isDirectMemoryPageAligned() || POOL_SLOTS <= 0 || POOL_SLOT_SIZE <= 1) {
            return null;
        }
        try {
            return new ThreadConfinedSegmentPool(thread);
        } catch (OutOfMemoryError _) {
            return null;
        }
    }

    @ForceInline
    Arena acquire(Thread owner) {
        return new CachedArena(owner);
    }

    @ForceInline
    // Returns the acquired allocator index if successful, otherwise returns 64
    private int tryAcquireAllocatorIndex() {
        final int allocatorIndex = Long.numberOfTrailingZeros(allocatorSet);
        allocatorSet &= ~(1L << allocatorIndex); // 1 -> 0
        return allocatorIndex;
    }

    @ForceInline
    private void releaseAllocatorIndex(int allocatorIndex) {
        allocatorSet |= 1L << allocatorIndex; // 0 -> 1
    }

    @ForceInline
    private SlicingAllocator allocator(int allocatorIndex) {
        SlicingAllocator allocator = allocators[allocatorIndex];
        // Lazily constuct allocators
        if (allocator == null) {
            // We do not have to zero out the backing segment as this is handled
            // on demand and later by the CachedArena.
            allocators[allocatorIndex] = allocator = new SlicingAllocator(
                    backingArena.allocateNoInit(POOL_SLOT_SIZE, POOL_SLOT_ALIGNMENT));
        }
        return allocator;
    }

    // This method might be called by another thread during thread cleanup.
    @Override
    public void close() {
        ((ConfinedSession) backingArena.scope()).closeFromThreadCleanup();
    }

    final class CachedArena implements Arena, NoInitAllocator {

        private final MemorySessionImpl session;
        @Stable
        private int allocatorIndex;
        @Stable
        private SlicingAllocator allocator;
        private boolean allocatorReleased;

        @ForceInline
        CachedArena(Thread owner) {
            this.session = MemorySessionImpl.createConfined(owner);
            super();
        }

        @ForceInline
        @Override
        public NativeMemorySegmentImpl allocate(long byteSize, long byteAlignment) {
            return allocate0(byteSize, byteAlignment, true);
        }

        @Override
        public MemorySegment.Scope scope() {
            return session;
        }

        @ForceInline
        @Override
        public NativeMemorySegmentImpl allocateNoInit(long byteSize, long byteAlignment) {
            return allocate0(byteSize, byteAlignment, false);
        }

        @ForceInline
        private NativeMemorySegmentImpl allocate0(long byteSize, long byteAlignment, boolean init) {
            // We need these checks here upfront as the following methods have side effects:
            //  - tryAcquireAllocator()
            //  - `allocator.allocate()`
            //  - `segment.fill()`, and
            Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
            session.checkValidState();

            final SlicingAllocator allocator;
            if (
                // No use even trying if `byteSize` is larger than the pool size.
                // This also prevents/delays pool allocation if larger chunks are
                // initially allocated from this arena.
                    byteSize > POOL_SLOT_SIZE
                            // Preserve distinct addresses for zero-length arenas by
                            // falling back to the regular allocator
                            || byteSize == 0
                            // Did we get an allocator?
                            || (allocator = tryAcquireAllocator()) == null
                            // If so, can we accomodate the request with that allocator?
                            || !allocator.canAllocate(byteSize, byteAlignment)) {
                            // Fall back to the non-pooled code path
                return SegmentFactories.allocateNativeSegment(byteSize, byteAlignment, session, false, init);
            }

            // The backing segment in the `allocator` is guaranteed to be zeroed out after
            // each recycle so, we do not have to do this explicitly.
            final NativeMemorySegmentImpl segment = (NativeMemorySegmentImpl) allocator.allocate(byteSize, byteAlignment);
            // Reinterpret the slice to use this arena's scope.
            return SegmentFactories.makeNativeSegmentUnchecked(segment.address(), byteSize, session);
        }

        @ForceInline
        private SlicingAllocator tryAcquireAllocator() {
            SlicingAllocator allocator = this.allocator;
            if (allocator == null) {
                final int allocatorIndex = tryAcquireAllocatorIndex();
                if (allocatorIndex >= Long.SIZE) {
                    return null;
                }
                this.allocatorIndex = allocatorIndex;
                this.allocator = allocator = allocator(allocatorIndex);
            }
            return allocator;
        }

        @ForceInline
        @Override
        public void close() {
            if (session.isAlive()) {
                // To minimize data exposure, we zero out _after_ use rather than
                // before use.
                allocator.zeroOutToOffset();
            }
            // The Arena::close method is called first as it checks thread
            // confinement and liveness before cached chunks are made available again.
            try {
                session.close();
            } finally {
                // This covers the case if a cleanup action in super.close() throws.
                // In such cases, the session is not alive and we need to release the
                // allocator.
                if (!session.isAlive()) {
                    releaseAllocator();
                }
            }
        }

        @ForceInline
        private void releaseAllocator() {
            if (allocator != null) {
                // Make this method idempotent
                if (!allocatorReleased) {
                    // Reset the allocator allowing future reuse
                    allocator.resetTo(0);
                    releaseAllocatorIndex(allocatorIndex);
                    allocatorReleased = true;
                }
            }
        }
    }
}
