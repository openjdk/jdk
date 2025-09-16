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
package jdk.jfr.tracing;

import jdk.jfr.events.MethodTimingEvent;
import jdk.jfr.events.MethodTraceEvent;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.tracing.PlatformTracer;

/**
 * This class serves as the frontend for method tracing capabilities. The
 * jdk.jfr.tracing package is exported to all modules when the first method
 * tracing filter is applied.
 * <p>
 * A malicious user could craft bytecode that invoke these methods with an
 * invalid method ID, resulting in an event where the method field is
 * incorrect or {@code null}. This is considered acceptable.
 */
public final class MethodTracer {

    private MethodTracer() {
    }

    public static long timestamp() {
        return JVM.counterTime();
    }

    public static void traceObjectInit(long startTime, long methodId) {
        long endTime = JVM.counterTime();
        long duration = endTime - startTime;
        if (MethodTraceEvent.enabled() && JVM.getEventWriter() != null) {
            MethodTraceEvent.commit(startTime, duration, methodId);
        }
    }

    public static void timingObjectInit(long startTime, long methodId) {
        long endTime = JVM.counterTime();
        long duration = endTime - startTime;
        if (MethodTimingEvent.enabled()) {
            PlatformTracer.addObjectTiming(duration);
        }
    }

    public static void traceTimingObjectInit(long startTime, long methodId) {
        long endTime = JVM.counterTime();
        long duration = endTime - startTime;
        if (MethodTraceEvent.enabled() && JVM.getEventWriter() != null) {
            MethodTraceEvent.commit(startTime, duration, methodId);
        }
        if (MethodTimingEvent.enabled()) {
            PlatformTracer.addObjectTiming(duration);
        }
    }

    public static void trace(long startTime, long methodId) {
        long endTime = JVM.counterTime();
        long duration = endTime - startTime;
        if (MethodTraceEvent.enabled()) {
            MethodTraceEvent.commit(startTime, duration, methodId);
        }
    }

    public static void timing(long startTime, long methodId) {
        long endTime = JVM.counterTime();
        long duration = endTime - startTime;
        if (MethodTimingEvent.enabled()) {
            PlatformTracer.addTiming(methodId, duration);
        }
    }

    public static void traceTiming(long startTime, long methodId) {
        long endTime = JVM.counterTime();
        long duration = endTime - startTime;
        if (MethodTimingEvent.enabled()) {
            PlatformTracer.addTiming(methodId, duration);
        }
        if (MethodTraceEvent.enabled()) {
            MethodTraceEvent.commit(startTime, duration, methodId);
        }
    }
}
