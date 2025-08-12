/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jfr.internal.JVM;
import jdk.jfr.internal.MetadataRepository;
import jdk.jfr.internal.PlatformRecorder;
import jdk.jfr.internal.PrivateAccess;
import jdk.jfr.internal.util.Utils;

/**
 * Periodic task that flushes event data to disk.
 *<p>
 * The task is run once every second and after all other periodic events.
 * <p>
 * A flush interval of {@code Long.MAX_VALUE} means the event is disabled.
 */
final class FlushTask extends PeriodicTask {
    private volatile long flushInterval = Long.MAX_VALUE;

    public FlushTask() {
        super(new LookupKey(new Object()), "flush task");
    }

    @Override
    public void execute(long timestamp, PeriodicType periodicType) {
        PlatformRecorder recorder = PrivateAccess.getInstance().getPlatformRecorder();
        recorder.flush();
        Utils.notifyFlush();
    }

    @Override
    public boolean isSchedulable() {
        return true;
    }

    @Override
    protected long fetchPeriod() {
        return flushInterval;
    }

    public void setInterval(long millis) {
        // Don't accept shorter interval than 1 s
        long interval = millis < 1000 ? 1000 : millis;
        boolean needsNotify = interval < flushInterval;
        flushInterval = interval;
        PeriodicEvents.setChanged();
        if (needsNotify) {
            synchronized (JVM.CHUNK_ROTATION_MONITOR) {
                JVM.CHUNK_ROTATION_MONITOR.notifyAll();
            }
        }
    }
}
