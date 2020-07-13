/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.test.lib.Asserts.assertEQ;
import static jdk.test.lib.Asserts.assertNotNull;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.runtime.TestThreadStartEndEvents
 */

/**
 * Starts a number of threads in order and verifies
 * that Thread Start events are in that same order.
 * The order of Thread End events is non-deterministic.
 */
public class TestThreadStartEndEvents {
    private final static String EVENT_NAME_THREAD_START = EventNames.ThreadStart;
    private final static String EVENT_NAME_THREAD_END = EventNames.ThreadEnd;
    private static final String THREAD_NAME_PREFIX = "TestThread-";
    private static int currentThreadIndex = 0;
    private static int numberOfThreadStartEvents = 0;
    private static int numberOfThreadEndEvents = 0;

    public static void main(String[] args) throws Throwable {
        Recording recording = new Recording();
        recording.enable(EVENT_NAME_THREAD_START).withThreshold(Duration.ofMillis(0));
        recording.enable(EVENT_NAME_THREAD_END).withThreshold(Duration.ofMillis(0));
        recording.start();
        LatchedThread[] threads = startThreads();
        stopThreads(threads);
        recording.stop();
        List<RecordedEvent> events = Events.fromRecording(recording);
        events.sort((e1, e2) -> e1.getStartTime().compareTo(e2.getStartTime()));
        Events.hasEvents(events);
        for (RecordedEvent event : events) {
            if (!event.getThread().getJavaName().startsWith(THREAD_NAME_PREFIX)) {
                continue;
            }
            System.out.println("Event:" + event);
            String eventType = event.getEventType().getName();
            switch (eventType) {
                case EVENT_NAME_THREAD_START:
                    validateThreadStartEvent(event, threads);
                break;
                case EVENT_NAME_THREAD_END:
                    validateThreadEndEvent(event);
                break;
                default:
                   throw new RuntimeException("Test encountered an invalid event: " + eventType);
            }
        }
        assertEQ(numberOfThreadStartEvents, threads.length);
        assertEQ(numberOfThreadEndEvents, threads.length);
    }

    // The order of Thread Start events should corresponding to their start order.
    private static void validateThreadStartEvent(RecordedEvent event, LatchedThread[] threads) {
        Events.assertEventThread(event, threads[currentThreadIndex++]);
        Events.assertEventThread(event, "parentThread", Thread.currentThread());
        RecordedStackTrace stackTrace = event.getValue("stackTrace");
        assertNotNull(stackTrace);
        RecordedMethod topMethod = stackTrace.getFrames().get(0).getMethod();
        assertEQ(topMethod.getName(), "startThread");
        numberOfThreadStartEvents++;
    }

    // The order of Thread End events is non-deterministic. This is because the event
    // is committed as part of dismantling the JavaThread in the VM, post thread.isAlive().
    private static void validateThreadEndEvent(RecordedEvent event) {
        numberOfThreadEndEvents++;
    }

    private static LatchedThread[] startThreads() {
        LatchedThread threads[] = new LatchedThread[10];
        ThreadGroup threadGroup = new ThreadGroup("TestThreadGroup");
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new LatchedThread(threadGroup, THREAD_NAME_PREFIX + i);
            threads[i].startThread();
            System.out.println("Started thread id=" + threads[i].getId());
        }
        return threads;
    }

    private static void stopThreads(LatchedThread[] threads) {
        for (LatchedThread thread : threads) {
            thread.stopThread();
            while (thread.isAlive()) {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class LatchedThread extends Thread {
        private final CountDownLatch start = new CountDownLatch(1);
        private final CountDownLatch stop = new CountDownLatch(1);

        public LatchedThread(ThreadGroup threadGroup, String name) {
            super(threadGroup, name);
        }

        public void run() {
            start.countDown();
            try {
                stop.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void startThread() {
            this.start();
            try {
                start.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public void stopThread() {
            stop.countDown();
        }
    }
}
