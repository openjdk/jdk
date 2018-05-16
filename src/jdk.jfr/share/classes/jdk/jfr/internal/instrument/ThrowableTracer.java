/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jfr.events.ErrorThrownEvent;
import jdk.jfr.events.ExceptionThrownEvent;

public final class ThrowableTracer {

    private static AtomicLong numThrowables = new AtomicLong(0);

    public static void traceError(Error e, String message) {
        if (e instanceof OutOfMemoryError) {
            return;
        }
        ErrorThrownEvent errorEvent = new ErrorThrownEvent();
        errorEvent.message = message;
        errorEvent.thrownClass = e.getClass();
        errorEvent.commit();

        ExceptionThrownEvent exceptionEvent = new ExceptionThrownEvent();
        exceptionEvent.message = message;
        exceptionEvent.thrownClass = e.getClass();
        exceptionEvent.commit();
        numThrowables.incrementAndGet();
    }

    public static void traceThrowable(Throwable t, String message) {
        ExceptionThrownEvent event = new ExceptionThrownEvent();
        event.message = message;
        event.thrownClass = t.getClass();
        event.commit();
        numThrowables.incrementAndGet();
    }

    public static long numThrowables() {
        return numThrowables.get();
    }
}
