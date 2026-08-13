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
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;

/**
 * Provides reusable native-memory pools for confined arenas.
 *<p>
 * Each platform thread lazily maintains a cache of up to {@value #PLATFORM_POOL_COUNT}
 * fixed-size pools. Small allocations made by a confined arena are carved out from
 * one pool. Allocations that do not fit in the pool use the regular native allocator.
 * If no cached pool is available, the arena may allocate a local pool and
 * attempt to cache it when the arena is closed.
 *<p>
 * A platform thread retains acquired pools in its cache, marking them as
 * unavailable. This allows thread-exit cleanup to free pools held by confined
 * arenas that were not closed before the owning thread terminated.
 *<p>
 * A virtual thread acquires pools from its current carrier thread. An
 * acquired pool is removed from the carrier's cache and is owned exclusively
 * by the arena until it is closed. This prevents the original carrier from
 * freeing the pool if the virtual thread migrates and that carrier terminates.
 * On close, the pool is returned to the current carrier's cache or freed if
 * that cache is full. Carrier-thread termination still frees pools remaining
 * in that carrier's cache but does not affect unclosed confined arenas.
 *<p>
 * Before a pool is released, the portion used by the arena is cleared.
 * Together with clearing performed when a pool is initially allocated, this
 * ensures that pooled allocations do not expose data written by an earlier
 * arena.
 *<p>
 * The pool cache is accessed only by its owning platform thread, either
 * directly or while that thread is acting as a virtual-thread carrier.
 * Consequently, cache acquisition and release do not require synchronization.
 *<p>
 * Pool entries use the following representation:
 * <ul>
 *     <li>zero: empty cache entry;
 *     <li>positive address: available pool;
 *     <li>negative address: pool acquired by a platform-thread arena.
 * </ul>
 * Native pool addresses are required to be positive, allowing the sign to
 * encode whether a cached pool is available or platform-arena-owned.
 * <p>
 * Pooling can be disabled through the internal pool-size configuration.
 * When disabled, all allocations use the regular native allocator.
 *<p>
 * This class operates directly on native addresses using {@link Unsafe}.
 * Its ownership and clearing invariants must therefore be preserved when
 * acquisition, release, or thread-termination protocols are changed.
 * Defensive release checks detect invalid sizes and duplicate releases, but
 * cannot validate arbitrary native addresses.
 */
public final class ConfinedSegmentPool {

    private ConfinedSegmentPool() { }

    private static final Unsafe U = Unsafe.getUnsafe();

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    // Internal tuning knob; no behavioral or compatibility guarantees are given.
    // Setting the pool-size power to 0 disables confined pooling.
    private static final String POOLED_MEMORY_PROPERTY = "java.lang.foreign.native.confined.pool.power.size";

    // -1 disables pooling; otherwise the pool size is 8, 16, 32, or 64 bytes.
    private static final long POOLED_MEMORY_SIZE = clampedPowerOfPropertyOr(POOLED_MEMORY_PROPERTY, 6);

    private static final int PLATFORM_POOL_COUNT = 4;

    // Constant-folded away in release builds; checks owner-thread invariants
    // in debug builds.
    private static final boolean DEBUG = !"release".equals(VM.getSavedProperty("jdk.debug"));

    /**
     * Returns the size of the native memory pool.
     */
    public static long pooledMemorySize() {
        return POOLED_MEMORY_SIZE;
    }


    /**
     * Acquires a pool for an arena owned by {@code thread}. A virtual-thread
     * arena acquires from the current carrier's cache.
     *
     * @return a positive native address, or zero if no pool is available
     */
    @ForceInline
    static long acquire(Thread thread) {
        assertCurrentThreadInDebugMode(thread);
        if (POOLED_MEMORY_SIZE <= 0) {
            return 0;
        }
        return thread.isVirtual()
                ? acquireVirtual(JLA.currentCarrierThread())
                : acquirePlatform(thread);
    }

    /**
     * Allocates a pool owned directly by the arena and not yet present in any
     * thread cache. On arena close, the pool is cached or freed.
     */
    static long allocateLocal(Thread thread) {
        assertCurrentThreadInDebugMode(thread);
        if (POOLED_MEMORY_SIZE <= 0) {
            return 0;
        }
        return allocatePlatformPool();
    }

    /**
     * Clears the used prefix and returns the pool to the appropriate cache.
     * Platform-thread arenas return pools to their owner; virtual-thread arenas
     * return pools to the current carrier.
     */
    @ForceInline
    static void release(Thread thread, long pool, long size) {
        assertCurrentThreadInDebugMode(thread);
        final Thread cacheOwner = thread.isVirtual()
                ? JLA.currentCarrierThread()
                : thread;
        releasePlatform(cacheOwner, pool, size);
    }

    /**
     * Frees all available and platform-arena-owned pools recorded in the
     * terminating platform thread's cache.
     */
    public static void releaseOnThreadExit(Thread thread) {
        final long[] pools = JLA.getConfinedMemoryPools(thread);
        if (pools == null) {
            return;
        }
        for (int i = 0; i < PLATFORM_POOL_COUNT; i++) {
            final long pool = pools[i];
            if (pool != 0) {
                U.freeMemory(Math.abs(pool));
                pools[i] = 0;
            }
        }
    }

    @ForceInline
    private static void assertCurrentThreadInDebugMode(Thread thread) {
        if (DEBUG && thread != Thread.currentThread()) {
            throw new AssertionError();
        }
    }

    @ForceInline
    private static long acquirePlatform(Thread thread) {
        final long[] pools = JLA.getConfinedMemoryPools(thread);
        if (pools == null) {
            return 0;
        }
        for (int i = 0; i < PLATFORM_POOL_COUNT; i++) {
            final long pool = pools[i];
            if (pool > 0) {
                pools[i] = -pool; // available (+p) -> platform-owned (-p)
                return pool;
            }
        }
        return 0;
    }

    /**
     * Special acquire method for virtual threads utilizing the pool of the underlying
     * carrier thread. This method does not store acquired pools in the array to protect
     * against use-after-free or double-free after a virtual thread migrated to another
     * carrier thread.
     *
     * @param carrier thread from which pools should be used
     * @return the acquired pool or zero if no pool could be acquired.
     */
    @ForceInline
    private static long acquireVirtual(Thread carrier) {
        long[] pools = JLA.getConfinedMemoryPools(carrier);
        if (pools == null) {
            return 0;
        }
        for (int i = 0; i < PLATFORM_POOL_COUNT; i++) {
            long pool = pools[i];
            if (pool > 0) {
                pools[i] = 0; // available (+p) -> arena-owned and detached; no free on thread exit
                return pool;
            }
        }
        return 0;
    }

    private static long allocatePlatformPool() {
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
        return address;
    }

    @ForceInline
    private static void releasePlatform(Thread thread, long pool, long size) {
        // Reject invalid prefixes before zeroOutMemory performs unchecked writes.
        if (pool <= 0 || size < 0 || size > POOLED_MEMORY_SIZE) {
            throw cannotReleasePooledMemory(pool, size);
        }

        long[] pools = JLA.getConfinedMemoryPools(thread);
        if (pools == null) {
            pools = createPoolCacheOrFree(thread, pool);
            if (pools == null) {
                return; // The `createPoolCacheOrFree` method freed the pool.
            }
        }

        zeroOutMemory(pool, size);

        int empty = -1;
        for (int i = 0; i < PLATFORM_POOL_COUNT; i++) {
            final long entry = pools[i];
            if (entry == -pool) {
                pools[i] = pool; // platform-owned (-p) -> available (+p)
                return;
            }
            if (entry == pool) {
                throw cannotReleasePooledMemory(pool, size); // already released
            }
            if (entry == 0 && empty < 0) {
                empty = i;
            }
        }
        if (empty >= 0) {
            pools[empty] = pool;
        } else {
            U.freeMemory(pool);
        }
    }

    // Support method to isolate exception handling from the hot inline path
    @DontInline
    private static long[] createPoolCacheOrFree(Thread thread, long pool) {
        try {
            return JLA.getOrCreateConfinedMemoryPools(thread, PLATFORM_POOL_COUNT);
        } catch (OutOfMemoryError _) {
            // In the unlikely event a `new long[]` fails we still need to free the
            // pool and allow the rest of the Arena's cleanup operations to continue
            U.freeMemory(pool);
            return null;
        }
    }

    @DontInline
    private static IllegalStateException cannotReleasePooledMemory(long pool, long size) {
        return new IllegalStateException("Cannot release pooled memory owned by " + JLA.currentCarrierThread() + ", pool = " + pool + ", size = " + size);
    }

    @SuppressWarnings("fallthrough")
    @ForceInline
    private static void zeroOutMemory(long address, long size) {
        // Deliberate fall-through clears the required number of 8-byte buckets
        // without a loop branch. The validated size guarantees writes remain in-pool.
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
            default: throw new IllegalStateException(Long.toString(size));
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

}
