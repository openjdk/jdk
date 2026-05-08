/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
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

package jdk.jfr.jvmti;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;

import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Profiler-style use case: a "profiler" thread calls
 *          RequestStackTrace targeting a different "worker" thread with
 *          a null ucontext. The handshake-based path is exercised; the
 *          stack trace is allowed to be safepoint-biased.
 * @requires vm.hasJFR & os.family != "windows"
 * @library /test/lib
 * @build jdk.jfr.jvmti.RequestStackTraceHelper
 * @run main/othervm/native -agentlib:RequestStackTraceAgent
 *      jdk.jfr.jvmti.TestRequestStackTraceForeignThread
 */
public class TestRequestStackTraceForeignThread {

    private static final String EVENT_NAME = "jdk.StackTraceRequest";
    private static final long   USER_DATA  = 0xD00DL;
    private static final int    CALLS      = 500;
    private static final int    MIN_EVENTS = 50;

    private static final AtomicBoolean stop = new AtomicBoolean(false);

    public static void main(String[] args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("throttle", "100000/s");
            r.start();

            Worker worker = new Worker();
            Thread workerThread = new Thread(worker, "RequestStackTrace-Worker");
            workerThread.start();

            // Give the worker a moment to enter its loop.
            Thread.sleep(50);

            for (int i = 0; i < CALLS; i++) {
                int rc = RequestStackTraceHelper.requestStackTraceWithThread(
                        workerThread, USER_DATA);
                Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_NONE, rc,
                        "unexpected JVMTI error: " + rc);
            }

            stop.set(true);
            workerThread.join();
            r.stop();

            long workerId = workerThread.threadId();
            List<RecordedEvent> events = Events.fromRecording(r).stream()
                    .filter(e -> e.getEventType().getName().equals(EVENT_NAME))
                    .filter(e -> e.getLong("userData") == USER_DATA)
                    .toList();

            int succeeded = 0;
            int failed = 0;
            int wrongThread = 0;
            int containsBusyMethod = 0;
            for (RecordedEvent e : events) {
                if (e.getThread() == null
                        || e.getThread().getJavaThreadId() != workerId) {
                    wrongThread++;
                    continue;
                }
                if (e.getBoolean("failed")) {
                    failed++;
                } else {
                    succeeded++;
                    if (stackContainsBusy(e)) containsBusyMethod++;
                }
            }

            System.out.println("Issued " + CALLS + " requests; events for worker: "
                    + (succeeded + failed)
                    + " (" + succeeded + " ok, " + failed + " failed, "
                    + wrongThread + " other-thread); "
                    + containsBusyMethod + " contained worker frame");

            Asserts.assertEquals(0, wrongThread,
                    "no events should be attributed to a different thread");
            Asserts.assertGreaterThanOrEqual(succeeded, MIN_EVENTS,
                    "expected at least " + MIN_EVENTS
                            + " successful events for the worker thread");
            // Of the successful events, most should show the worker actually
            // executing its busy method. Allow some slack for safepoint bias
            // landing the sample on a sender frame.
            Asserts.assertGreaterThanOrEqual(containsBusyMethod, succeeded / 2,
                    "expected most successful events to contain worker frame");
        }
    }

    private static class Worker implements Runnable {
        @Override
        public void run() {
            busyLoop();
        }

        // Kept on its own frame so the assertion can find it in stack traces.
        private void busyLoop() {
            double i = 0;
            while (!stop.get()) {
                for (int j = 0; j < 10_000; j++) {
                    i = Math.sqrt(i * Math.pow(Math.sqrt(Math.random()), Math.random()));
                }
            }
        }
    }

    private static boolean stackContainsBusy(RecordedEvent e) {
        if (e.getStackTrace() == null) {
            return false;
        }
        for (RecordedFrame f : e.getStackTrace().getFrames()) {
            if (f.getMethod() == null) {
                continue;
            }
            String type = f.getMethod().getType().getName();
            String method = f.getMethod().getName();
            if ("jdk.jfr.jvmti.TestRequestStackTraceForeignThread$Worker".equals(type)
                    && "busyLoop".equals(method)) {
                return true;
            }
        }
        return false;
    }
}
