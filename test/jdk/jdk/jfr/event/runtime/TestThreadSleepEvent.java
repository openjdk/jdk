/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.runtime;

import static jdk.test.lib.Asserts.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadFactory;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR & vm.continuations
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.runtime.TestThreadSleepEvent
 */
public class TestThreadSleepEvent {

    private final static String EVENT_NAME = EventNames.ThreadSleep;
    // Need to set the sleep time quite high (47 ms) since the sleep
    // time on Windows has been proved unreliable.
    // See bug 6313903
    private final static Long SLEEP_TIME_MS = new Long(47);

    public static void main(String[] args) throws Throwable {

        try (Recording recording = new Recording()) {
            recording.enable(EVENT_NAME).withoutThreshold().withStackTrace();
            recording.start();
            Thread.sleep(SLEEP_TIME_MS);
            Thread virtualThread = Thread.ofVirtual().start(TestThreadSleepEvent::virtualSleep);
            virtualThread.join();
            recording.stop();

            int threadCount = 2;
            List<RecordedEvent> events = Events.fromRecording(recording);
            Asserts.assertEquals(events.size(), threadCount);
            for (RecordedEvent event : events) {
                System.out.println(event);
                System.out.println(event.getStackTrace());
                if (event.getThread().getJavaThreadId() == Thread.currentThread().getId()) {
                    threadCount--;
                    Events.assertTopFrame(event, TestThreadSleepEvent.class, "main");
                    Events.assertDuration(event, "time", Duration.ofMillis(SLEEP_TIME_MS));
                }
                if (event.getThread().getJavaThreadId() == virtualThread.getId()) {
                    threadCount--;
                    Events.assertTopFrame(event, TestThreadSleepEvent.class, "virtualSleep");
                    Events.assertDuration(event, "time", Duration.ofMillis(SLEEP_TIME_MS));
                }
            }
            Asserts.assertEquals(threadCount, 0, "Could not find all expected events");
        }
    }

    private static void virtualSleep() {
        try {
            Thread.sleep(SLEEP_TIME_MS);
        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        }
    }
}
