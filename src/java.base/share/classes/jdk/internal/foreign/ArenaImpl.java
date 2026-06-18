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

    @ForceInline
    public final NativeMemorySegmentImpl allocateNoInit(long byteSize, long byteAlignment) {
        return allocateLowLevel(byteSize, byteAlignment, false);
    }

    @Override
    @ForceInline
    public final NativeMemorySegmentImpl allocate(long byteSize, long byteAlignment) {
        return allocateLowLevel(byteSize, byteAlignment, true);
    }

    @ForceInline
    NativeMemorySegmentImpl allocateLowLevel(long byteSize, long byteAlignment, boolean init) {
        return SegmentFactories.allocateNativeSegment(byteSize, byteAlignment, session, shouldReserve, init);
    }

    static final class OfConfined extends ArenaImpl {

        private static final class ConfinedPoolHolder {
            private static final long POOL_SIZE = ConfinedSegmentPool.pooledMemorySize();
        }

        @Stable
        private long pool;
        private long poolSp;

        OfConfined(ConfinedSession session) {
            super(session);
        }

        @Override
        public void close() {
            session.justClose();
            if (pool > 0) {
                ConfinedSegmentPool.release(session.owner, poolSp);
            }
            session.resourceList.cleanup();
        }

        @Override
        @ForceInline
        NativeMemorySegmentImpl allocateLowLevel(long byteSize, long byteAlignment, boolean init) {
            final long poolSize = ConfinedPoolHolder.POOL_SIZE;
            if (byteSize <= poolSize) {
                Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
                session.checkValidState();
                long pool = this.pool;
                if (pool == 0) {
                    pool = ConfinedSegmentPool.acquire(session.owner);
                    if (pool > 0) {
                        this.pool = pool;
                    }
                }
                final boolean zeroLength = byteSize == 0;
                final long allocationByteSize = Math.max(1, byteSize);
                NativeMemorySegmentImpl segment;
                if (pool > 0 && (segment = trySlice(pool, allocationByteSize, byteAlignment, poolSize)) != null) {
                    // Preserve the invariant that zero-sized segments have unique addresses
                    // for any given Arena
                    return zeroLength
                            ? (NativeMemorySegmentImpl) segment.asSlice(0, 0)
                            : segment;
                }
            }
            return super.allocateLowLevel(byteSize, byteAlignment, init);
        }

        @ForceInline
        private NativeMemorySegmentImpl trySlice(long pool, long byteSize, long byteAlignment, long poolSize) {
            final long start = Utils.alignUp(pool + poolSp, byteAlignment) - pool;
            if (start + byteSize <= poolSize) {
                // The backing memory is zeroed on initial allocation and on each pool release.
                final NativeMemorySegmentImpl slice = SegmentFactories.makeNativeSegmentUnchecked(pool + start, byteSize, session);
                poolSp = start + byteSize;
                return slice;
            }
            return null;
        }

    }

    static ArenaImpl of(MemorySessionImpl session) {
        return session instanceof ConfinedSession confinedSession
                ? new OfConfined(confinedSession)
                : new ArenaImpl(session);
    }

}
