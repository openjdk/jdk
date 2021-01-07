/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.os;

import static jdk.test.lib.Asserts.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventField;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;


/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.os.TestCPULoad
 */
public class TestCPULoad {
    private final static String EVENT_NAME = EventNames.CPULoad;

    public static void main(String[] args) throws Throwable {
        Recording recording = new Recording();
        recording.enable(EVENT_NAME);
        recording.start();
        final AtomicLong sum = new AtomicLong(0);
        Thread[] threads = new Thread[16];
        for (int i = 0; i < 16; i++) {
            threads[i] = new Thread(() -> {
                // do some busy work here
                for (int j = 0; j < 5000000; j++) {
                    sum.addAndGet(j);
                }
            });
            threads[i].start();
        }
        for (int i = 0; i < 16; i++) {
            threads[i].join();
        }

        System.out.println(sum.get());
        // Need to sleep so a time delta can be calculated
        Thread.sleep(100);
        recording.stop();

        List<RecordedEvent> events = Events.fromRecording(recording);
        if (events.isEmpty()) {
            // CPU Load events are unreliable on Windows because
            // the way processes are identified with perf. counters.
            // See BUG 8010378.
            // Workaround is to detect Windows and allow
            // test to pass if events are missing.
            if (isWindows()) {
                return;
            }
            throw new AssertionError("Expected at least one event");
        }
        for (RecordedEvent event : events) {
            for (String metricName : metricNames) {
                String loadName = metricName;
                String cpuTimeName = metricName + "Time";

                EventField loadField = Events.assertField(event, loadName).atLeast(0.0f).atMost(1.0f);
                EventField timeField = Events.assertField(event, cpuTimeName).atLeast((Float)loadField.getValue() > 0.0f ? 1L : 0L);
                System.out.println("time: " + timeField.getValue());
            }
        }
    }

    private static final String[] metricNames = {"jvmUser", "jvmSystem", "machineTotal"};

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }
}
