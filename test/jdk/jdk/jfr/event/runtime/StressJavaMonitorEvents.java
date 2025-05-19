/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import jdk.jfr.consumer.RecordingStream;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.lib.thread.TestThread;
import jdk.test.lib.thread.XRun;

/**
 * @test StressJavaMonitorEvents
 * @summary Tests that VM does not crash when monitor-related JFR events are enabled
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:GuaranteedAsyncDeflationInterval=100 jdk.jfr.event.runtime.StressJavaMonitorEvents
 */
public class StressJavaMonitorEvents {

    static final int RUN_TIME_MS = 10000;
    static final int GC_EVERY_MS = 500;
    static final int THREADS = 4;
    static final int NUM_LOCKS = 1024;

    static final List<String> CAPTURE_EVENTS = List.of(
        EventNames.JavaMonitorEnter,
        EventNames.JavaMonitorWait,
        EventNames.JavaMonitorNotify,
        EventNames.JavaMonitorInflate,
        EventNames.JavaMonitorDeflate,
        EventNames.JavaMonitorStatistics
    );

    static final Set<String> CAN_BE_ZERO_EVENTS = Set.of(
        // Only when lock actually gets contended, not guaranteed.
        EventNames.JavaMonitorEnter,
        // Only when there are waiters on the lock, not guaranteed.
        EventNames.JavaMonitorNotify
    );

    static final Object[] LOCKS = new Object[NUM_LOCKS];

    public static TestThread startThread(final long deadline) {
        TestThread t = new TestThread(new XRun() {
            @Override
            public void xrun() throws Throwable {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                while (System.nanoTime() < deadline) {
                    // Overwrite random lock, making the old one dead
                    LOCKS[r.nextInt(NUM_LOCKS)] = new Object();

                    // Wait on random lock, inflating it
                    Object waitLock = LOCKS[r.nextInt(NUM_LOCKS)];
                    if (waitLock != null) {
                        synchronized (waitLock) {
                            waitLock.wait(1);
                        }
                    }

                    // Notify a random lock
                    Object notifyLock = LOCKS[r.nextInt(NUM_LOCKS)];
                    if (notifyLock != null) {
                        synchronized (notifyLock) {
                            notifyLock.notify();
                        }
                    }

                    // Notify all on a random lock
                    Object notifyAllLock = LOCKS[r.nextInt(NUM_LOCKS)];
                    if (notifyAllLock != null) {
                        synchronized (notifyAllLock) {
                            notifyAllLock.notifyAll();
                        }
                    }
                }
            }
        });
        t.start();
        return t;
    }

    public static void main(String[] args) throws Exception {
        Map<String, AtomicLong> counters = new HashMap<>();

        try (RecordingStream rs = new RecordingStream()) {
            // Setup all interesting events, and start recording.
            for (String ev : CAPTURE_EVENTS) {
                rs.enable(ev).withoutThreshold();
                AtomicLong counter = new AtomicLong();
                rs.onEvent(ev, e -> counter.incrementAndGet());
                counters.put(ev, counter);
            }
            rs.startAsync();

            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RUN_TIME_MS);
            List<TestThread> threads = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                threads.add(startThread(deadline));
            }

            // Trigger GCs periodically to yield dead objects with inflated monitors.
            while (System.nanoTime() < deadline) {
                Thread.sleep(GC_EVERY_MS);
                System.gc();
            }

            // Wait for all threads to exit and close the recording.
            for (TestThread t : threads) {
                t.join();
            }
            rs.close();

            // Print stats and check event counts.
            for (String ev : CAPTURE_EVENTS) {
                long count = counters.get(ev).get();
                System.out.println(ev + ": " + count);
                assertTrue(CAN_BE_ZERO_EVENTS.contains(ev) || (count > 0));
            }
        }
    }
}
