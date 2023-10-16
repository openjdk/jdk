/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

package javacserver.server;

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import javacserver.util.RunnableTimerTask;

/**
 * Monitors the javacserver daemon, shutting it down if it recieves no new requests
 * after a certain amount of time.
 */
public class IdleMonitor {
    // Accept 120 seconds of inactivity before quitting.
    private static final int KEEPALIVE = 120;

    private final Consumer<String> onShutdown;
    private final Timer idlenessTimer = new Timer();
    private int outstandingCalls = 0;

    // Class invariant: idlenessTimerTask != null <-> idlenessTimerTask is scheduled
    private TimerTask idlenessTimerTask;

    public IdleMonitor(Consumer<String> onShutdown) {
        this.onShutdown = onShutdown;
        scheduleTimeout();
    }

    public synchronized void startCall() {
        // Was there no outstanding calls before this call?
        if (++outstandingCalls == 1) {
            // Then the timer task must have been scheduled
            if (idlenessTimerTask == null)
                throw new IllegalStateException("Idle timeout already cancelled");
            // Cancel timeout task
            idlenessTimerTask.cancel();
            idlenessTimerTask = null;
        }
    }

    public synchronized void endCall() {
        if (--outstandingCalls == 0) {
            // No more outstanding calls. Schedule timeout.
            scheduleTimeout();
        }
    }

    private void scheduleTimeout() {
        if (idlenessTimerTask != null)
            throw new IllegalStateException("Idle timeout already scheduled");
        idlenessTimerTask = new RunnableTimerTask(() -> {
            Server.restoreServerErrorLog();
            onShutdown.accept("Server has been idle for " + KEEPALIVE + " seconds.");
        });
        idlenessTimer.schedule(idlenessTimerTask, KEEPALIVE * 1000);
    }

    public void shutdown() {
        idlenessTimer.cancel();
    }
}
