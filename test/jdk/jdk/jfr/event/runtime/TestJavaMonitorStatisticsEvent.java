/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.thread.TestThread;
import jdk.test.lib.thread.XRun;

/**
 * @test TestJavaMonitorStatisticsEvent
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm -XX:GuaranteedAsyncDeflationInterval=100 jdk.jfr.event.runtime.TestJavaMonitorStatisticsEvent
 */
public class TestJavaMonitorStatisticsEvent {

    private static final String FIELD_TOTAL_COUNT = "totalCount";
    private static final String FIELD_DEFLATED_COUNT = "deflatedCount";

    private static final String EVENT_NAME = EventNames.JavaMonitorStatistics;
    private static final long WAIT_TIME = 123456;

    static class Lock {
    }

    public static void main(String[] args) throws Exception {
        Recording recording = new Recording();
        recording.enable(EVENT_NAME).withThreshold(Duration.ofMillis(0));
        final Lock lock = new Lock();
        final CountDownLatch latch = new CountDownLatch(1);
        // create a thread that waits
        TestThread waitThread = new TestThread(new XRun() {
            @Override
            public void xrun() throws Throwable {
                synchronized (lock) {
                    latch.countDown();
                    lock.wait(WAIT_TIME);
                }
            }
        });
        try {
            recording.start();
            waitThread.start();
            latch.await();
            synchronized (lock) {
                lock.notifyAll();
            }
        } finally {
            waitThread.join();
            // Let deflater thread run.
            Thread.sleep(3000);
            recording.stop();
        }
        boolean isAnyFound = false;
        try {
            // Find at least one event with the correct monitor class and check the other fields
            for (RecordedEvent event : Events.fromRecording(recording)) {
                assertTrue(EVENT_NAME.equals(event.getEventType().getName()), "mismatched event types?");
                long totalCount = Events.assertField(event, FIELD_TOTAL_COUNT).getValue();
                long deflatedCount = Events.assertField(event, FIELD_DEFLATED_COUNT).getValue();
                assertTrue(totalCount >= 0, "Should be positive");
                assertTrue(deflatedCount >= 0, "Should be positive");
                assertTrue(totalCount + deflatedCount > 0, "Should be non-zero");
                isAnyFound = true;
                break;
            }
            assertTrue(isAnyFound, "Expected a statistics event from test");
        } catch (Throwable e) {
            recording.dump(Paths.get("failed.jfr"));
            throw e;
        } finally {
            recording.close();
        }
    }
}
