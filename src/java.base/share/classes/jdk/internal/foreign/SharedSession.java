/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.invoke.MhUtil;
import jdk.internal.vm.annotation.ForceInline;

/**
 * A shared session, which can be shared across multiple threads. Closing a shared session has to ensure that
 * (i) only one thread can successfully close a session (e.g. in a close vs. close race) and that
 * (ii) no other thread is accessing the memory associated with this session while the segment is being
 * closed. To ensure the former condition, a CAS is performed on the liveness bit. Ensuring the latter
 * is trickier, and require a complex synchronization protocol (see {@link jdk.internal.misc.ScopedMemoryAccess}).
 * Since it is the responsibility of the closing thread to make sure that no concurrent access is possible,
 * checking the liveness bit upon access can be performed in plain mode, as in the confined case.
 */
sealed class SharedSession extends MemorySessionImpl permits ImplicitSession {

    private static final ScopedMemoryAccess SCOPED_MEMORY_ACCESS = ScopedMemoryAccess.getScopedMemoryAccess();

    private static final int CLOSED_ACQUIRE_COUNT = -1;

    SharedSession() {
        super(null, new SharedResourceList());
    }

    @Override
    @ForceInline
    public void acquire0() {
        int value;
        do {
            value = (int) ACQUIRE_COUNT.getVolatile(this);
            if (value < 0) {
                //segment is not open!
                throw sharedSessionAlreadyClosed();
            } else if (value == MAX_FORKS) {
                //overflow
                throw tooManyAcquires();
            }
        } while (!ACQUIRE_COUNT.compareAndSet(this, value, value + 1));
    }

    @Override
    @ForceInline
    public void release0() {
        int value;
        do {
            value = (int) ACQUIRE_COUNT.getVolatile(this);
            if (value <= 0) {
                //cannot get here - we can't close segment twice
                throw sharedSessionAlreadyClosed();
            }
        } while (!ACQUIRE_COUNT.compareAndSet(this, value, value - 1));
    }

    void justClose() {
        int acquireCount = (int) ACQUIRE_COUNT.compareAndExchange(this, 0, CLOSED_ACQUIRE_COUNT);
        if (acquireCount < 0) {
            throw sharedSessionAlreadyClosed();
        } else if (acquireCount > 0) {
            throw alreadyAcquired(acquireCount);
        }

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
