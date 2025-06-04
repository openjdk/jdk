/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal;

import jdk.jfr.RecordingState;

/**
 * Class responsible for dumping recordings on exit
 *
 */
final class ShutdownHook extends Thread {
    private final PlatformRecorder recorder;
    Object tlabDummyObject;

    ShutdownHook(PlatformRecorder recorder) {
        super("JFR Shutdown Hook");
        this.recorder = recorder;
    }

    @Override
    public void run() {
        // this allocation is done in order to fetch a new TLAB before
        // starting any "real" operations. In low memory situations,
        // we would like to take an OOM as early as possible.
        tlabDummyObject = new Object();
        PlatformRecorder.setInShutDown();
        for (PlatformRecording recording : recorder.getRecordings()) {
            if (recording.getDumpOnExit() && recording.getState() == RecordingState.RUNNING) {
                dump(recording);
            }
        }
        recorder.destroy();
    }

    private void dump(PlatformRecording recording) {
        try {
            WriteablePath dest = recording.getDestination();
            if (dest == null) {
                dest = recording.makeDumpPath();
                recording.setDestination(dest);
            }
            if (dest != null) {
                recording.stop("Dump on exit");
            }
        } catch (Exception e) {
            if (Logger.shouldLog(LogTag.JFR, LogLevel.DEBUG)) {
                Logger.log(LogTag.JFR, LogLevel.DEBUG, "Could not dump recording " + recording.getName() + " on exit. " + e.getMessage());
            }
        }
    }

    static final class ExceptionHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            JVM.uncaughtException(t, e);
        }
    }
}
