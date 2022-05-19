/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.threading;

import java.util.List;

import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;

/**
 * @test
 * @summary Tests emitting an event, both in Java and native, in a virtual
 *          thread with the maximum number of allowed stack frames for JFR
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @modules jdk.jfr/jdk.jfr.internal
 * @compile --enable-preview -source ${jdk.version} TestDeepVirtualStackTrace.java
 * @run main/othervm --enable-preview -XX:FlightRecorderOptions:stackdepth=2048
 *      jdk.jfr.threading.TestDeepVirtualStackTrace
 */
public class TestDeepVirtualStackTrace {

    public final static int FRAME_COUNT = 2048;

    @Name("test.Deep")
    private static class TestEvent extends Event {
    }

    public static Object[] allocated;

    public static void main(String... args) throws Exception {
        testJavaEvent();
        testNativeEvent();
    }

    private static void testJavaEvent() throws Exception {
        assertStackTrace(() -> deepevent(FRAME_COUNT), "test.Deep", "deepevent");
    }

    private static void deepevent(int depth) {
        if (depth == 0) {
            TestEvent e = new TestEvent();
            e.commit();
            System.out.println("Emitted Deep event");
            return;
        }
        deepevent(depth - 1);
    }

    private static void testNativeEvent() throws Exception {
        assertStackTrace(() -> deepsleep(FRAME_COUNT), "jdk.ObjectAllocationOutsideTLAB", "sleep");
    }

    private static void deepsleep(int depth) {
        if (depth == 0) {
            allocated = new Object[10_000_000];
            System.out.println("Emitted ObjectAllocationOutsideTLAB event");
            return;
        }
        deepsleep(depth - 1);
    }

    private static void assertStackTrace(Runnable eventEmitter, String eventName, String stackMethod) throws Exception {
        System.out.println();
        System.out.println("Testing event: " + eventName);
        System.out.println("=============================");
        try (Recording r = new Recording()) {
            r.enable(eventName).withoutThreshold();
            r.start();
            Thread vt = Thread.ofVirtual().start(eventEmitter);
            vt.join();
            r.stop();
            List<RecordedEvent> events = Events.fromRecording(r);
            Asserts.assertEquals(events.size(), 1, "No event found in virtual thread");
            RecordedEvent event = events.get(0);
            System.out.println(event);
            RecordedStackTrace stackTrace = event.getStackTrace();
            List<RecordedFrame> frames = stackTrace.getFrames();
            Asserts.assertTrue(stackTrace.isTruncated());
            int count = 0;
            for (RecordedFrame frame : frames) {
                Asserts.assertTrue(frame.isJavaFrame());
                Asserts.assertNotNull(frame.getMethod());
                RecordedMethod m = frame.getMethod();
                Asserts.assertNotNull(m.getType());
                if (m.getName().contains(stackMethod)) {
                    count++;
                }
            }
            Asserts.assertEquals(count, FRAME_COUNT);
            Asserts.assertEquals(frames.size(), FRAME_COUNT);
        }
    }

}
