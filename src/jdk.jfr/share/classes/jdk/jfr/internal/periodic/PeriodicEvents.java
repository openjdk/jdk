/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.security.AccessControlContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import jdk.internal.event.Event;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.PlatformEventType;

/**
 * Class that runs and schedules tasks for periodic events.
 * <p>
 * Events can run at the beginning of a chunk rotation, at the end of a chunk
 * rotation or at a periodic interval.
 * <p>
 * Events with the same period runs in the same order as users added them.
 * Temporarily disabling events don't impact the order of execution.
 * <p>
 * A best effort is made to run events with different periods at the same time.
 * For example, an event that runs every two seconds executes half of the time
 * with the events that run every second.
 */
public final class PeriodicEvents {
    private static final TaskRepository taskRepository = new TaskRepository();
    private static final BatchManager batchManager = new BatchManager();
    private static final FlushTask flushTask = new FlushTask();
    private static final AtomicLong settingsIteration = new AtomicLong();

    // State only to be read and modified by periodic task thread
    private static long lastTimeMillis;

    public static void addJDKEvent(Class<? extends Event> eventClass, Runnable runnable) {
        taskRepository.add(new JDKEventTask(eventClass, runnable));
    }

    public static void addJVMEvent(PlatformEventType eventType) {
        taskRepository.add(new JVMEventTask(eventType));
    }

    public static void addUserEvent(@SuppressWarnings("removal") AccessControlContext acc, Class<? extends Event> eventClass, Runnable runnable) {
        taskRepository.add(new UserEventTask(acc, eventClass, runnable));
    }

    public static boolean removeEvent(Runnable runnable) {
        return taskRepository.removeTask(runnable);
    }

    public static void doChunkBegin() {
        long timestamp = JVM.counterTime();
        for (EventTask task : taskRepository.getTasks()) {
            var eventType = task.getEventType();
            if (eventType.isEnabled() && eventType.isBeginChunk()) {
                task.run(timestamp, PeriodicType.BEGIN_CHUNK);
            }
        }
    }

    public static void doChunkEnd() {
        long timestamp = JVM.counterTime();
        for (EventTask task : taskRepository.getTasks()) {
            var eventType = task.getEventType();
            if (eventType.isEnabled() && eventType.isEndChunk()) {
                task.run(timestamp, PeriodicType.END_CHUNK);
            }
        }
    }

    // Only to be called from periodic task thread
    public static long doPeriodic() {
        long eventTimestamp = JVM.counterTime();
        // Code copied from prior native implementation
        long last = lastTimeMillis;
        // The interval for periodic events is typically at least 1 s, so
        // System.currentTimeMillis() is sufficient. JVM.counterTime() lacks
        // unit and has in the past been more unreliable.
        long now = System.currentTimeMillis();
        long min = 0;
        long delta = 0;

        if (last == 0) {
            last = now;
        }

        // time from then to now
        delta = now - last;
        if (delta < 0) {
            // to handle time adjustments
            // for example Daylight Savings
            lastTimeMillis = now;
            return 0;
        }
        long iteration = settingsIteration.get();
        if (iteration > batchManager.getIteration()) {
            List<PeriodicTask> tasks = new ArrayList<>();
            tasks.addAll(taskRepository.getTasks());
            tasks.add(flushTask);
            batchManager.refresh(iteration, tasks);

        }
        boolean flush = false;
        Logger.log(LogTag.JFR_PERIODIC, LogLevel.DEBUG,"Periodic work started");
        for (Batch batch : batchManager.getBatches()) {
            long left = 0;
            long r_period = batch.getPeriod();
            long r_delta = batch.getDelta();

            // add time elapsed.
            r_delta += delta;

            // above threshold?
            if (r_delta >= r_period) {
                // Bug 9000556 - don't try to compensate
                // for wait > period
                r_delta = 0;
                for (PeriodicTask task : batch.getTasks()) {
                    task.tick();
                    if (task.shouldRun()) {
                        if (task instanceof FlushTask) {
                            flush = true;
                        } else {
                            task.run(eventTimestamp, PeriodicType.INTERVAL);
                        }
                    }
                }
            }

            // calculate time left
            left = (r_period - r_delta);

            /*
             * nothing outside checks that a period is >= 0, so left can end up negative
             * here. ex. (r_period =(-1)) - (r_delta = 0) if it is, handle it.
             */
            if (left < 0) {
                left = 0;
            }

            // assign delta back
            batch.setDelta(r_delta);

            if (min == 0 || left < min) {
                min = left;
            }
        }
        if (flush) {
            flushTask.run(eventTimestamp, PeriodicType.INTERVAL);
        }
        Logger.log(LogTag.JFR_PERIODIC, LogLevel.DEBUG,"Periodic work ended");
        lastTimeMillis = now;
        return min;
    }

    /**
     * Marks that a change has happened to a periodic event.
     * <p>
     * This method should be invoked if a periodic event has:
     * <ul>
     * <li>been added</li>
     * <li>been removed</li>
     * <li>been enabled
     * <li>been disabled</li>
     * <li>changed period</li>
     * </ul>
     * <p>
     * The periodic task thread will poll the changed state at least once every
     * second to see if a change has occurred. if that's the case, it will refresh
     * periodic tasks that need to be run.
     */
    public static void setChanged() {
        settingsIteration.incrementAndGet();
    }

    public static void setFlushInterval(long millis) {
        flushTask.setInterval(millis);
    }
}
