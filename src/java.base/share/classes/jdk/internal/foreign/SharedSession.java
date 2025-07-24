/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2025 Arm Limited and/or its affiliates.
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

import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.vm.annotation.ForceInline;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * A shared session, which can be shared across multiple threads. Closing a shared session has to ensure that
 * (i) only one thread can successfully close a session (e.g. in a close vs. close race) and that
 * (ii) no other thread is accessing the memory associated with this session while the segment is being
 * closed. To ensure the former condition, the method {@link #justClose() justClose} is synchronized. Ensuring
 * the latter is trickier, using a number of counters to track how many threads are accessing the memory and
 * requires a complex synchronization protocol (see {@link jdk.internal.misc.ScopedMemoryAccess}).
 * Since it is the responsibility of the closing thread to make sure that no concurrent access is possible,
 * checking the liveness bit upon access can be performed in plain mode, as in the confined case.
 */
final class SharedSession extends MemorySessionImpl {

    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

    final private AtomicIntegerArray counters;

    final static private int numCounters;
    final static private int mask;

    // The number of ints per cacheline.
    final static private int multiplier;
    final static int CNT_CLOSING = -1;
    final static int CNT_CLOSED = -2;

    private static final jdk.internal.misc.Unsafe UNSAFE = jdk.internal.misc.Unsafe.getUnsafe();

    static {
        int cpus = Runtime.getRuntime().availableProcessors();

        if (cpus < 2) {
            // Single CPU case.
            cpus = 1;
            mask = 0;
        } else {
            // Round up to next power of 2 CPUs.
            // Cap at 1024 to avoid excessive size.
            cpus = Integer.min(Integer.highestOneBit(cpus) << 1, 1024);
            mask = cpus - 1;
        }
        numCounters = cpus;

        int cacheLineSize = UNSAFE.dataCacheLineFlushSize();

        // Each counter is an integer on its own cacheline.
        multiplier = ((cacheLineSize < Integer.BYTES) ? 64 : cacheLineSize) / Integer.BYTES;
    }


    SharedSession() {
        super(null, new SharedResourceList());
        counters = new AtomicIntegerArray(numCounters * multiplier);
    }

    @ForceInline
    private int getCounter() {
        return Thread.currentThread().hashCode() & mask;
    }

    @ForceInline
    private int getAcquire(int index) {
        assert numCounters > index;
        return counters.getAcquire(index * multiplier);
    }

    @ForceInline
    private int compareAndExchange(int index, int expected, int value) {
        assert numCounters > index;
        return counters.compareAndExchange(index * multiplier, expected, value);
    }

    @Override
    @ForceInline
    public int acquire0() {
        int ticket = getCounter();
        int value = 0;
        int old = getAcquire(ticket);
        do {
            value = old;

            if (value >= 0) {
                if (value == MAX_FORKS) {
                    throw tooManyAcquires();
                }
                old = compareAndExchange(ticket, value, value + 1);
            } else if (value == CNT_CLOSED) {
                // The following method will wait for the justClose() method to
                // set STATE variable to CLOSED, after all counters have been set
                // to CNT_CLOSED.
                throw sharedSessionAlreadyClosed();
            } else if (value == CNT_CLOSING) {
                // The closing thread will either succeed, changing this counter
                // to CNT_CLOSED or fail and backout the counter state to "0".
                do {
                    old = getAcquire(ticket);
                    Thread.onSpinWait();
                } while (old == CNT_CLOSING);
                // On exit value is CNT_CLOSING and old is >=0 or CNT_CLOSED.
                assert (old == CNT_CLOSED) || (old >= 0);
            }
        } while (old != value);

        return ticket;
    }

    @Override
    @ForceInline
    public void release0(int ticket) {
        assert (ticket >= 0 && ticket < numCounters) : "Invalid ticket.";

        int value = 0;
        int old = getAcquire(ticket);
        do {
            value = old;

            if (value > 0) {
                old = compareAndExchange(ticket, value, value - 1);
            } else {
                throw alreadyClosed();
            }
        } while (old != value);
    }

    synchronized void justClose() {
        int value;

        if (state == CLOSED) {
            throw alreadyClosed();
        }

        // Attempt to transition all counters to CNT_CLOSING state.
        // Normally each counter should be 0. This method atomically changes them
        // to CNT_CLOSING (-1) and if that succeeds, then changes them all to
        // CNT_CLOSED (-2) then updates STATE and the SCOPED_MEMORY_ACCESS to
        // match.
        // Threads calling acquire0 will spin if CNT_CLOSING is acquired, and will
        // either fail if this method succeeds, or pass if this method fails to close.
        // If this method encounters a counter >0, counters that were set to
        // CNT_CLOSING are set to 0 and this method fails.
        for (int i = 0; i < numCounters; i++) {
            value = compareAndExchange(i, 0, CNT_CLOSING);

            assert value != CNT_CLOSING;

            if (value == CNT_CLOSED) {
                // It is already closed - throw an exception.
                throw alreadyClosed();
            }

            if (value != 0) {
                // Total the counters we haven't set to CNT_CLOSING.
                // This might be inaccurate, but won't be zero.
                int total = value;
                for (int j = i + 1; j < numCounters; j++) {
                    int counter = counters.get(j * multiplier);
                    assert counter >= 0;

                    total += counter;
                }

                // Swapping from 0 to CNT_CLOSING failed, set back to 0.
                // We can't set the current one, that's the one that failed.
                for (int j = 0; j < i; j++) {
                    assert counters.getAcquire(j * multiplier) == CNT_CLOSING;
                    counters.setRelease(j * multiplier, 0);
                }

                throw alreadyAcquired(total);
            }
        }
        // Success, any threads acquiring will spin on CNT_CLOSING now, for this counter.

        // This causes threads that were spinning on CNT_CLOSING to throw alreadyClosed().
        for (int i = 0; i < numCounters; i++) {
            assert counters.getAcquire(i * multiplier) == CNT_CLOSING;

            counters.setRelease(i * multiplier, CNT_CLOSED);
        }

        // Set MemorySessionImpl.state to match the counters closed status.
        STATE.setVolatile(this, CLOSED);
        SCOPED_MEMORY_ACCESS.closeScope(this, ALREADY_CLOSED);
    }

    @Override
    public boolean isCloseable() {
        if (state == CLOSED) {
            return true;
        }

        for (int i = 0; i < numCounters; i++) {
            int value = getAcquire(i);

            if (value == CNT_CLOSING) {
                while ((value = getAcquire(i)) == CNT_CLOSING) {
                    Thread.onSpinWait();
                }

                // Restart from first counter.
                i = -1;
                continue;
            }

            if (value == CNT_CLOSED) {
                return false;
            } else if (value > 0) {
                return false;
            }
        }

        return true;
    }

    private IllegalStateException sharedSessionAlreadyClosed() {
        // To avoid the situation where a scope fails to be acquired or closed but still reports as
        // alive afterward, we wait for the state to change before throwing the exception
        while ((int) STATE.getVolatile(this) == OPEN) {
            Thread.onSpinWait();
        }
        return alreadyClosed();
    }
}
