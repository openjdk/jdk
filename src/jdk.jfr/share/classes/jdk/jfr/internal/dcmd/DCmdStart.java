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
package jdk.jfr.internal.dcmd;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.SecuritySupport.SafePath;
import jdk.jfr.internal.Type;
import jdk.jfr.internal.Utils;
import jdk.jfr.internal.jfc.JFC;

/**
 * JFR.start
 *
 */
//Instantiated by native
final class DCmdStart extends AbstractDCmd {

    /**
     * Execute JFR.start.
     *
     * @param name optional name that can be used to identify recording.
     * @param configurations names of settings files to use, i.e. "default" or
     *        "default.jfc".
     * @param delay delay before recording is started, in nanoseconds. Must be
     *        at least 1 second.
     * @param duration duration of the recording, in nanoseconds. Must be at
     *        least 1 second.
     * @param disk if recording should be persisted to disk
     * @param path file path where recording data should be written
     * @param maxAge how long recording data should be kept in the disk
     *        repository, or <code>0</code> if no limit should be set.
     *
     * @param maxSize the minimum amount data to keep in the disk repository
     *        before it is discarded, or <code>0</code> if no limit should be
     *        set.
     *
     * @param dumpOnExit if recording should dump on exit
     *
     * @return result output
     *
     * @throws DCmdException if recording could not be started
     */
    @SuppressWarnings("resource")
    public String execute(String name, String[] configurations, Long delay, Long duration, Boolean disk, String path, Long maxAge, Long maxSize, Boolean dumpOnExit, Boolean pathToGcRoots) throws DCmdException {
        if (name != null) {
            try {
                Integer.parseInt(name);
                throw new DCmdException("Name of recording can't be numeric");
            } catch (NumberFormatException nfe) {
                // ok, can't be mixed up with name
            }
        }

        if (duration == null && Boolean.FALSE.equals(dumpOnExit) && path != null) {
            throw new DCmdException("Filename can only be set for a time bound recording or if dumponexit=true. Set duration/dumponexit or omit filename.");
        }
        if (dumpOnExit == null && path != null) {
            dumpOnExit = Boolean.TRUE;
        }

        Map<String, String> s = new HashMap<>();

        if (configurations == null || configurations.length == 0) {
            configurations = new String[] { "default" };
        }

        for (String configName : configurations) {
            try {
                s.putAll(JFC.createKnown(configName).getSettings());
            } catch (IOException | ParseException e) {
                throw new DCmdException("Could not parse setting " + configurations[0], e);
            }
        }

        Utils.updateSettingPathToGcRoots(s, pathToGcRoots);

        if (duration != null) {
            if (duration < 1000L * 1000L * 1000L) {
                // to avoid typo, duration below 1s makes no sense
                throw new DCmdException("Could not start recording, duration must be at least 1 second.");
            }
        }

        if (delay != null) {
            if (delay < 1000L * 1000L * 1000) {
                // to avoid typo, delay shorter than 1s makes no sense.
                throw new DCmdException("Could not start recording, delay must be at least 1 second.");
            }
        }

        if (!FlightRecorder.isInitialized() && delay == null) {
            initializeWithForcedInstrumentation(s);
        }

        Recording recording = new Recording();
        if (name != null) {
            recording.setName(name);
        }

        if (disk != null) {
            recording.setToDisk(disk.booleanValue());
        }
        recording.setSettings(s);

        if (path != null) {
            try {
                recording.setDestination(Paths.get(path));
            } catch (IOException | InvalidPathException e) {
                recording.close();
                throw new DCmdException("Could not start recording, not able to write to file %s. %s ", path, e.getMessage());
            }
        }

        if (maxAge != null) {
            recording.setMaxAge(Duration.ofNanos(maxAge));
        }

        if (maxSize != null) {
            recording.setMaxSize(maxSize);
        }

        if (duration != null) {
            recording.setDuration(Duration.ofNanos(duration));
        }

        if (dumpOnExit != null) {
            recording.setDumpOnExit(dumpOnExit);
        }

        if (delay != null) {
            Duration dDelay = Duration.ofNanos(delay);
            recording.scheduleStart(dDelay);
            print("Recording " + recording.getId() + " scheduled to start in ");
            printTimespan(dDelay, " ");
            print(".");
        } else {
            recording.start();
            print("Started recording " + recording.getId() + ".");
        }

        if (recording.isToDisk() && duration == null && maxAge == null && maxSize == null) {
            print(" No limit specified, using maxsize=250MB as default.");
            recording.setMaxSize(250*1024L*1024L);
        }

        if (path != null && duration != null) {
            println(" The result will be written to:");
            println();
            printPath(new SafePath(path));
        } else {
            println();
            println();
            String cmd = duration == null ? "dump" : "stop";
            String fileOption = path == null ? "filename=FILEPATH " : "";
            String recordingspecifier = "name=" + recording.getId();
            // if user supplied a name, use it.
            if (name != null) {
                recordingspecifier = "name=" + quoteIfNeeded(name);
            }
            print("Use JFR." + cmd + " " + recordingspecifier + " " + fileOption + "to copy recording data to file.");
            println();
        }
        return getResult();
    }

    // Instruments JDK-events on class load to reduce startup time
    private void initializeWithForcedInstrumentation(Map<String, String> settings) {
        if (!hasJDKEvents(settings)) {
            return;
        }
        JVM jvm = JVM.getJVM();
        try {
            jvm.setForceInstrumentation(true);
            FlightRecorder.getFlightRecorder();
        } finally {
            jvm.setForceInstrumentation(false);
        }
    }

    private boolean hasJDKEvents(Map<String, String> settings) {
        String[] eventNames = new String[7];
        eventNames[0] = "FileRead";
        eventNames[1] = "FileWrite";
        eventNames[2] = "SocketRead";
        eventNames[3] = "SocketWrite";
        eventNames[4] = "JavaErrorThrow";
        eventNames[5] = "JavaExceptionThrow";
        eventNames[6] = "FileForce";
        for (String eventName : eventNames) {
            if ("true".equals(settings.get(Type.EVENT_NAME_PREFIX + eventName + "#enabled"))) {
                return true;
            }
        }
        return false;
    }
}
