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

package jdk.jfr.event.runtime;

import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.management.ThreadMXBeanTool;
import jdk.test.lib.thread.TestThread;


/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 *
 * @run main/othervm jdk.jfr.event.runtime.TestThreadParkEvent
 */

public class TestThreadParkEvent {
    private static final String EVENT_NAME = EventNames.ThreadPark;
    private static final long THRESHOLD_MILLIS = 1;

    static class Blocker {
    }

    public static void main(String[] args) throws Throwable {
        final CountDownLatch stop = new CountDownLatch(1);
        final Blocker blocker = new Blocker();
        TestThread parkThread = new TestThread(new Runnable() {
            public void run() {
                while (stop.getCount() > 0) {
                    LockSupport.park(blocker);
                }
            }
        });

        Recording recording = new Recording();
        recording.enable(EVENT_NAME).withThreshold(Duration.ofMillis(THRESHOLD_MILLIS));
        try {
            recording.start();
            parkThread.start();
            ThreadMXBeanTool.waitUntilBlockingOnObject(parkThread, Thread.State.WAITING, blocker);
            // sleep so we know the event is recorded
            Thread.sleep(2 * THRESHOLD_MILLIS);
        } finally {
            stop.countDown();
            LockSupport.unpark(parkThread);
            parkThread.join();
            recording.stop();
        }

        List<RecordedEvent> events = Events.fromRecording(recording);
        Events.hasEvents(events);
        boolean isAnyFound = false;
        for (RecordedEvent event : events) {
            System.out.println("Event:" + event);
            String klassName = Events.assertField(event, "parkedClass.name").notNull().getValue();
            if (klassName.equals(blocker.getClass().getName().replace('.', '/'))) {
                assertFalse(isAnyFound, "Found more than 1 event");
                isAnyFound = true;
                Events.assertField(event, "timeout").equal(0L);
                Events.assertField(event, "address").notEqual(0L);
                Events.assertEventThread(event, parkThread);
            }
        }
        assertTrue(isAnyFound, "Correct event not found");
    }

}
