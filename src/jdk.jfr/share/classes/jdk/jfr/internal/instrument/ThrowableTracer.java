/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.instrument;

import java.util.concurrent.atomic.AtomicLong;

import jdk.jfr.events.EventConfigurations;
import jdk.jfr.events.ErrorThrownEvent;
import jdk.jfr.events.ExceptionThrownEvent;
import jdk.jfr.internal.event.EventConfiguration;

public final class ThrowableTracer {

    private static final AtomicLong numThrowables = new AtomicLong();

    public static void traceError(Error e, String message) {
        if (e instanceof OutOfMemoryError) {
            return;
        }
        long timestamp = EventConfiguration.timestamp();

        EventConfiguration eventConfiguration1 = EventConfigurations.ERROR_THROWN;
        if (eventConfiguration1.isEnabled()) {
            ErrorThrownEvent.commit(timestamp, message, e.getClass());
        }
        EventConfiguration eventConfiguration2 = EventConfigurations.EXCEPTION_THROWN;
        if (eventConfiguration2.isEnabled()) {
            ExceptionThrownEvent.commit(timestamp, message, e.getClass());
        }
        numThrowables.incrementAndGet();
    }

    public static void traceThrowable(Throwable t, String message) {
        EventConfiguration eventConfiguration = EventConfigurations.EXCEPTION_THROWN;
        if (eventConfiguration.isEnabled()) {
            long timestamp = EventConfiguration.timestamp();
            ExceptionThrownEvent.commit(timestamp, message, t.getClass());
        }
        numThrowables.incrementAndGet();
    }

    public static long numThrowables() {
        return numThrowables.get();
    }
}
