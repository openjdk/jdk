/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.management;

import java.io.Closeable;
import java.io.IOException;

/**
 * Purpose of this class is to provide a synchronization point when stopping a
 * recording. Without it, a race can happen where a stream advances beyond the
 * last chunk of the recording.
 *
 * Code that is processing the stream calls check() and Unless the recording is
 * in the process of being stopped, it will just return. On the other hand, if
 * the recording is stopping, the thread waits and when it wakes up an end
 * position should have been set (last chunk position) beyond which the stream
 * processing should not continue.
 */
public final class StreamBarrier implements Closeable {
    private final HiddenWait lock = new HiddenWait();
    private boolean activated = false;
    private boolean used = false;
    private long end = Long.MAX_VALUE;

    // Blocks thread until barrier is deactivated
    public void check() {
        synchronized (lock) {
            while (activated) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void setStreamEnd(long timestamp) {
        synchronized(lock) {
            end = timestamp;
        }
    }

    public long getStreamEnd() {
        synchronized(lock) {
            return end;
        }
    }

    public void activate() {
        synchronized (lock) {
            activated = true;
            used = true;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        synchronized (lock) {
            activated = false;
            lock.notifyAll();
        }
    }

    /**
     * Returns {@code true) if barrier is, or has been, in active state, {@code false) otherwise.
     */
    public boolean used() {
        synchronized (lock) {
            return used;
        }
    }
}
