/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.jfr.jmx.streaming;

import jdk.jfr.*;
import jdk.jfr.consumer.RecordedEvent;
import jdk.management.jfr.RemoteRecordingStream;

import javax.management.MBeanServerConnection;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * @test
 * @summary Tests that a RemoteRecordingStream is closed if the underlying remote recording is closed.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @build jdk.jfr.api.consumer.recordingstream.EventProducer
 * @run main/othervm jdk.jfr.jmx.streaming.TestClosedRecording
 */
public class TestClosedRecording {

    private static class SendEventListener implements FlightRecorderListener {
        @Override
        public void recordingStateChanged(Recording recording) {
            if(recording.getState() == RecordingState.RUNNING){
                CloseEvent e = new CloseEvent();
                e.commit();
            }
        }
    }

    private static final class CloseEvent extends Event {
    }

    private static final Consumer<RecordedEvent> CLOSE_RECORDING = e -> {
        FlightRecorder.getFlightRecorder().getRecordings().getFirst().close();
    };

    private static final MBeanServerConnection CONNECTION = ManagementFactory.getPlatformMBeanServer();

    public static void main(String... args) throws Exception {
        FlightRecorder.addListener(new SendEventListener());
        sync();
        async();
    }

    private static void sync() throws Exception {
        try (RemoteRecordingStream rs = new RemoteRecordingStream(CONNECTION)) {
            rs.onEvent(CLOSE_RECORDING);
            rs.start();
        }
    }

    private static void async() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try (RemoteRecordingStream rs = new RemoteRecordingStream(CONNECTION)) {
            rs.onEvent(CLOSE_RECORDING);
            rs.onClose(() -> {
                latch.countDown();
            });
            rs.startAsync();
            latch.await();
        }
    }
}
