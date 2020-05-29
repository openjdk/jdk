/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

package jdk.internal.foreign;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * This class manages the temporal bounds associated with a memory segment. A scope has a liveness bit, which is updated
 * when the scope is closed (this operation is triggered by {@link AbstractMemorySegmentImpl#close()}). Furthermore, a scope is
 * associated with an <em>atomic</em> counter which can be incremented (upon calling the {@link #acquire()} method),
 * and is decremented (when a previously acquired segment is later closed).
 */
public final class MemoryScope {

    //reference to keep hold onto
    final Object ref;

    int activeCount = UNACQUIRED;

    final static VarHandle COUNT_HANDLE;

    static {
        try {
            COUNT_HANDLE = MethodHandles.lookup().findVarHandle(MemoryScope.class, "activeCount", int.class);
        } catch (Throwable ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    final static int UNACQUIRED = 0;
    final static int CLOSED = -1;
    final static int MAX_ACQUIRE = Integer.MAX_VALUE;

    final Runnable cleanupAction;

    final static MemoryScope GLOBAL = new MemoryScope(null, null);

    public MemoryScope(Object ref, Runnable cleanupAction) {
        this.ref = ref;
        this.cleanupAction = cleanupAction;
    }

    /**
     * This method performs a full, thread-safe liveness check; can be used outside confinement thread.
     */
    final boolean isAliveThreadSafe() {
        return ((int)COUNT_HANDLE.getVolatile(this)) != CLOSED;
    }

    /**
     * This method performs a quick liveness check; must be called from the confinement thread.
     */
    final void checkAliveConfined() {
        if (activeCount == CLOSED) {
            throw new IllegalStateException("Segment is not alive");
        }
    }

    MemoryScope acquire() {
        int value;
        do {
            value = (int)COUNT_HANDLE.getVolatile(this);
            if (value == CLOSED) {
                //segment is not alive!
                throw new IllegalStateException("Segment is not alive");
            } else if (value == MAX_ACQUIRE) {
                //overflow
                throw new IllegalStateException("Segment acquire limit exceeded");
            }
        } while (!COUNT_HANDLE.compareAndSet(this, value, value + 1));
        return new MemoryScope(ref, this::release);
    }

    private void release() {
        int value;
        do {
            value = (int)COUNT_HANDLE.getVolatile(this);
            if (value <= UNACQUIRED) {
                //cannot get here - we can't close segment twice
                throw new IllegalStateException();
            }
        } while (!COUNT_HANDLE.compareAndSet(this, value, value - 1));
    }

    void close(boolean doCleanup) {
        if (!COUNT_HANDLE.compareAndSet(this, UNACQUIRED, CLOSED)) {
            //first check if already closed...
            checkAliveConfined();
            //...if not, then we have acquired views that are still active
            throw new IllegalStateException("Cannot close a segment that has active acquired views");
        }
        if (doCleanup && cleanupAction != null) {
            cleanupAction.run();
        }
    }

    MemoryScope dup() {
        close(false);
        return new MemoryScope(ref, cleanupAction);
    }
}
