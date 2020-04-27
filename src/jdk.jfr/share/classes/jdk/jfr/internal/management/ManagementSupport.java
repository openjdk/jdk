/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.management;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jdk.jfr.EventType;
import jdk.jfr.Recording;
import jdk.jfr.internal.JVMSupport;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.MetadataRepository;
import jdk.jfr.internal.PlatformRecording;
import jdk.jfr.internal.PrivateAccess;
import jdk.jfr.internal.Utils;
import jdk.jfr.internal.WriteableUserPath;
import jdk.jfr.internal.instrument.JDKEvents;

/**
 * The management API in module jdk.management.jfr should be built on top of the
 * public API in jdk.jfr. Before putting more functionality here, consider if it
 * should not be part of the public API, and if not, please provide motivation
 *
 */
public final class ManagementSupport {

    // Purpose of this method is to expose the event types to the
    // FlightRecorderMXBean without instantiating Flight Recorder.
    //
    // This allows:
    //
    // 1) discoverability, so event settings can be exposed without the need to
    // create a new Recording in FlightRecorderMXBean.
    //
    // 2) a graphical JMX client to list all attributes to the user, without
    // loading JFR memory buffers. This is especially important when there is
    // no intent to use Flight Recorder.
    //
    // An alternative design would be to make FlightRecorder#getEventTypes
    // static, but it would the make the API look strange
    //
    public static List<EventType> getEventTypes() {
        // would normally be checked when a Flight Recorder instance is created
        Utils.checkAccessFlightRecorder();
        if (JVMSupport.isNotAvailable()) {
            return new ArrayList<>();
        }
        JDKEvents.initialize(); // make sure JDK events are available
        return Collections.unmodifiableList(MetadataRepository.getInstance().getRegisteredEventTypes());
    }

    // Reuse internal code for parsing a timespan
    public static long parseTimespan(String s) {
        return Utils.parseTimespan(s);
    }

    // Reuse internal code for formatting settings
    public static final String formatTimespan(Duration dValue, String separation) {
        return Utils.formatTimespan(dValue, separation);
    }

    // Reuse internal logging mechanism
    public static void logError(String message) {
        Logger.log(LogTag.JFR, LogLevel.ERROR, message);
    }

    // Get the textual representation when the destination was set, which
    // requires access to jdk.jfr.internal.PlatformRecording
    public static String getDestinationOriginalText(Recording recording) {
        PlatformRecording pr = PrivateAccess.getInstance().getPlatformRecording(recording);
        WriteableUserPath wup = pr.getDestination();
        return wup == null ? null : wup.getOriginalText();
    }

    public static void checkSetDestination(Recording recording, String destination) throws IOException{
        PlatformRecording pr = PrivateAccess.getInstance().getPlatformRecording(recording);
        if(destination != null){
            WriteableUserPath wup = new WriteableUserPath(Paths.get(destination));
            pr.checkSetDestination(wup);
        }
    }
}
