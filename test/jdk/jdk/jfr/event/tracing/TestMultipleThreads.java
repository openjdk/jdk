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

import java.util.ArrayList;
import java.util.List;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordingStream;

/**
 * @test
 * @summary Tests that tracing and timing work when using multiple threads.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @run main/othervm
 *           -Xlog:jfr+methodtrace=trace
 *           jdk.jfr.event.tracing.TestMultipleThreads
 **/
public class TestMultipleThreads {
    private static final String METHOD_PREFIX = TestMultipleThreads.class.getName() + "::method";
    private static final String TRACE_EVENT = "jdk.MethodTrace";
    private static final String TIMING_EVENT = "jdk.MethodTiming";
    private static int METHOD_COUNT = 5;
    private static int THREAD_COUNT = 5;
    private static int INVOCATIONS_PER_THREAD = 25_000; // Low enough to fit one chunk
    private static int INVOCATIONS_PER_METHOD = THREAD_COUNT * INVOCATIONS_PER_THREAD / METHOD_COUNT;

    public static class TestThread extends Thread {
        public void run() {
            for (int i = 0; i < INVOCATIONS_PER_THREAD; i++) {
                switch (i % METHOD_COUNT) {
                case 0 -> method0();
                case 1 -> method1();
                case 2 -> method2();
                case 3 -> method3();
                case 4 -> method4();
                }
            }
        }
    }

    public static void main(String... args) throws Exception {
        List<RecordedEvent> traceEvents = new ArrayList<>();
        List<RecordedEvent> timingEvents = new ArrayList<>();
        try (RecordingStream r = new RecordingStream()) {
            r.enable(TRACE_EVENT).with("filter",
                METHOD_PREFIX + "0;" +
                METHOD_PREFIX + "2;");
            r.enable(TIMING_EVENT).with("filter",
                METHOD_PREFIX + "0;" +
                METHOD_PREFIX + "1;" +
                METHOD_PREFIX + "2;" +
                METHOD_PREFIX + "3;" +
                METHOD_PREFIX + "4;")
             .with("period", "endChunk");
            List<TestThread> threads = new ArrayList<>();
            for (int i = 0; i < THREAD_COUNT; i++) {
                threads.add(new TestThread());
            }
            r.setReuse(false);
            r.onEvent(TRACE_EVENT, traceEvents::add);
            r.onEvent(TIMING_EVENT, timingEvents::add);
            r.startAsync();
            for (TestThread t : threads) {
                t.start();
            }
            for (TestThread t : threads) {
                t.join();
            }
            r.stop();
            verifyTraceEvents(traceEvents);
            for (RecordedEvent event : timingEvents) {
                System.out.println(event);
            }
            verifyTimingEvents(timingEvents);
        }
    }

    private static void verifyTimingEvents(List<RecordedEvent> events) throws Exception {
        for (RecordedEvent e : events) {
            long invocations = e.getLong("invocations");
            if (invocations != INVOCATIONS_PER_METHOD) {
                RecordedMethod method = e.getValue("method");
                String msg = "Expected " + INVOCATIONS_PER_METHOD + " invocations for ";
                msg += method.getName() + ", but got " + invocations;
                throw new Exception(msg);
            }
        }
        if (events.size() != METHOD_COUNT) {
            throw new Exception("Expected " + METHOD_COUNT + " timing events, one per method");
        }
    }

    private static void verifyTraceEvents(List<RecordedEvent> events) throws Exception {
        int expected = 2 * INVOCATIONS_PER_METHOD;
        if (events.size() != expected) {
            throw new Exception("Expected " + expected + " event, but got " + events.size());
        }
    }

    private static void method0() {
    }

    private static void method1() {
    }

    private static void method2() {
    }

    private static void method3() {
    }

    private static void method4() {
    }
}
