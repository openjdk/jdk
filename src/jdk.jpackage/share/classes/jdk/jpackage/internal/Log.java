/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import jdk.jpackage.internal.log.ProgressLogger;
import jdk.jpackage.internal.log.ResourceLogger;
import jdk.jpackage.internal.log.StandardLogger;
import jdk.jpackage.internal.log.TraceLogger;

/**
 * Log
 *
 * General purpose logging mechanism.
 */
final class Log {

    static void trace(String format, Object... args) {
        tracer().trace(format, args);
    }

    static void trace(Throwable t, String format, Object... args) {
        tracer().trace(t, format, args);
    }

    static void trace(Throwable t) {
        tracer().trace(t);
    }

    static void useResource(String localizedMsg) {
        resourceLogger().useResource(localizedMsg);
    }

    static void progress(String localizedMsg) {
        progressLogger().progress(localizedMsg);
    }

    static void progressWarning(Exception cause) {
        progressLogger().progressWarning(cause);
    }

    static void progressWarning(Exception cause, String localizedMsg) {
        progressLogger().progressWarning(cause, localizedMsg);
    }

    static void progressWarning(String localizedMsg) {
        progressLogger().progressWarning(localizedMsg);
    }

    private static TraceLogger tracer() {
        return Globals.instance().logger(StandardLogger.TRACE_LOGGER);
    }

    private static ProgressLogger progressLogger() {
        return Globals.instance().logger(StandardLogger.PROGRESS_LOGGER);
    }

    private static ResourceLogger resourceLogger() {
        return Globals.instance().logger(StandardLogger.RESOURCE_LOGGER);
    }
}
