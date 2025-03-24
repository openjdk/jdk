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

import java.util.concurrent.atomic.AtomicReference;

import jdk.jfr.Event;
import jdk.jfr.StackTrace;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordingStream;

/**
 * @test
 * @summary Basic test of the MethodTrace event.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm -Xlog:jfr+methodtrace=trace
 *      jdk.jfr.event.tracing.TestMethodTrace
 **/
public class TestMethodTrace {
    private static final String EVENT_NAME = "jdk.MethodTrace";
    private static final String CLASS_NAME = TestMethodTrace.class.getName();

    @StackTrace(false)
    private static class OuterMeasurement extends Event {
    }

    @StackTrace(false)
    private static class InnerMeasurement extends Event {
    }

    public static void main(String... args) throws Exception {
        AtomicReference<RecordedEvent> o = new AtomicReference<>();
        AtomicReference<RecordedEvent> i = new AtomicReference<>();
        AtomicReference<RecordedEvent> e = new AtomicReference<>();
        try (RecordingStream s = new RecordingStream()) {
            s.enable(EVENT_NAME).with("filter", CLASS_NAME + "::bar");
            s.onEvent(EVENT_NAME, e::set);
            s.onEvent(OuterMeasurement.class.getName(), o::set);
            s.onEvent(InnerMeasurement.class.getName(), i::set);
            s.startAsync();
            foo();
            s.stop();
        }
        RecordedEvent event = e.get();
        RecordedEvent outer = o.get();
        RecordedEvent inner = i.get();
        System.out.println(event);

        System.out.println("Outer start          : " + outer.getStartTime());
        System.out.println("  Method Trace start : " + event.getStartTime());
        System.out.println("   Inner start       : " + inner.getStartTime());
        System.out.println("   Inner end         : " + inner.getEndTime());
        System.out.println("  Method Trace end   : " + event.getEndTime());
        System.out.println("Outer end            : " + outer.getEndTime());

        if (event.getStartTime().isBefore(outer.getStartTime())) {
            throw new Exception("Too early start time");
        }
        if (event.getStartTime().isAfter(inner.getStartTime())) {
            throw new Exception("Too late start time");
        }
        if (event.getEndTime().isBefore(inner.getEndTime())) {
            throw new Exception("Too early end time");
        }
        if (event.getEndTime().isAfter(outer.getEndTime())) {
            throw new Exception("Too late end time");
        }
        RecordedMethod method = event.getValue("method");
        if (!method.getName().equals("bar")) {
            throw new Exception("Expected method too be bar()");
        }
        RecordedMethod topMethod = event.getStackTrace().getFrames().get(0).getMethod();
        if (!topMethod.getName().equals("foo")) {
            throw new Exception("Expected top frame too be foo()");
        }
    }

    private static void foo() {
        OuterMeasurement event = new OuterMeasurement();
        event.begin();
        bar();
        event.commit();
    }

    private static void bar() {
        InnerMeasurement event = new InnerMeasurement();
        event.begin();
        event.commit();
    }
}
