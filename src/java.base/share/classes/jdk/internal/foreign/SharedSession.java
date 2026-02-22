/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.invoke.MhUtil;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.vm.annotation.ForceInline;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.LongAdder;

/**
 * A shared session, which can be shared across multiple threads. Closing a shared session has to ensure that
 * (i) only one thread can successfully close a session (e.g. in a close vs. close race) and that
 * (ii) no other thread is accessing the memory associated with this session while the segment is being
 * closed.
 * To ensure the former condition, a CAS is performed on the closingPhase from 0 to 1 to exclusively spin-lock
 * access to the decision logic which decides whether there are live threads that have the session acquired or not, and
 * either unlocks closingPhase back to 0 or promotes it to 2.
 * The decision logic uses two instances of {@link LongAdder}, {@code acquireCount} and {@code releaseCount} which act as atomic
 * counters. If the only mutating method used on {@code LongAdder} is {@link LongAdder#increment()}, then it is indistinguishable
 * from {@link java.util.concurrent.atomic.AtomicLong} except it has practically zero contention when incrementing the value,
 * but some overhead when reading the value depending on previously executed concurrency when incrementing.
 * Ensuring the latter is trickier, and requires a complex synchronization protocol (see {@link jdk.internal.misc.ScopedMemoryAccess}).
 * Since it is the responsibility of the closing thread to make sure that no concurrent access is possible,
 * clever ordering of accesses to all of {@code acquireCount}, {@code releaseCount} and {@code closingPhase} makes this possible
 * in the following way:
 * <ul>
 *     <li>acquire-ing thread 1st increments {@code acquireCount} then checks the {@code closingPhase}.
 *     If closingPhase is open (0), acquire0 returns immediately (this is the fast-path). If closingPhase is undecided
 *     (1 - maybe closing) then it spin-waits for decision. If it reverts to open (0), acquire0 returns, else it promotes to
 *     closed (2) in which case it throws exception</li>
 *     <li>close-ing thread 1st CAS-es {@code closingPhase} from 0 to 1 and when successful, then reads {@code releaseCount}
 *     and then reads {@code acquireCount}. This order ensures that the difference between acquireCount and releaseCount is never
 *     less than the number of threads that are active somewhere between the acquire0 and release0 calls. It may temporarily be more,
 *     but that only means {@code justClose} will throw "already acquired" exception due to race with releasing thread.
 * </ul>
 */
sealed class SharedSession extends MemorySessionImpl permits ImplicitSession {

    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();
    private static final VarHandle CLOSING_PHASE = MhUtil.findVarHandle(MethodHandles.lookup(), "closingPhase", int.class);

    private final LongAdder acquireCount = new LongAdder();
    private final LongAdder releaseCount = new LongAdder();
    private volatile int closingPhase; // 0=open, 1=maybe closing, 2=closed

    SharedSession() {
        super(null, new SharedResourceList());
    }

    @Override
    @ForceInline
    public void acquire0() {
        acquireCount.increment();
        // above write to acquireCount's Cell may have been reordered after below read of closingPhase if
        // it was not for this fullFence that prevents this from happening
        VarHandle.fullFence();
        int cp;
        while ((cp = closingPhase) != 0) {
            if (cp == 2) {
                releaseCount.increment(); // not required, but useful for debugging
                throw sharedSessionAlreadyClosed();
            }
            Thread.onSpinWait();
        }
    }

    @Override
    @ForceInline
    public void release0() {
        releaseCount.increment();
    }

    void justClose() {
        int cp;
        while ((cp = (int) CLOSING_PHASE.compareAndExchange(0, 1)) != 0) {
            if (cp == 2) {
                throw sharedSessionAlreadyClosed();
            }
            Thread.onSpinWait();
        }
        // above write to closingPhase may have been reordered after below reads of releaseCount's and acquireCount's Cells if
        // it was not for this fullFence that prevents this from happening
        VarHandle.fullFence();
        // reading order is important: 1st RELEASE_COUNT, 2nd ACQUIRE_COUNT
        long value = -releaseCount.longValue() + acquireCount.longValue();
        if (value > 0) {
            closingPhase = 0;
            throw alreadyAcquired((int) value);
        }
        closingPhase = 2;

        STATE.setVolatile(this, CLOSED);
        SCOPED_MEMORY_ACCESS.closeScope(this, ALREADY_CLOSED);
    }

    private IllegalStateException sharedSessionAlreadyClosed() {
        // To avoid the situation where a scope fails to be acquired or closed but still reports as
        // alive afterward, we wait for the state to change before throwing the exception
        while ((int) STATE.getVolatile(this) == OPEN) {
            Thread.onSpinWait();
        }
        return alreadyClosed();
    }

    /**
     * A shared resource list; this implementation has to handle add vs. add races, as well as add vs. cleanup races.
     */
    static class SharedResourceList extends ResourceList {

        static final VarHandle FST = MhUtil.findVarHandle(
                MethodHandles.lookup(), ResourceList.class, "fst", ResourceCleanup.class);

        @Override
        void add(ResourceCleanup cleanup) {
            while (true) {
                ResourceCleanup prev = (ResourceCleanup) FST.getVolatile(this);
                if (prev == ResourceCleanup.CLOSED_LIST) {
                    // too late
                    throw alreadyClosed();
                }
                cleanup.next = prev;
                if (FST.compareAndSet(this, prev, cleanup)) {
                    return; //victory
                }
                // keep trying
            }
        }

        void cleanup() {
            // At this point we are only interested about add vs. close races - not close vs. close
            // (because MemorySessionImpl::justClose ensured that this thread won the race to close the session).
            // So, the only "bad" thing that could happen is that some other thread adds to this list
            // while we're closing it.
            if (FST.getAcquire(this) != ResourceCleanup.CLOSED_LIST) {
                //ok now we're really closing down
                ResourceCleanup prev = null;
                while (true) {
                    prev = (ResourceCleanup) FST.getVolatile(this);
                    // no need to check for DUMMY, since only one thread can get here!
                    if (FST.compareAndSet(this, prev, ResourceCleanup.CLOSED_LIST)) {
                        break;
                    }
                }
                cleanup(prev);
            } else {
                throw alreadyClosed();
            }
        }
    }
}
