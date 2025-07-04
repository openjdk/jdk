/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.vm;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Represents a snapshot of information about a Thread.
 */
class ThreadSnapshot {
    private static final StackTraceElement[] EMPTY_STACK = new StackTraceElement[0];
    private static final ThreadLock[] EMPTY_LOCKS = new ThreadLock[0];

    // filled by VM
    private String name;
    private int threadStatus;
    private Thread carrierThread;
    private StackTraceElement[] stackTrace;
    // owned monitors
    private ThreadLock[] locks;
    // an object the thread is blocked/waiting on, converted to ThreadBlocker by ThreadSnapshot.of()
    private int blockerTypeOrdinal;
    private Object blockerObject;

    // set by ThreadSnapshot.of()
    private ThreadBlocker blocker;

    private ThreadSnapshot() {}

    /**
     * Take a snapshot of a Thread to get all information about the thread.
     * Return null if a ThreadSnapshot is not created, for example if the
     * thread has terminated.
     * @throws UnsupportedOperationException if not supported by VM
     */
    static ThreadSnapshot of(Thread thread) {
        ThreadSnapshot snapshot = create(thread);
        if (snapshot == null) {
            return null; // thread terminated
        }
        if (snapshot.stackTrace == null) {
            snapshot.stackTrace = EMPTY_STACK;
        }
        if (snapshot.locks != null) {
            Arrays.stream(snapshot.locks).forEach(ThreadLock::finishInit);
        } else {
            snapshot.locks = EMPTY_LOCKS;
        }
        if (snapshot.blockerObject != null) {
            snapshot.blocker = new ThreadBlocker(snapshot.blockerTypeOrdinal, snapshot.blockerObject);
            snapshot.blockerObject = null; // release
        }
        return snapshot;
    }

    /**
     * Returns the thread name.
     */
    String threadName() {
        return name;
    }

    /**
     * Returns the thread state.
     */
    Thread.State threadState() {
        return jdk.internal.misc.VM.toThreadState(threadStatus);
    }

    /**
     * Returns the thread stack trace.
     */
    StackTraceElement[] stackTrace() {
        return stackTrace;
    }

    /**
     * Returns the thread's parkBlocker.
     */
    Object parkBlocker() {
        return getBlocker(BlockerLockType.PARK_BLOCKER);
    }

    /**
     * Returns the object that the thread is blocked on.
     * @throws IllegalStateException if not in the blocked state
     */
    Object blockedOn() {
        if (threadState() != Thread.State.BLOCKED) {
            throw new IllegalStateException();
        }
        return getBlocker(BlockerLockType.WAITING_TO_LOCK);
    }

    /**
     * Returns the object that the thread is waiting on.
     * @throws IllegalStateException if not in the waiting state
     */
    Object waitingOn() {
        if (threadState() != Thread.State.WAITING
                && threadState() != Thread.State.TIMED_WAITING) {
            throw new IllegalStateException();
        }
        return getBlocker(BlockerLockType.WAITING_ON);
    }

    private Object getBlocker(BlockerLockType type) {
        return (blocker != null && blocker.type == type) ? blocker.obj : null;
    }

    /**
     * Returns true if the thread owns any object monitors.
     */
    boolean ownsMonitors() {
        return locks.length > 0;
    }

    /**
     * Returns the objects that the thread locked at the given depth. The stream
     * will contain a null element for a monitor that has been eliminated.
     */
    Stream<Object> ownedMonitorsAt(int depth) {
        return Arrays.stream(locks)
                .filter(lock -> lock.depth() == depth)
                .map(lock -> (lock.type == OwnedLockType.LOCKED)
                        ? lock.lockObject()
                        : /*eliminated*/ null);
    }

    /**
     * If the thread is a mounted virtual thread then return its carrier.
     */
    Thread carrierThread() {
        return carrierThread;
    }

    /**
     * Represents information about a locking operation.
     */
    private enum OwnedLockType {
        LOCKED,
        // Lock object is a class of the eliminated monitor
        ELIMINATED,
    }

    private enum BlockerLockType {
        // Park blocker
        PARK_BLOCKER,
        WAITING_TO_LOCK,
        // Object.wait()
        WAITING_ON,
    }

    /**
     * Represents a locking operation of a thread at a specific stack depth.
     */
    private static class ThreadLock {
        private static final OwnedLockType[] lockTypeValues = OwnedLockType.values(); // cache

        // set by the VM
        private int depth;
        private int typeOrdinal;
        private Object obj;

        // set by ThreadLock.of()
        private OwnedLockType type;

        private ThreadLock() {}

        void finishInit() {
            type = lockTypeValues[typeOrdinal];
        }

        int depth() {
            return depth;
        }

        OwnedLockType type() {
            return type;
        }

        Object lockObject() {
            if (type == OwnedLockType.ELIMINATED) {
                // we have no lock object, lock contains lock class
                return null;
            }
            return obj;
        }
    }

    private record ThreadBlocker(BlockerLockType type, Object obj) {
        private static final BlockerLockType[] lockTypeValues = BlockerLockType.values(); // cache

        ThreadBlocker(int typeOrdinal, Object obj) {
            this(lockTypeValues[typeOrdinal], obj);
        }
    }

    private static native ThreadSnapshot create(Thread thread);
}
