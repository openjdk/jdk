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
import java.util.concurrent.atomic.AtomicLong;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;

import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Call RequestStackTrace(thread=NULL) from within a mounted
 *          virtual thread. The walker should detect the carrier has a
 *          vthread mounted, walk the continuation's frames, and attribute
 *          the event to the vthread (not the carrier).
 * @requires vm.hasJFR & vm.continuations & os.family != "windows"
 * @library /test/lib
 * @build jdk.jfr.jvmti.RequestStackTraceHelper
 * @run main/othervm/native -agentlib:RequestStackTraceAgent
 *      jdk.jfr.jvmti.TestRequestStackTraceFromVirtualThread
 */
public class TestRequestStackTraceFromVirtualThread {

    private static final String EVENT_NAME       = "jdk.StackTraceRequest";
    private static final long   USER_DATA        = 0xBAD0CAFEL;
    private static final int    CALLS            = 500;
    private static final int    MIN_VTHREAD_EVTS = 50;

    public static void main(String[] args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME).with("throttle", "100000/s");
            r.start();

            AtomicLong vthreadId = new AtomicLong();
            Thread vt = Thread.ofVirtual()
                    .name("RequestStackTrace-VThread")
                    .start(() -> {
                        vthreadId.set(Thread.currentThread().threadId());
                        issueRequestsFromVThread();
                    });
            vt.join();

            r.stop();

            long vtid = vthreadId.get();
            List<RecordedEvent> events = Events.fromRecording(r).stream()
                    .filter(e -> e.getEventType().getName().equals(EVENT_NAME))
                    .filter(e -> e.getLong("userData") == USER_DATA)
                    .toList();

            int succeededOnVThread       = 0;
            int succeededOnVThreadWithFrame = 0;
            int succeededOtherThread     = 0;
            int failed                   = 0;
            for (RecordedEvent e : events) {
                if (e.getBoolean("failed")) {
                    failed++;
                    continue;
                }
                long eventTid = e.getThread() == null
                        ? -1 : e.getThread().getJavaThreadId();
                if (eventTid != vtid) {
                    succeededOtherThread++;
                    continue;
                }
                succeededOnVThread++;
                if (stackContainsIssueRequestsFromVThread(e)) {
                    succeededOnVThreadWithFrame++;
                }
            }

            System.out.println("Issued " + CALLS + " requests; events: total="
                    + events.size() + " onVThread=" + succeededOnVThread
                    + " withVThreadFrame=" + succeededOnVThreadWithFrame
                    + " otherThread=" + succeededOtherThread
                    + " failed=" + failed);

            Asserts.assertGreaterThanOrEqual(succeededOnVThread, MIN_VTHREAD_EVTS,
                    "expected >= " + MIN_VTHREAD_EVTS
                            + " events attributed to the virtual thread");

            // Of the events attributed to the vthread, most should have walked
            // the continuation and captured the vthread's busy method.
            Asserts.assertGreaterThanOrEqual(
                    succeededOnVThreadWithFrame, succeededOnVThread / 2,
                    "expected most vthread-attributed events to contain the"
                            + " vthread busy method in their stack trace");
        }
    }

    // Each iteration of this loop must contain enough Java work to keep the
    // vthread mounted between the JNI return and the next safepoint check;
    // otherwise the request gets processed when the vthread is no longer
    // mounted and the event is attributed to the carrier.
    private static void issueRequestsFromVThread() {
        double i = 0;
        for (int n = 0; n < CALLS; n++) {
            for (int j = 0; j < 100; j++) {
                i = Math.sqrt(i + j);
            }
            int rc = RequestStackTraceHelper.requestStackTrace(USER_DATA);
            Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_NONE, rc,
                    "unexpected JVMTI error: " + rc);
        }
    }

    private static boolean stackContainsIssueRequestsFromVThread(RecordedEvent e) {
        if (e.getStackTrace() == null) {
            return false;
        }
        for (RecordedFrame f : e.getStackTrace().getFrames()) {
            if (f.getMethod() == null) continue;
            String type = f.getMethod().getType().getName();
            String method = f.getMethod().getName();
            if ("jdk.jfr.jvmti.TestRequestStackTraceFromVirtualThread".equals(type)
                    && "issueRequestsFromVThread".equals(method)) {
                return true;
            }
        }
        return false;
    }
}
