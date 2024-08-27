/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.jvm;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import jdk.jfr.Recording;
import jdk.jfr.Name;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import jdk.test.lib.jfr.Events;

/**
 * @test TestHiddenWait
 * @key jfr
 * @summary Checks that JFR code don't emit noise in the form of ThreadSleep and JavaMonitorWait events.
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.jvm.TestHiddenWait
 */
public class TestHiddenWait {
    static final String PERIODIC_EVENT_NAME = "test.Periodic";

    @Name(PERIODIC_EVENT_NAME)
    public static class PeriodicEvent extends Event {
    }

    public static void main(String... args) throws Exception {
        FlightRecorder.addPeriodicEvent(PeriodicEvent.class,  () -> {
            PeriodicEvent event = new PeriodicEvent();
            event.commit();
        });
        try (Recording r = new Recording()) {
            AtomicLong counter = new AtomicLong();
            r.enable("jdk.ThreadSleep").withoutThreshold();
            r.enable("jdk.JavaMonitorWait").withoutThreshold();
            r.enable(PERIODIC_EVENT_NAME).withPeriod(Duration.ofMillis(100));
            r.start();
            // Triggers Object.wait() in stream barrier
            try (RecordingStream b = new RecordingStream()) {
                b.startAsync();
                b.stop();
            }
            // Wait for for periodic events
            try (RecordingStream s = new RecordingStream()) {
                s.onEvent(PERIODIC_EVENT_NAME, e -> {
                    if (counter.incrementAndGet() >= 2) {
                        s.close();
                    }
                });
                s.start();
            }
            List<RecordedEvent> events = Events.fromRecording(r);
            for (RecordedEvent event : events) {
                if (!event.getEventType().getName().equals(PERIODIC_EVENT_NAME)) {
                    System.out.println(event);
                    throw new Exception("Didn't expect ThreadSleep or JavaMonitorWait events");
                }
            }
        }
    }
}
