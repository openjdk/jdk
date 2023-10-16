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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.PlatformEventType;

/**
 * Class that holds periodic tasks.
 * <p>
 * This class is thread safe.
 */
final class TaskRepository {
    // Keeps periodic tasks in the order they were added by the user
    private final Map<LookupKey, EventTask> lookup = new LinkedHashMap<>();

    // An immutable copy that can be used to iterate over tasks.
    private List<EventTask> cache;

    public synchronized List<EventTask> getTasks() {
        if (cache == null) {
            cache = List.copyOf(lookup.values());
        }
        return cache;
    }

    public synchronized boolean removeTask(Runnable r) {
        EventTask pt = lookup.remove(new LookupKey(r));
        if (pt != null) {
            var eventType = pt.getEventType();
            // Invokes PeriodicEvents.setChanged()
            eventType.setEventHook(false);
            logTask("Removed", eventType);
            cache = null;
            return true;
        }
        return false;
    }

    public synchronized void add(EventTask task) {
        if (lookup.containsKey(task.getLookupKey())) {
            throw new IllegalArgumentException("Hook has already been added");
        }
        lookup.put(task.getLookupKey(), task);
        var eventType = task.getEventType();
        // Invokes PeriodicEvents.setChanged()
        eventType.setEventHook(true);
        logTask("Added", eventType);
        cache = null;
    }

    private void logTask(String action, PlatformEventType type) {
        if (type.isSystem()) {
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.INFO, action + " periodic task for " + type.getLogName());
        } else {
            Logger.log(LogTag.JFR, LogLevel.INFO, action + " periodic task for " + type.getLogName());
        }
    }
}
