/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.misc;

import java.util.concurrent.locks.ReentrantLock;

/**
 * A reentrant mutual exclusion lock for internal use. The lock does not
 * implement {@link java.util.concurrent.locks.Lock} or extend {@link
 * java.util.concurrent.locks.ReentrantLock} so that it can be distinguished
 * from lock objects accessible to subclasses of {@link java.io.Reader} and
 * {@link java.io.Writer} (it is possible to create a Reader that uses a
 * lock object of type ReentrantLock for example).
 */
public class InternalLock {
    private static final boolean CAN_USE_INTERNAL_LOCK;
    static {
        String s = System.getProperty("jdk.io.useMonitors");
        if (s != null && (s.isEmpty() || s.equals("true"))) {
            CAN_USE_INTERNAL_LOCK = false;
        } else {
            CAN_USE_INTERNAL_LOCK = true;
        }
    }

    private final ReentrantLock lock;

    private InternalLock() {
        this.lock = new ReentrantLock();
    }

    /**
     * Returns a new InternalLock or null.
     */
    public static InternalLock newLockOrNull() {
        return (CAN_USE_INTERNAL_LOCK) ? new InternalLock() : null;
    }

    /**
     * Returns a new InternalLock or the given object.
     */
    public static Object newLockOr(Object obj) {
        return (CAN_USE_INTERNAL_LOCK) ? new InternalLock() : obj;
    }

    public boolean tryLock() {
        return lock.tryLock();
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    public boolean isHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }
}
