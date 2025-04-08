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
import java.util.HexFormat;
import java.util.List;

public class ThreadSnapshot {

    private String name;
    private int threadStatus;
    private StackTraceElement[] ste;
    private ThreadLock[] locks;

    private static final StackTraceElement[] EMPTY_STACK = new StackTraceElement[0];
    private static final ThreadLock[] EMPTY_LOCKS = new ThreadLock[0];

    private static native ThreadSnapshot create0(Thread thread, boolean withLocks);

    public static ThreadSnapshot create(Thread thread) {
        ThreadSnapshot snapshot = create0(thread, true);

        if (snapshot.ste == null) {
            snapshot.ste = EMPTY_STACK;
        }
        if (snapshot.locks == null) {
            snapshot.locks = EMPTY_LOCKS;
        }
        return snapshot;
    }

//    private ThreadSnapshot() {    }

    private ThreadSnapshot(StackTraceElement[] ste, ThreadLock[] locks, String name, int threadStatus) {
        this.ste = ste;
        this.locks = locks;
        this.name = name;
        this.threadStatus = threadStatus;
    }

    public String getName() {
        return name;
    }

    public Thread.State getState() {
        return jdk.internal.misc.VM.toThreadState(threadStatus);
    }

    List<StackTraceElement>    getStackTrace() {
        return Arrays.asList(ste);
    }

    List<ThreadLock> getLocks(int depth) {
        return Arrays.stream(locks)
            .filter(lock -> lock.depth == depth)
            .toList();
    }

    List<ThreadLock> getLocksFor(StackTraceElement element) {
        int depth  = getStackTrace().indexOf(element);
        if (depth < 0) {
            throw new IllegalArgumentException();
        }
        return getLocks(depth);
    }


    public static class ThreadLock {
        private static final int PARKING_TO_WAIT = 1;
        private static final int ELEMINATED_SCALAR_REPLACED = 2;
        private static final int ELEMINATED_MONITOR = 3;
        private static final int LOCKED = 4;
        private static final int WAITING_TO_LOCK = 5;
        private static final int WAITING_ON = 6;
        private static final int WAITING_TO_RELOCK = 7;

        public final int depth;
        public final int lockType;
        public final Object lock;

        private ThreadLock(int depth, int type, Object lock) {
            this.depth = depth;
            this.lockType = type;
            this.lock = lock;
        }

        public String toString() {
            switch (lockType) {
            case PARKING_TO_WAIT:
                return "parking to wait for " + lockString(lock);
            case ELEMINATED_SCALAR_REPLACED:
                // lock is the klass
                return "eliminated, owner is scalar replaced (" + ((Class)lock).getName() + ")";
            case ELEMINATED_MONITOR:
                return "eliminated " + lockString(lock);
            case LOCKED:
                return "locked " + lockString(lock);
            case WAITING_TO_LOCK:
                return "waiting to lock  " + lockString(lock);
            case WAITING_ON:
                // lock is null if there is no reference
                return "waiting on  " + lockString(lock);
            case WAITING_TO_RELOCK:
                return "waiting to re-lock in wait " + lockString(lock);
            }
            return "Unknown lock (type " + lockType + "), lock=" + lockString(lock);
        }

        public String lockString(Object lock) {
            if (lock == null) {
                return "<no object reference available>";
            }
            // TODO: need to generate unique object id instead of hash
            long id = lock.hashCode();
            return "0x" + HexFormat.of().toHexDigits(id, 16)
                    + " (" + lock.getClass().getName() + ")";
        }
    }
}
