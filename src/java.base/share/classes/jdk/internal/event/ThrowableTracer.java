/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.event;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Helper class for exception events.
 */
public final class ThrowableTracer {

    private static final AtomicLong numThrowables = new AtomicLong();

    public static void traceError(Class<?> clazz, String message) {
        if (OutOfMemoryError.class.isAssignableFrom(clazz)) {
            return;
        }

        if (ErrorThrownEvent.enabled()) {
            long timestamp = ErrorThrownEvent.timestamp();
            ErrorThrownEvent.commit(timestamp, message, clazz);
        }
        if (ExceptionThrownEvent.enabled()) {
            long timestamp = ExceptionThrownEvent.timestamp();
            ExceptionThrownEvent.commit(timestamp, message, clazz);
        }
        numThrowables.incrementAndGet();
    }

    public static void traceThrowable(Class<?> clazz, String message) {
        if (ExceptionThrownEvent.enabled()) {
            long timestamp = ExceptionThrownEvent.timestamp();
            ExceptionThrownEvent.commit(timestamp, message, clazz);
        }
        numThrowables.incrementAndGet();
    }

    public static void emitStatistics() {
        long timestamp = ExceptionStatisticsEvent.timestamp();
        ExceptionStatisticsEvent.commit(timestamp, numThrowables.get());
    }
}
