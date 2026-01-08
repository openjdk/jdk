/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.test.lib.Asserts.assertFalse;
import static jdk.test.lib.Asserts.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingStream;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.thread.TestThread;
import jdk.test.lib.thread.XRun;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.runtime.TestJavaMonitorNotifyEvent
 */
public class TestJavaMonitorNotifyEvent {

    private static final String FIELD_KLASS_NAME = "monitorClass.name";
    private static final String FIELD_ADDRESS    = "address";
    private static final String FIELD_NOTIFIED_COUNT = "notifiedCount";

    private final static String EVENT_NAME = EventNames.JavaMonitorNotify;
    private static final long WAIT_TIME = 123456;

    static class Lock {
    }

    public static void main(String[] args) throws Throwable {
        final Lock lock = new Lock();
        final String lockClassName = lock.getClass().getName().replace('.', '/');
        final long mainThreadId = Thread.currentThread().threadId();

        List<RecordedEvent> events = new CopyOnWriteArrayList<>();
        try (RecordingStream rs = new RecordingStream()) {
            rs.enable(EVENT_NAME).withoutThreshold();
            rs.onEvent(EVENT_NAME, e -> {
                long threadId = e.getThread().getJavaThreadId();
                Object clazz = e.getValue(FIELD_KLASS_NAME);
                if (clazz.equals(lockClassName) && (threadId == mainThreadId)) {
                    events.add(e);
                    rs.close();
                }
            });
            rs.startAsync();

            final CountDownLatch latch = new CountDownLatch(1);
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
                waitThread.start();
                latch.await();
                synchronized (lock) {
                    lock.notifyAll();
                }
            } finally {
                waitThread.join();
            }

            rs.awaitTermination();

            System.out.println(events);
            assertFalse(events.isEmpty());
            for (RecordedEvent ev : events) {
                Events.assertField(ev, FIELD_ADDRESS).notEqual(0L);
                Events.assertField(ev, FIELD_NOTIFIED_COUNT).equal(1);
            }
        }
    }
}
