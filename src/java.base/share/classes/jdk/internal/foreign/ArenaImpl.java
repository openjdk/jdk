/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment.Scope;

public sealed class ArenaImpl implements Arena {

    final MemorySessionImpl session;
    private final boolean shouldReserve;

    ArenaImpl(MemorySessionImpl session) {
        this.session = session;
        this.shouldReserve = session instanceof ImplicitSession;
    }

    @Override
    public final Scope scope() {
        return session;
    }

    @Override
    public void close() {
        session.close();
    }

    public final NativeMemorySegmentImpl allocateNoInit(long byteSize, long byteAlignment) {
        return allocateLowLevel(byteSize, byteAlignment, false);
    }

    @Override
    public final NativeMemorySegmentImpl allocate(long byteSize, long byteAlignment) {
        return allocateLowLevel(byteSize, byteAlignment, true);
    }

    NativeMemorySegmentImpl allocateLowLevel(long byteSize, long byteAlignment, boolean init) {
        return SegmentFactories.allocateNativeSegment(byteSize, byteAlignment, session, shouldReserve, init);
    }

    static final class OfConfined extends ArenaImpl {

        private static final long POOL_SIZE = ConfinedSegmentPool.pooledMemorySize();

        @Stable
        private long pool;
        private long poolSp;

        OfConfined(ConfinedSession session) {
            super(session);
        }

        @Override
        public void close() {
            session.justClose();
            // The session cleanup at the end of this method may throw, so we
            // need to release the acquired pooled memory first.
            if (pool > 0) {
                ConfinedSegmentPool.release(session.owner, poolSp);
            }
            session.resourceList.cleanup();
        }

        @Override
        @ForceInline
        NativeMemorySegmentImpl allocateLowLevel(long byteSize, long byteAlignment, boolean init) {
            if (byteSize <= POOL_SIZE) {
                Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
                session.checkValidState();
                long pool = this.pool;
                if (pool == 0) {
                    pool = ConfinedSegmentPool.acquire(session.owner);
                    if (pool > 0) {
                        this.pool = pool;
                    }
                }
                // Preserve the invariant that zero-sized segments have unique addresses
                // for any given Arena
                final long allocationByteSize = Math.max(1, byteSize);
                final long address;
                if (pool > 0 && (address = trySlice(pool, allocationByteSize, byteAlignment)) != 0) {
                    return SegmentFactories.makeNativeSegmentUnchecked(address, byteSize, session);
                }
            }
            return super.allocateLowLevel(byteSize, byteAlignment, init);
        }

        private long trySlice(long pool, long byteSize, long byteAlignment) {
            final long start = Utils.alignUp(pool + poolSp, byteAlignment) - pool;
            if (start + byteSize <= POOL_SIZE) {
                // The backing memory is zeroed on initial allocation and on each pool release.
                poolSp = start + byteSize;
                return pool + start;
            }
            return 0;
        }

    }

}
