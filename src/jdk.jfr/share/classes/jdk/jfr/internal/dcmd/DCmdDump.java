/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.PlatformRecorder;
import jdk.jfr.internal.PlatformRecording;
import jdk.jfr.internal.PrivateAccess;
import jdk.jfr.internal.SecuritySupport.SafePath;
import jdk.jfr.internal.Utils;
import jdk.jfr.internal.WriteableUserPath;

/**
 * JFR.dump
 *
 */
// Instantiated by native
final class DCmdDump extends AbstractDCmd {
    /**
     * Execute JFR.dump.
     *
     * @param name name or id of the recording to dump, or {@code null} to dump everything
     *
     * @param filename file path where recording should be written, not null
     * @param maxAge how far back in time to dump, may be null
     * @param maxSize how far back in size to dump data from, may be null
     * @param begin point in time to dump data from, may be null
     * @param end point in time to dump data to, may be null
     * @param pathToGcRoots if Java heap should be swept for reference chains
     *
     * @return result output
     *
     * @throws DCmdException if the dump could not be completed
     */
    public String execute(String name, String filename, Long maxAge, Long maxSize, String begin, String end, Boolean pathToGcRoots) throws DCmdException {
        if (Logger.shouldLog(LogTag.JFR_DCMD, LogLevel.DEBUG)) {
            Logger.log(LogTag.JFR_DCMD, LogLevel.DEBUG,
                    "Executing DCmdDump: name=" + name +
                    ", filename=" + filename +
                    ", maxage=" + maxAge +
                    ", maxsize=" + maxSize +
                    ", begin=" + begin +
                    ", end=" + end +
                    ", path-to-gc-roots=" + pathToGcRoots);
        }

        if (FlightRecorder.getFlightRecorder().getRecordings().isEmpty()) {
            throw new DCmdException("No recordings to dump from. Use JFR.start to start a recording.");
        }

        if (maxAge != null) {
            if (end != null || begin != null) {
                throw new DCmdException("Dump failed, maxage can't be combined with begin or end.");
            }

            if (maxAge < 0) {
                throw new DCmdException("Dump failed, maxage can't be negative.");
            }
            if (maxAge == 0) {
                maxAge = Long.MAX_VALUE / 2; // a high value that won't overflow
            }
        }

        if (maxSize!= null) {
            if (maxSize < 0) {
                throw new DCmdException("Dump failed, maxsize can't be negative.");
            }
            if (maxSize == 0) {
                maxSize = Long.MAX_VALUE / 2; // a high value that won't overflow
            }
        }

        Instant beginTime = parseTime(begin, "begin");
        Instant endTime = parseTime(end, "end");

        if (beginTime != null && endTime != null) {
            if (endTime.isBefore(beginTime)) {
                throw new DCmdException("Dump failed, begin must precede end.");
            }
        }

        Duration duration = null;
        if (maxAge != null) {
            duration = Duration.ofNanos(maxAge);
            beginTime = Instant.now().minus(duration);
        }
        Recording recording = null;
        if (name != null) {
            recording = findRecording(name);
        }
        PlatformRecorder recorder = PrivateAccess.getInstance().getPlatformRecorder();

        try {
            synchronized (recorder) {
                dump(recorder, recording, name, filename, maxSize, pathToGcRoots, beginTime, endTime);
            }
        } catch (IOException | InvalidPathException e) {
            throw new DCmdException("Dump failed. Could not copy recording data. %s", e.getMessage());
        }
        return getResult();
    }

    public void dump(PlatformRecorder recorder, Recording recording, String name, String filename, Long maxSize, Boolean pathToGcRoots, Instant beginTime, Instant endTime) throws DCmdException, IOException {
        try (PlatformRecording r = newSnapShot(recorder, recording, pathToGcRoots)) {
            r.filter(beginTime, endTime, maxSize);
            if (r.getChunks().isEmpty()) {
                throw new DCmdException("Dump failed. No data found in the specified interval.");
            }
            // If a filename exist, use it
            // if a filename doesn't exist, use destination set earlier
            // if destination doesn't exist, generate a filename
            WriteableUserPath wup = null;
            if (recording != null) {
                PlatformRecording pRecording = PrivateAccess.getInstance().getPlatformRecording(recording);
                wup = pRecording.getDestination();
            }
            if (filename != null || (filename == null && wup == null) ) {
                SafePath safe = resolvePath(recording, filename);
                wup = new WriteableUserPath(safe.toPath());
            }
            r.dumpStopped(wup);
            reportOperationComplete("Dumped", name, new SafePath(wup.getRealPathText()));
        }
    }

    private Instant parseTime(String time, String parameter) throws DCmdException {
        if (time == null) {
            return null;
        }
        try {
            return Instant.parse(time);
        } catch (DateTimeParseException dtp) {
            // fall through
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(time);
            return ZonedDateTime.of(ldt, ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException dtp) {
            // fall through
        }
        try {
            LocalTime lt = LocalTime.parse(time);
            LocalDate ld = LocalDate.now();
            Instant instant = ZonedDateTime.of(ld, lt, ZoneId.systemDefault()).toInstant();
            Instant now = Instant.now();
            if (instant.isAfter(now) && !instant.isBefore(now.plusSeconds(3600))) {
                // User must have meant previous day
                ld = ld.minusDays(1);
            }
            return ZonedDateTime.of(ld, lt, ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException dtp) {
            // fall through
        }

        if (time.startsWith("-")) {
            try {
                long durationNanos = Utils.parseTimespan(time.substring(1));
                Duration duration = Duration.ofNanos(durationNanos);
                return Instant.now().minus(duration);
            } catch (NumberFormatException nfe) {
                // fall through
            }
        }
        throw new DCmdException("Dump failed, not a valid %s time.", parameter);
    }

    private PlatformRecording newSnapShot(PlatformRecorder recorder, Recording recording, Boolean pathToGcRoots) throws DCmdException, IOException {
        if (recording == null) {
            // Operate on all recordings
            PlatformRecording snapshot = recorder.newTemporaryRecording();
            recorder.fillWithRecordedData(snapshot, pathToGcRoots);
            return snapshot;
        }

        PlatformRecording pr = PrivateAccess.getInstance().getPlatformRecording(recording);
        return pr.newSnapshotClone("Dumped by user", pathToGcRoots);
    }

}
