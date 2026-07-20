/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.event.tracing;

import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests that <clinit> can be instrumented.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @build jdk.jfr.event.tracing.StaticInitializer
 * @run main/othervm -Xlog:jfr+methodtrace=trace
 *      jdk.jfr.event.tracing.TestClinit
 **/
public class TestClinit {
    private static final String PACKAGE_NAME = TestClinit.class.getPackageName();
    private static final String CLINIT_CLASS_NAME = PACKAGE_NAME + ".StaticInitializer";
    private static final String CLINIT_METHOD_NAME = CLINIT_CLASS_NAME + "::<clinit>";

    public static void main(String... args) throws Exception {
        try (Recording r = new Recording()) {
            r.enable("jdk.MethodTrace")
             .with("filter", CLINIT_CLASS_NAME);
            r.enable("jdk.MethodTiming")
             .with("filter", CLINIT_METHOD_NAME)
             .with("period", "endChunk");

            r.start();
            StaticInitializer.TRIGGERED = "true";
            r.stop();

            List<RecordedEvent> events = Events.fromRecording(r);
            Events.assertEventCount(events, 2);

            RecordedEvent traceEvent = Events.getFirst(events, "jdk.MethodTrace");
            Events.assertTopFrame(traceEvent, TestClinit.class.getName(), "main");
            assertClinitMethod(traceEvent);

            RecordedEvent timingEvent = Events.getFirst(events, "jdk.MethodTiming");
            assertClinitMethod(timingEvent);
        }
    }

    private static void assertClinitMethod(RecordedEvent event) throws Exception {
        RecordedMethod method = event.getValue("method");
        if (!method.getName().equals("<clinit>")) {
            System.out.println(event);
            throw new Exception("Expected <clinit>, was " + method.getName());
        }
    }
}
