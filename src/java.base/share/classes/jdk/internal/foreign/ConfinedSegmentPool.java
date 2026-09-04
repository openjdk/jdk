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
import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationSupport;
import jdk.internal.vm.annotation.DontInline;
import jdk.internal.vm.annotation.ForceInline;

/**
 * Provides reusable native-memory pools for confined arenas.
 *<p>
 * Each platform thread lazily maintains a cache containing a configurable number
 * of fixed-size pools. Small allocations made by a confined arena are carved out
 * from one pool. Allocations that do not fit in the pool use the regular native
 * allocator. If no cached pool is available, the arena may allocate a local pool
 * and attempt to cache it when the arena is closed.
 *<p>
 * A platform thread acquires pools from its own cache, while a virtual thread
 * acquires pools from its current carrier thread's cache. In both cases, an
 * acquired pool is removed from the cache and owned exclusively by the arena
 * until it is closed. Consequently, platform- or carrier-thread termination
 * can free only available cached pools and does not affect pools owned by open
 * arenas. Removing virtual-thread-owned pools from the carrier cache also
 * prevents the original carrier from freeing such a pool if the virtual thread
 * migrates and that carrier terminates.
 *<p>
 * When an arena is closed, its pool is returned to the owning platform
 * thread's cache or, for a virtual thread, to the current carrier's cache. The
 * pool is freed instead if the target cache is full.
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
 *     <li>non-zero: available pool.
 * </ul>
 * Acquired pools are not represented in the cache.
 * <p>
 * Pooling can be disabled through the internal pool configuration.
 * When disabled, all allocations use the regular native allocator.
 *<p>
 * This class operates directly on native addresses using {@link Unsafe}.
 * Its ownership and clearing invariants must therefore be preserved when
 * acquisition, release, or thread-termination protocols are changed.
 * Defensive release checks detect invalid sizes and some duplicate releases, but
 * cannot validate arbitrary native addresses.
 */
public final class ConfinedSegmentPool {

    private ConfinedSegmentPool() { }

    private static final Unsafe U = Unsafe.getUnsafe();

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private static final String PROPERTY_PATH = "jdk.internal.foreign.native.confined.pool.power.";

    // Unsupported implementation-specific tuning knob; no behavioral or compatibility
    // guarantees are given.
    // A negative value disables pooling; otherwise the pool size is
    // 2^3, 2^4, ..., 2^20 bytes, defaulting to 2^6 = 64 bytes.
    private static final String POOLED_MEMORY_SIZE_PROPERTY = PROPERTY_PATH + "size";

    private static final long POOLED_MEMORY_SIZE =
            clampedPowerOfPropertyOr(POOLED_MEMORY_SIZE_PROPERTY, 3, 20, 6);

    // Unsupported implementation-specific tuning knob; no behavioral or compatibility
    // guarantees are given.
    // A negative value disables pooling; otherwise the pool count is
    // 1, 2, 4, or 8, defaulting to 4 corresponding to a cache capacity of four available
    // pools per platform thread.
    private static final String THREAD_POOL_COUNT_PROPERTY = PROPERTY_PATH + "count";

    private static final int THREAD_POOL_COUNT =
            clampedPowerOfPropertyOr(THREAD_POOL_COUNT_PROPERTY, 0, 3, 2);

    private static final boolean POOLING_DISABLED = POOLED_MEMORY_SIZE <= 0 || THREAD_POOL_COUNT <= 0;

    /**
     * Returns the size of the native memory pool, or {@code -1} if pooling is disabled.
     */
    public static long pooledMemorySize() {
        return POOLING_DISABLED ? -1 : POOLED_MEMORY_SIZE;
    }

    /**
     * Acquires and removes a pool from the appropriate cache for an arena owned
     * by the current thread. A platform-thread arena uses its owner's cache, while
     * a virtual-thread arena uses the current carrier's cache.
     *
     * @return a non-zero native address, or zero if no pool is available
     */
    @ForceInline
    static long acquire() {
        if (POOLING_DISABLED) {
            return 0;
        }
        final Thread thread = Thread.currentThread();
        if (ContinuationSupport.isSupported() && thread.isVirtual()) {
            Continuation.pin();
            try {
                return acquireFromCache(JLA.currentCarrierThread());
            } finally {
                Continuation.unpin();
            }
        } else {
            return acquireFromCache(thread);
        }
    }

    /**
     * Allocates a pool owned directly by the arena and not yet present in any
     * thread cache. On arena close, the pool is cached or freed.
     */
    static long allocateLocal() {
        return POOLING_DISABLED ? 0 : allocateDetachedPool();
    }

    /**
     * Clears the used prefix and returns the pool to the appropriate cache.
     * Platform-thread arenas return pools to their owner; virtual-thread arenas
     * return pools to the current carrier.
     */
    @ForceInline
    static void release(long pool, long usedSize) {
        final Thread thread = Thread.currentThread();
        if (ContinuationSupport.isSupported() && thread.isVirtual()) {
            Continuation.pin();
            try {
                releaseToCache(JLA.currentCarrierThread(), pool, usedSize);
            } finally {
                Continuation.unpin();
            }
        } else {
            releaseToCache(thread, pool, usedSize);
        }
    }

    /**
     * Frees all available pools recorded in the terminating platform thread's
     * cache. Pools owned by open arenas are detached from the cache and are not
     * affected.
     */
    public static void threadTerminated(long[] pools) {
        for (int i = 0; i < pools.length; i++) {
            final long pool = pools[i];
            if (pool != 0) {
                U.freeMemory(pool);
                pools[i] = 0;
            }
        }
    }

    @ForceInline
    private static long acquireFromCache(Thread cacheOwner) {
        final long[] pools = JLA.getConfinedMemoryPools(cacheOwner);
        if (pools == null) {
            return 0;
        }
        for (int i = 0; i < pools.length; i++) {
            final long pool = pools[i];
            if (pool != 0) {
                pools[i] = 0; // available -> arena-owned and detached
                return pool;
            }
        }
        return 0;
    }

    private static long allocateDetachedPool() {
        final long address;
        try {
            address = U.allocateMemory(POOLED_MEMORY_SIZE);
        } catch (OutOfMemoryError _) {
            return 0;
        }
        zeroOutMemory(address, POOLED_MEMORY_SIZE);
        return address;
    }

    @ForceInline
    private static void releaseToCache(Thread cacheOwner, long pool, long usedSize) {
        // Reject invalid prefixes before zeroOutMemory performs unchecked writes.
        if (pool == 0 || usedSize < 0 || usedSize > POOLED_MEMORY_SIZE) {
            throw cannotReleasePooledMemory(pool, usedSize);
        }

        long[] pools = JLA.getConfinedMemoryPools(cacheOwner);
        if (pools == null) {
            pools = createPoolCacheOrFree(cacheOwner, pool);
            if (pools == null) {
                return; // The `createPoolCacheOrFree` method freed the pool.
            }
        }

        for (int i = 0; i < pools.length; i++) {
            final long entry = pools[i];
            if (entry == pool) {
                throw cannotReleasePooledMemory(pool, usedSize); // already released
            }
            if (entry == 0) {
                zeroOutMemory(pool, usedSize);
                pools[i] = pool;
                return;
            }
        }
        U.freeMemory(pool);
    }

    // Support method to isolate exception handling from the hot inline path
    @DontInline
    private static long[] createPoolCacheOrFree(Thread cacheOwner, long pool) {
        try {
            return JLA.getOrCreateConfinedMemoryPools(cacheOwner, THREAD_POOL_COUNT);
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
        // Pools are always at least `long` aligned so we can use aligned Unsafe access
        // below.
        // We are first checking `POOLED_MEMORY_SIZE` here rather than
        // `size` to enable potential code elimination by the C2 compiler.
        if (POOLED_MEMORY_SIZE <= 64 || size <= 64) {
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
        } else {
            // This is safe because the underlying pool is guaranteed to be of a size
            // that is a multiple of a `long`.
            for (long i = 0; i < size; i += Long.BYTES) {
                U.putLong(address + i, 0L);
            }
        }
    }

    private static int clampedPowerOfPropertyOr(String name, int minPower,
                                                 int maxPower, int defaultPower) {
        // Slicing out memory segment from an otherwise page aligned pool slab would make
        // native memory slices non-aligned to page boundaries. Hence, we need to
        // disable pooling in such cases.
        if (VM.isDirectMemoryPageAligned()) {
            return -1;
        }
        final int power = Integer.getInteger(name, defaultPower);

        return power < 0
                ? -1
                : Math.toIntExact(1L << Math.clamp(power, minPower, maxPower));
    }

}
