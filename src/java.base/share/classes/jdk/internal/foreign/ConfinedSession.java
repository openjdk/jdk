/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.invoke.MhUtil;
import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.AOTSafeClassInitializer;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.annotation.Stable;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * A confined session, which features an owner thread. The liveness check features an additional
 * confinement check - that is, calling any operation on this session from a thread other than the
 * owner thread will result in an exception. Because of this restriction, checking the liveness bit
 * can be performed in plain mode.
 */
final class ConfinedSession extends MemorySessionImpl {

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final long POOL_SIZE = JLA.pooledMemorySize();

    private int asyncReleaseCount = 0;
    @Stable
    private long min;
    private long sp;

    static final VarHandle ASYNC_RELEASE_COUNT= MhUtil.findVarHandle(MethodHandles.lookup(), "asyncReleaseCount", int.class);

    public ConfinedSession(Thread owner) {
        super(owner, new ConfinedResourceList());
    }

    @Override
    @ForceInline
    public void acquire0() {
        checkValidState();
        if (acquireCount == MAX_FORKS) {
            throw tooManyAcquires();
        }
        acquireCount++;
    }

    @Override
    @ForceInline
    public void release0() {
        if (Thread.currentThread() == owner) {
            acquireCount--;
        } else {
            // It is possible to end up here in two cases: this session was kept alive by some other confined session
            // which is implicitly released (in which case the release call comes from the cleaner thread). Or,
            // this session might be kept alive by a shared session, which means the release call can come from any
            // thread.
            ASYNC_RELEASE_COUNT.getAndAdd(this, 1);
        }
    }

    void justClose() {
        checkValidState();
        int asyncCount = (int)ASYNC_RELEASE_COUNT.getVolatile(this);
        int acquire = acquireCount - asyncCount;
        if (acquire == 0) {
            state = CLOSED;
            cleanupPool();
        } else {
            throw alreadyAcquired(acquire);
        }
    }

    @Override
    @ForceInline
    NativeMemorySegmentImpl allocateLowLevel(long byteSize, long byteAlignment, boolean init) {
        if (byteSize <= POOL_SIZE) {
            Utils.checkAllocationSizeAndAlign(byteSize, byteAlignment);
            checkValidState();
            long min = this.min;
            if (min == 0) {
                min = JLA.acquirePooledMemory(owner);
                if (min > 0) {
                    this.min = min;
                }
            }
            final boolean zeroLength = byteSize == 0;
            final long allocationByteSize = Math.max(1, byteSize);
            NativeMemorySegmentImpl segment;
            if (min > 0 && (segment = trySlice(min, allocationByteSize, byteAlignment)) != null) {
                // Preserve the invariant that zero-sized segments have unique addresses
                // for any given Arena
                return zeroLength
                        ? (NativeMemorySegmentImpl) segment.asSlice(0, 0)
                        : segment;
            }
        }
        // Fall back to normal allocation
        return SegmentFactories.allocateNativeSegment(byteSize, byteAlignment, this, false, init);
    }

    @ForceInline
    private NativeMemorySegmentImpl trySlice(long min, long byteSize, long byteAlignment) {
        final long start = Utils.alignUp(min + sp, byteAlignment) - min;
        if (start + byteSize <= POOL_SIZE) {
            // We know the backing memory is zeroed out since the initial slab of memory
            // is cleared and upon each recycle we zero out upon closing the arena.
            final NativeMemorySegmentImpl slice = SegmentFactories.makeNativeSegmentUnchecked(min + start, byteSize, this);
            sp = start + byteSize;
            return slice;
        }
        return null;
    }

    @ForceInline
    private void cleanupPool() {
        if (min > 0) {
            JLA.releaseAndZeroOutPooledMemory(owner, sp);
        }
    }

    /**
     * A confined resource list; no races are possible here.
     */
    static final class ConfinedResourceList extends ResourceList {
        // The first element of the list is pulled into a separate field
        // which helps escape analysis keep track of the instance, allowing
        // it to be scalar replaced.
        ResourceCleanup cache;

        @Override
        void add(ResourceCleanup cleanup) {
            if (fst != ResourceCleanup.CLOSED_LIST) {
                if (cache == null) {
                    cache = cleanup;
                } else {
                    cleanup.next = fst;
                    fst = cleanup;
                }
            } else {
                throw alreadyClosed();
            }
        }

        @Override
        void cleanup() {
            if (fst != ResourceCleanup.CLOSED_LIST) {
                ResourceCleanup prev = fst;
                fst = ResourceCleanup.CLOSED_LIST;
                RuntimeException pendingException = null;
                if (cache != null) {
                    pendingException = cleanupSingle(cache, pendingException);
                }
                cleanup(prev, pendingException);
            } else {
                throw alreadyClosed();
            }
        }
    }
}
