/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.periodic;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import jdk.jfr.internal.JVM;
import jdk.jfr.internal.PlatformEventType;

/**
 * Task for periodic events defined in the JVM.
 * <p>
 * This class guarantees that only one event can execute in native at a time.
 */
final class JVMEventTask extends EventTask {
    // java.util.concurrent lock is used to avoid JavaMonitorBlocked event from
    // synchronized block.
    private static final Lock lock = new ReentrantLock();

    public JVMEventTask(PlatformEventType eventType) {
        super(eventType, new LookupKey(eventType));
        if (!eventType.isJVM()) {
            throw new InternalError("Must be a JVM event");
        }
    }

    @Override
    public void execute(long timestamp, PeriodicType periodicType) {
        try {
            lock.lock();
            JVM.getJVM().emitEvent(getEventType().getId(), timestamp, periodicType.ordinal());
        } finally {
            lock.unlock();
        }
    }
}
