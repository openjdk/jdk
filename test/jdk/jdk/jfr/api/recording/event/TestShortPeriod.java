/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
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

package jdk.jfr.api.recording.event;

import java.time.Duration;

import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordingStream;
import jdk.test.lib.jfr.EventNames;

/**
 * @test Tests that periodic events are not disabled when using a very short
 *       period
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.api.recording.event.TestShortPeriod
 */
public class TestShortPeriod {

    static class PeriodicEvent extends Event {
    }

    private static Thread startLoopingThread() {
        var t = new Thread(() -> {
            while (!Thread.interrupted()) {}
        });
        t.start();
        return t;
    }

    public static void main(String... args) {
        testNativeEventPeriod();
        testJavaEventPeriod();
        testExecutionSamplePeriod();
    }

    private static void testNativeEventPeriod() {
        try (var r = new RecordingStream()) {
            r.enable(EventNames.JVMInformation).withPeriod(Duration.ofNanos(1));
            r.onEvent(e -> r.close());
            r.start();
        }
    }

    private static void testJavaEventPeriod() {
        Runnable hook = () -> {
            PeriodicEvent e = new PeriodicEvent();
            e.commit();
        };
        FlightRecorder.addPeriodicEvent(PeriodicEvent.class, hook);
        try (var r = new RecordingStream()) {
            r.enable(PeriodicEvent.class).withPeriod(Duration.ofNanos(1));
            r.onEvent(e -> r.close());
            r.start();
        }
        FlightRecorder.removePeriodicEvent(hook);
    }

    // The execution sample event doesn't use the standard mechanism
    // for periodic events
    private static void testExecutionSamplePeriod() {
        try (var r = new RecordingStream()) {
            // start a new thread as the current thread is excluded from
            // sample capturing to avoid recursion
            var t = startLoopingThread();
            r.enable(EventNames.ExecutionSample).withPeriod(Duration.ofNanos(1));
            r.onEvent("jdk.ExecutionSample", e -> {
                t.interrupt();
                r.close();
            });
            r.start();
        }
    }
}
