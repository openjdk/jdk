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
import java.util.List;

/**
 * Class that holds periodic tasks that run at the same time.
 * <p>
 * For example, events with period 1s, 3s and 7s can run when the 1s event run,
 * not every time, but some of the time. An event with period 1.5s would not
 * belong to the same batch since it would need to run between the 1s interval.
 * <p>
 * This class should only be accessed from the periodic task thread.
 */
final class Batch {
    private final List<PeriodicTask> tasks = new ArrayList<>();
    private final long period;
    private long delta;

    public Batch(long period) {
        this.period = period;
    }

    public long getDelta() {
        return delta;
    }

    public void setDelta(long delta) {
        this.delta = delta;
    }

    public long getPeriod() {
        return period;
    }

    public List<PeriodicTask> getTasks() {
        return tasks;
    }

    public void add(PeriodicTask task) {
        task.setIncrement(period);
        tasks.add(task);
    }

    public void clear() {
        tasks.clear();
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }
}