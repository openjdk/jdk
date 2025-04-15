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

    List<StackTraceElement> getStackTrace() {
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

    List<ThreadLock> getOwnableSynchronizers() {
        return getLocks(-1);
    }

    public static enum LockType {
        // Park blocker
        PARKING_TO_WAIT,
        // Lock object is a class of the eliminated monitor
        ELEMINATED_SCALAR_REPLACED,
        ELEMINATED_MONITOR,
        LOCKED,
        WAITING_TO_LOCK,
        WAITING_ON,
        WAITING_TO_RELOCK,
        // No corresponding stack frame, depth is always == -1
        OWNABLE_SYNCHRONIZER
    }

    public static record ThreadLock(int depth, LockType type, Object obj) {
        private static final LockType[] lockTypeValues = LockType.values(); // cache
        private ThreadLock(int depth, int typeOrdinal, Object obj) {
            this(depth, lockTypeValues[typeOrdinal], obj);
        }

        public Object lockObject() {
            if (type == LockType.ELEMINATED_SCALAR_REPLACED) {
                // we have no lock object, lock contains lock class
                return null;
            }
            return obj;
        }
        public Class<?> lockClass() {
            if (type == LockType.ELEMINATED_SCALAR_REPLACED) {
                return (Class)obj;
            }
            return obj == null ? null : obj.getClass();
        }
    }
}
