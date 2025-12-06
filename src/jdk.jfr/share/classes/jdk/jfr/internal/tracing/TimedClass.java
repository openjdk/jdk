/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.internal.tracing;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;

import jdk.jfr.events.MethodTimingEvent;

/**
 * Holds timed method for a class. Used when publishing method ids.
 */
public final class TimedClass {
    private static final long MISSING = Long.MIN_VALUE;
    private final ConcurrentHashMap<Long, TimedMethod> methods = new ConcurrentHashMap<>();

    public TimedMethod add(Method method) {
        return methods.computeIfAbsent(method.methodId(), id -> new TimedMethod(method));
    }

    public void remove(Method method) {
        methods.remove(method.methodId());
    }

    public void publish() {
        for (TimedMethod t : methods.values()) {
            t.published().set(true);
            t.method().log("Timing entry published");
        }
    }

    Collection<TimedMethod> methods() {
        return methods.values();
    }

    public void emit(long timestamp) {
        for (var tm : methods.values()) {
            if (tm.published().get()) {
                long methodId = tm.method().methodId();
                long invocations = tm.invocations().get();
                long time = tm.time().get();
                long min = tm.minimum().get();
                long max = tm.maximum().get();
                if (time == 0 || invocations == 0) {
                    // If time is zero, it's a low resolution clock and more invocations are needed.
                    MethodTimingEvent.commit(timestamp, methodId, invocations, MISSING, MISSING, MISSING);
                } else {
                    long average = (time + invocations / 2) / invocations;
                    if (min == Long.MAX_VALUE) {
                        min = average;
                    }
                    if (max == Long.MIN_VALUE) {
                        max = average;
                    }
                    min = Math.min(min, average);
                    max = Math.max(max, average);
                    MethodTimingEvent.commit(timestamp, methodId, invocations, min, average, max);
                }
                tm.method().log("Emitted event");
            }
        }
    }
}
