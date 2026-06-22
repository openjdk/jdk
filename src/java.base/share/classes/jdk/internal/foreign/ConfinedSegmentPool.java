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

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.Unsafe;
import jdk.internal.misc.VM;
import jdk.internal.vm.annotation.ForceInline;

/**
 * Native memory pool used by confined sessions.
 * <p>
 * Platform threads lazily allocate one native-memory pool per thread. Virtual
 * threads use a fixed number of shared native-memory slots, where a virtual
 * thread maps to a candidate slot from its thread id and acquires the slot with
 * a CAS operation. A normal release zeroes the used memory and releases the
 * slot, making it available to other virtual threads. If the slot is already
 * acquired, allocation falls back to the regular native allocator.
 * <p>
 * For performance reasons, this class operates directly on native memory and
 * pointers via Unsafe.
 */
public final class ConfinedSegmentPool {

    private ConfinedSegmentPool() { }

    private static final Unsafe U = Unsafe.getUnsafe();

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    // Providing "0" as a value for this property disables confined pooling
    private static final String POOLED_MEMORY_PROPERTY = "java.lang.foreign.native.confined.pool.power.size";

    // The following values can be observed {-1 (disabled), 8, 16, 32 or 64} bytes
    private static final long POOLED_MEMORY_SIZE = clampedPowerOfPropertyOr(POOLED_MEMORY_PROPERTY, 6);

    private static volatile boolean virtualPoolInitialized;

    /**
     * Returns the size of the native memory pool.
     */
    public static long pooledMemorySize() {
        return POOLED_MEMORY_SIZE;
    }

    /**
     * Returns the pooled memory owned by the given thread, or zero if the
     * thread does not own pooled memory.
     */
    public static long currentPool(Thread thread) {
        if (POOLED_MEMORY_SIZE <= 0) {
            return 0;
        }
        return thread.isVirtual()
                ? (virtualPoolInitialized ? VirtualThreadPool.currentPool(thread) : 0)
                : JLA.getConfinedMemoryPool(thread);
    }

    /**
     * Returns a pointer to pooled memory owned by the given thread, or zero if
     * pooled memory cannot be acquired.
     */
    @ForceInline
    static long acquire(Thread thread) {
        if (POOLED_MEMORY_SIZE <= 0) {
            return 0;
        }
        return thread.isVirtual()
                ? acquireVirtual(thread)
                : acquirePlatform(thread);
    }

    /**
     * Zeros out and releases pooled memory owned by the given thread.
     */
    @ForceInline
    public static void release(Thread thread, long size) {
        if (thread.isVirtual()) {
            releaseVirtual(thread, size);
        } else {
            releasePlatform(thread, size);
        }
    }

    @ForceInline
    static void releaseAcquired(Thread thread, long size) {
        if (thread.isVirtual()) {
            releaseAcquiredVirtual(thread, size);
        } else {
            releasePlatform(thread, size);
        }
    }

    /**
     * Releases any pool still associated with a terminating thread.
     */
    public static void releaseOnThreadExit(Thread thread) {
        if (thread.isVirtual()) {
            if (virtualPoolInitialized) {
                VirtualThreadPool.releaseIfOwned(thread, POOLED_MEMORY_SIZE);
            }
        } else {
            final JavaLangAccess jla = JLA;
            final long pool = jla.getConfinedMemoryPool(thread);
            if (pool != 0) {
                U.freeMemory(Math.abs(pool));
                jla.setConfinedMemoryPool(thread, 0);
            }
        }
    }

    @ForceInline
    private static long acquirePlatform(Thread thread) {
        final JavaLangAccess jla = JLA;
        final long pool = jla.getConfinedMemoryPool(thread);
        if (pool > 0) {
            jla.setConfinedMemoryPool(thread, -pool);
            return pool;
        } else if (pool == 0) {
            return allocateAndAcquirePlatformPool(thread);
        }
        return 0;
    }

    private static long allocateAndAcquirePlatformPool(Thread thread) {
        final long address;
        try {
            address = U.allocateMemory(POOLED_MEMORY_SIZE);
            if (address < 0) {
                throw new InternalError("Allocated memory pool is negative contrary to" +
                        " the non-negative pointer invariant: 0x" + Long.toHexString(address));
            }
        } catch (OutOfMemoryError _) {
            return 0;
        }
        U.setMemory(address, POOLED_MEMORY_SIZE, (byte) 0);
        JLA.setConfinedMemoryPool(thread, -address);
        return address;
    }

    @ForceInline
    private static void releasePlatform(Thread thread, long size) {
        final JavaLangAccess jla = JLA;
        final long pool = -jla.getConfinedMemoryPool(thread);
        if (pool <= 0) {
            throw cannotReleasePooledMemory(thread);
        }
        zeroOutMemory(pool, size);
        jla.setConfinedMemoryPool(thread, pool);
    }

    @ForceInline
    private static long acquireVirtual(Thread thread) {
        return VirtualThreadPool.acquire(thread);
    }

    @ForceInline
    private static void releaseVirtual(Thread thread, long size) {
        if (!virtualPoolInitialized || !VirtualThreadPool.release(thread, size)) {
            throw cannotReleasePooledMemory(thread);
        }
    }

    @ForceInline
    private static void releaseAcquiredVirtual(Thread thread, long size) {
        if (!VirtualThreadPool.release(thread, size)) {
            throw cannotReleasePooledMemory(thread);
        }
    }

    private static IllegalStateException cannotReleasePooledMemory(Thread thread) {
        return new IllegalStateException("Cannot release pooled memory: " + currentPool(thread));
    }

    @SuppressWarnings("fallthrough")
    @ForceInline
    private static void zeroOutMemory(long address, long size) {
        // Clear the 8-byte buckets covering the used range. It is safe to clear
        // beyond `size` as long as we stay inside the pool.
        // Note: we are using fallthrough here.
        switch ((int) ((size + Long.BYTES - 1) >>> 3)) {
            case 8: U.putLong(address + 0x38, 0L);
            case 7: U.putLong(address + 0x30, 0L);
            case 6: U.putLong(address + 0x28, 0L);
            case 5: U.putLong(address + 0x20, 0L);
            case 4: U.putLong(address + 0x18, 0L);
            case 3: U.putLong(address + 0x10, 0L);
            case 2: U.putLong(address + 0x08, 0L);
            case 1: U.putLong(address, 0L);
            case 0: break;
            default: throw new AssertionError(size);
        }
    }

    private static int clampedPowerOfPropertyOr(String name, int defaultPower) {
        if (VM.isDirectMemoryPageAligned()) {
            return -1;
        }
        final int power = Integer.getInteger(name, defaultPower);

        return power <= 0
                ? -1
                : 1 << Math.clamp(power, 3, 6);
    }

    /**
     * A shared confined segment pool for virtual threads.
     */
    private static final class VirtualThreadPool {

        private VirtualThreadPool() { }

        // We create an over-provisioned number of slots to reduce the
        // probability that two virtual threads compete for the same slot.
        private static final int SLOTS = slotCount();
        private static final int SLOT_MASK = SLOTS - 1;
        // The distance between each slot. This is usually larger than 1 to
        // reduce contention.
        private static final long SLOT_OFFSET = slotOffset();
        // Slot owner states: zero     -> Free to acquire
        //                    positive -> Acquired by a live virtual thread
        private static final long RELEASED = 0;

        // Sentinel value for no pooling.
        private static final long NO_POOLING = -1;

        // Raw memory pointer to the pool. The pool is then sliced into separate
        // segments, each of which has a size of POOLED_MEMORY_SIZE.
        private static final long POOL;
        private static final long OWNERS;

        static {
            final long pool = allocatePool();
            final long owners = allocateOwners();
            if (pool == NO_POOLING || owners == NO_POOLING) {
                if (pool > 0) {
                    U.freeMemory(pool);
                }
                if (owners > 0) {
                    U.freeMemory(owners);
                }
                POOL = NO_POOLING;
                OWNERS = NO_POOLING;
            } else {
                POOL = pool;
                OWNERS = owners;
                virtualPoolInitialized = true;
            }
        }

        @ForceInline
        static long acquire(Thread thread) {
            if (POOL == NO_POOLING) {
                return 0;
            }
            final long owner = thread.threadId();
            final long slot = slotFor(owner);
            final long ownerAddress = ownerAddress(slot);
            return U.compareAndSetLong(null, ownerAddress, RELEASED, owner)
                    ? poolAddress(slot)
                    : 0;
        }

        @ForceInline
        static boolean release(Thread thread, long size) {
            if (POOL == NO_POOLING) {
                return false;
            }
            final long owner = thread.threadId();
            final long slot = slotFor(owner);
            final long ownerAddress = ownerAddress(slot);
            if (U.getLongVolatile(null, ownerAddress) != owner) {
                return false;
            }
            final long address = poolAddress(slot);
            zeroOutMemory(address, size);
            return U.compareAndSetLong(null, ownerAddress, owner, RELEASED);
        }

        @ForceInline
        static void releaseIfOwned(Thread thread, long size) {
            if (POOL == NO_POOLING) {
                return;
            }
            final long owner = thread.threadId();
            final long slot = slotFor(owner);
            final long ownerAddress = ownerAddress(slot);
            if (U.getLongVolatile(null, ownerAddress) == owner) {
                final long address = poolAddress(slot);
                zeroOutMemory(address, size);
                if (!U.compareAndSetLong(null, ownerAddress, owner, RELEASED)) {
                    throw new IllegalStateException("Cannot release pooled memory: " + thread);
                }
            }
        }

        @ForceInline
        static long currentPool(Thread thread) {
            if (POOL == NO_POOLING) {
                return 0;
            }
            final long owner = thread.threadId();
            final long slot = slotFor(owner);
            return U.getLongVolatile(null, ownerAddress(slot)) == owner
                    ? poolAddress(slot)
                    : 0;
        }

        @ForceInline
        private static long slotFor(long owner) {
            return owner & SLOT_MASK;
        }

        @ForceInline
        private static long poolAddress(long slot) {
            return POOL + slot * POOLED_MEMORY_SIZE;
        }

        @ForceInline
        private static long ownerAddress(long slot) {
            return OWNERS + slot * SLOT_OFFSET;
        }

        private static long slotOffset() {
            final int cacheLineSize = U.dataCacheLineFlushSize();
            return cacheLineSize > 0
                    ? Math.max(cacheLineSize, Long.BYTES)
                    : Long.BYTES; // No cache line support
        }

        // Always a power of two.
        private static int slotCount() {
            // Default carrier threads times two
            final int target = Runtime.getRuntime().availableProcessors() << 1;
            return Integer.highestOneBit(target - 1) << 1;
        }

        private static long allocatePool() {
            return mallocAndZero(POOLED_MEMORY_SIZE * SLOTS);
        }

        private static long allocateOwners() {
            return mallocAndZero(SLOTS * SLOT_OFFSET);
        }

        private static long mallocAndZero(long size) {
            if (POOLED_MEMORY_SIZE <= 0) {
                return NO_POOLING;
            }
            try {
                final long pool = U.allocateMemory(size);
                U.setMemory(pool, size, (byte) 0);
                return pool;
            } catch (OutOfMemoryError _) {
                return NO_POOLING;
            }
        }
    }
}
