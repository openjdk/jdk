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

import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;

/**
 * Base class that holds time related information for a periodic task.
 * <p>
 * Class hierarchy for periodic tasks:
 * <pre>
 *               PeriodicTask
 *                /        \
 *               /          \
 *          EventTask    FlushTask
 *           /     \
 *          /       \
 * JVMEventTask   JavaEventTask
 *                /         \
 *               /           \
 *      UserEventTask     JDKEventTask
 * </pre>
 * <p>
 * State modifications should only be done from the periodic task thread.
 */
abstract class PeriodicTask {
    private final LookupKey lookupKey;
    private final String name;
    // State only to be modified by the periodic task thread
    private long counter;
    private long period;
    private Batch batch;

    public PeriodicTask(LookupKey lookupKey, String name) {
        this.lookupKey = lookupKey;
        this.name = name;
    }

    public abstract void execute(long timestamp, PeriodicType periodicType);

    public abstract boolean isSchedulable();

    protected abstract long fetchPeriod();

    public final LookupKey getLookupKey() {
        return lookupKey;
    }

    public final String getName() {
        return name;
    }

    // Only to be called from periodic task thread
    public void setBatch(Batch batch) {
        this.batch = batch;
    }

    // Only to be called from periodic task thread
    public Batch getBatch() {
        return batch;
    }

    // Only to be called from periodic task thread
    public final void tick() {
        long increment = batch.getPeriod();
        if (period != 0) {
            counter = (counter + increment) % period;
            if (Logger.shouldLog(LogTag.JFR_PERIODIC, LogLevel.DEBUG)) {
                boolean trace = Logger.shouldLog(LogTag.JFR_PERIODIC, LogLevel.TRACE);
                boolean run = shouldRun();
                if (trace || run) {
                    logInterval(trace, run ? "Run" : "Skip");
                }
            }
        }
    }

    // Only to be called from periodic task thread
    public final boolean shouldRun() {
        return counter == 0 && period != 0;
    }

    // Only to be called from periodic task thread
    public final void updatePeriod() {
        long p = fetchPeriod();
        // Reset counter if new period
        if (p != period) {
            counter = 0;
            period = p;
            batch = null;
        }
    }

    // Only to be called from periodic task thread
    public final long getPeriod() {
        return period;
    }

    public final void run(long timestamp, PeriodicType periodicType) {
        if (periodicType != PeriodicType.INTERVAL) {
            logChunk(periodicType);
        }
        try {
            execute(timestamp, periodicType);
        } catch (Throwable e) {
            // Prevent malicious user to propagate exception callback in the wrong context
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.WARN, "Exception occurred during execution of " + name);
        }
    }

    private void logChunk(PeriodicType periodicType) {
        if (Logger.shouldLog(LogTag.JFR_PERIODIC, LogLevel.DEBUG)) {
            String action = periodicType == PeriodicType.BEGIN_CHUNK ? "beginChunk" : "endChunk";
            String message = "Run " + action + " " + getName();
            Logger.log(LogTag.JFR_PERIODIC, LogLevel.DEBUG, message);
        }
    }

    private void logInterval(boolean trace, String action) {
        String message = action + " periodic " + getName();
        if (trace) {
            // Makes the counter run on x/x instead of 0/x which looks strange.
            long a =  counter == 0 ? period : counter;
            Logger.log(LogTag.JFR_PERIODIC, LogLevel.TRACE, message + (" " + a + "/" + period));
        } else {
            Logger.log(LogTag.JFR_PERIODIC, LogLevel.DEBUG, message);
        }
    }
}