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

import jdk.jfr.Configuration;
import jdk.jfr.Recording;

import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Verify that default.jfc ships jdk.StackTraceRequest disabled and
 *          profile.jfc ships it enabled. Locks in the JFC defaults and
 *          serves as a regression guard against accidental flips.
 * @requires vm.hasJFR & os.family != "windows"
 * @library /test/lib
 * @build jdk.jfr.jvmti.RequestStackTraceHelper
 * @run main/othervm/native -agentlib:RequestStackTraceAgent
 *      jdk.jfr.jvmti.TestRequestStackTraceJfcDefault
 */
public class TestRequestStackTraceJfcDefault {

    private static final String EVENT_NAME = "jdk.StackTraceRequest";

    // profile.jfc throttles at 100/s; the throttler uses 200ms windows with a
    // budget of 20 events per window.  A burst of CALLS requests will complete
    // in roughly one window, so at least one window's worth of events (20)
    // should be accepted.  Use 10 as the lower bound to leave room for the
    // window not being full at the start of the recording.
    private static final int CALLS      = 100;
    private static final int MIN_EVENTS = 10;

    public static void main(String[] args) throws Exception {
        runWithConfiguration("default", false);
        runWithConfiguration("profile", true);
    }

    private static void runWithConfiguration(String name, boolean expectEnabled)
            throws Exception {
        Configuration c = Configuration.getConfiguration(name);
        try (Recording r = new Recording(c)) {
            r.start();

            int observedRc = -1;
            for (int i = 0; i < CALLS; i++) {
                observedRc = RequestStackTraceHelper.requestStackTrace(0xCAFEL);
            }

            r.stop();

            int eventCount = (int) Events.fromRecording(r).stream()
                    .filter(e -> e.getEventType().getName().equals(EVENT_NAME))
                    .count();

            System.out.println(name + ".jfc: rc=" + observedRc
                    + " events=" + eventCount);

            if (expectEnabled) {
                Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_NONE,
                        observedRc, name + ".jfc: expected NONE return code");
                Asserts.assertGreaterThanOrEqual(eventCount, MIN_EVENTS,
                        name + ".jfc: expected >= " + MIN_EVENTS + " events");
            } else {
                Asserts.assertEquals(RequestStackTraceHelper.JVMTI_ERROR_NOT_AVAILABLE,
                        observedRc, name + ".jfc: expected NOT_AVAILABLE return code");
                Asserts.assertEquals(0, eventCount,
                        name + ".jfc: expected zero events");
            }
        }
    }
}
