package java.lang;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.ForceInline;

/**
 * A shared confined segment pool for virtual threads.
 * <p>
 * Virtual threads use a fixed number of native-memory slots instead of one
 * native allocation per virtual thread. A virtual thread maps to a candidate
 * slot from its thread id and acquires the slot with a CAS operation. If the slot is
 * already acquired, allocation falls back to the regular native allocator.
 * <p>
 * Slots are padded to reduce contention on the acquisition flags. The shared
 * native memory is intentionally process-lifetime memory.
 * <p>
 * For performance reasons, this class operates directly on native memory and pointers
 * via Unsafe.
 * <p>
 * By not attaching segment pools to the underlying carrier thread, the solution
 * can be greatly simplified and we do not have to consider migrating virtual threads.
 */
final class ConfinedSegmentPool {

    private ConfinedSegmentPool() {
    }

    private static final Unsafe U = Unsafe.getUnsafe();

    // We create an over-provisioned number of slots to reduce the
    // probability that two virtual threads compete for the same
    // slot.
    private static final int SLOTS = Runtime.getRuntime().availableProcessors() * 2;
    // The distance between each slot. This is usually larger than 1 to
    // reduce contention.
    private static final long SLOT_OFFSET = slotOffset();
    // Flag states:
    private static final byte RELEASED = 0;
    private static final byte ACQUIRED = 1;

    // Sentinel value for no pooling.
    // Selecting -1 rather than zero allows constant folding.
    private static final long NO_POOLING = -1;

    // Raw memory pointer to the pool. The pool is then
    // sliced into separate segments each of which as a size
    // of PoolConfigHolder.POOLED_MEMORY_SIZE.
    private static final long POOL;
    private static final long FLAGS;

    static {
        final long pool = allocatePool();
        final long flags = allocateFLags();
        if (pool == NO_POOLING || flags == NO_POOLING) {
            // We were unable to allocate memory
            POOL = NO_POOLING;
            FLAGS = NO_POOLING;
        } else {
            POOL = pool;
            FLAGS = flags;
        }
    }

    @ForceInline
    static long acquirePooledMemory(VirtualThread thread) {
        // Do not acquire if pooling is disabled
        if (POOL == NO_POOLING) {
            return 0;
        }
        final long slot = slotFor(thread);
        return U.compareAndSetByte(null, flagAddress(slot), RELEASED, ACQUIRED)
                ? POOL + slot * Thread.PoolConfigHolder.POOLED_MEMORY_SIZE
                : 0;
    }

    @ForceInline
    static void releasePooledMemory(VirtualThread thread,
                                    long address,
                                    long size) {
        // Zero out memory before releasing the slot.
        Thread.zeroOutMemory(address, size);
        final long slot = slotFor(thread);
        if (!U.compareAndSetByte(null, flagAddress(slot), ACQUIRED, RELEASED)) {
            throw new IllegalStateException("Cannot release pooled memory: " + thread);
        }
    }

    @ForceInline
    static long slotFor(VirtualThread thread) {
        // The thread id is always positive so it is safe to do a modulo operation
        return thread.threadId() % SLOTS;
    }

    @ForceInline
    static long flagAddress(long slot) {
        return FLAGS + slot * SLOT_OFFSET;
    }

    private static final long slotOffset() {
        // By placing the allocation flags on separate cache lines
        // we can reduce contention imposed by CAS operations.
        final int cacheLineSize = U.dataCacheLineFlushSize();
        return cacheLineSize > 0
                ? cacheLineSize
                : 1; // No cache line support
    }

    private static long allocatePool() {
        return mallocAndZero(Thread.PoolConfigHolder.POOLED_MEMORY_SIZE * SLOTS);
    }

    private static long allocateFLags() {
        return mallocAndZero(SLOTS * SLOT_OFFSET);
    }

    private static long mallocAndZero(long size) {
        if (Thread.PoolConfigHolder.POOLED_MEMORY_SIZE <= 0) {
            // Pooling disabled
            return NO_POOLING;
        }
        try {
            final long pool = U.allocateMemory(size);
            U.setMemory(pool, size, (byte) 0);
            return pool;
        } catch (OutOfMemoryError e) {
            return NO_POOLING;
        }
    }

}
