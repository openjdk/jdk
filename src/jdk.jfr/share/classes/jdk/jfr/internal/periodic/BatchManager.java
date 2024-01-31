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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;

/**
 * Class that groups periodic tasks into batches.
 * <p>
 * This class should only be accessed from the periodic task thread.
 */
final class BatchManager {
    private final List<Batch> batches = new ArrayList<>();
    private long iteration = -1;

    public List<Batch> getBatches() {
        return batches;
    }

    public long getIteration() {
        return iteration;
    }

    public void refresh(long iteration, List<PeriodicTask> tasks) {
        Logger.log(LogTag.JFR_SYSTEM_PERIODIC, LogLevel.DEBUG, "Grouping tasks into batches. Iteration " + iteration);
        groupTasksIntoBatches(tasks);
        this.iteration = iteration;
        logBatches();
    }

    private void groupTasksIntoBatches(List<PeriodicTask> tasks) {
        // Batches are cleared instead of recreated to keep batch delta intact
        for (Batch batch : batches) {
            batch.clear();
        }
        for (PeriodicTask task : activeSortedTasks(tasks)) {
            if (task.isSchedulable()) {
                Batch batch = findBatch(task.getPeriod(), task.getBatch());
                batch.add(task);
            }
        }
        // Remove unused batches
        batches.removeIf(Batch::isEmpty);
    }

    private List<PeriodicTask> activeSortedTasks(List<PeriodicTask> unsorted) {
        // Update with latest periods
        List<PeriodicTask> tasks = new ArrayList<>(unsorted.size());
        for (PeriodicTask task : unsorted) {
            task.updatePeriod();
            if (task.getPeriod() != 0) {
                tasks.add(task);
            }
        }
        // Sort tasks by lowest period
        tasks.sort(Comparator.comparingLong(PeriodicTask::getPeriod));
        return tasks;
    }

    private Batch findBatch(long period, Batch oldBatch) {
        // All events with a period less than 1000 ms
        // get their own unique batch. The rationale for
        // this is to avoid a scenario where a user (mistakenly) specifies
        // period=1ms for an event and then all events end
        // up in that batch. It would work, but 99,9% of the time
        // the iteration would be pointless.
        for (Batch batch : batches) {
            long batchPeriod = batch.getPeriod();
            if ((period >= 1000 && batchPeriod >= 1000 && period % batchPeriod == 0) || (batchPeriod == period)) {
                return batch;
            }
        }
        Batch batch = oldBatch != null ? oldBatch : new Batch(period);
        batches.add(batch);
        return batch;
    }

    private void logBatches() {
        if (!Logger.shouldLog(LogTag.JFR_SYSTEM_PERIODIC, LogLevel.TRACE)) {
            return;
        }
        for (Batch batch : batches) {
            for (PeriodicTask task : batch.getTasks()) {
                logTrace("Batched task [0.." + task.getPeriod() + "] step " + batch.getPeriod() + " " + task.getName());
            }
        }
    }

    private void logTrace(String text) {
       Logger.log(LogTag.JFR_SYSTEM_PERIODIC, LogLevel.DEBUG, text);
    }
}
