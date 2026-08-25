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

package jdk.jfr.event.profiling;

import java.util.List;
import java.util.zip.Deflater;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/*
 * @test
 * @summary Tests that jdk.CPUTimeSample records native frames when the
 *          nativeStack setting is enabled, and does not record them otherwise
 * @requires vm.hasJFR & os.family == "linux"
 * @library /test/lib
 * @run main/othervm jdk.jfr.event.profiling.TestCPUTimeSampleNativeStack
 */
public class TestCPUTimeSampleNativeStack {

    private static final String EVENT_NAME = EventNames.CPUTimeSample;
    private static final long RECORDING_DURATION_MS = 2000;

    private static volatile boolean alive = true;

    public static void main(String[] args) throws Exception {
        Thread worker = new Thread(TestCPUTimeSampleNativeStack::nativeWorkload, "NativeWorker");
        worker.setDaemon(true);
        worker.start();

        try {
            testWithNativeStack();
            testWithoutNativeStack();
        } finally {
            alive = false;
        }
    }

    // Spend CPU time inside libzip/libz
    private static void nativeWorkload() {
        byte[] input = new byte[64 * 1024];
        byte[] output = new byte[128 * 1024];
        try (Deflater deflater = new Deflater()) {
            while (alive) {
                deflater.reset();
                deflater.setInput(input);
                deflater.finish();
                deflater.deflate(output);
            }
        }
    }

    private static List<RecordedEvent> record(boolean nativeStack) throws Exception {
        try (Recording r = new Recording()) {
            r.enable(EVENT_NAME)
                    .with("throttle", "1ms")
                    .with("nativeStack", String.valueOf(nativeStack));
            r.start();
            Thread.sleep(RECORDING_DURATION_MS);
            r.stop();
            return Events.fromRecording(r);
        }
    }

    private static void testWithNativeStack() throws Exception {
        List<RecordedEvent> events = record(true);
        Asserts.assertFalse(events.isEmpty(), "No events recorded");

        int eventsWithNativeFrames = 0;
        boolean resolvedSymbol = false;

        for (RecordedEvent e : events) {
            RecordedStackTrace st = e.getStackTrace();
            if (st == null || !"NativeWorker".equals(e.getThread().getJavaName())) {
                continue;
            }

            List<RecordedFrame> frames = st.getFrames();
            boolean hasJavaFrames = false;
            boolean hasNativeFrames = false;
            boolean foundWorkloadMethod = false;
            for (RecordedFrame frame : frames) {
                if (frame.isJavaFrame()) {
                    hasJavaFrames = true;
                    Asserts.assertNotNull(frame.getMethod(), "Java frames have a method");
                    Asserts.assertNull(frame.getValue("nativeFunction"), "Java frames never have nativeFunction");
                    foundWorkloadMethod |= "nativeWorkload".equals(frame.getMethod().getName());
                } else {
                    hasNativeFrames = true;
                    Asserts.assertFalse(hasJavaFrames, "Native frames must precede Java frames");
                    Asserts.assertNull(frame.getMethod(), "Native frames have no method");
                    Asserts.assertNotNull(frame.getValue("nativeFunction"), "Native frames always have nativeFunction");
                    Asserts.assertEquals(frame.getType(), "Non-Java", "Unexpected frame type");
                    resolvedSymbol |= checkNativeFunction(frame.getValue("nativeFunction"));
                }
            }

            if (hasNativeFrames) {
                eventsWithNativeFrames++;
                Asserts.assertTrue(foundWorkloadMethod, "Unexpected Java stack trace");
            }
        }

        Asserts.assertGreaterThan(eventsWithNativeFrames, 0, "No events with native frames");
        Asserts.assertTrue(resolvedSymbol, "No resolved symbols");
    }

    private static void testWithoutNativeStack() throws Exception {
        List<RecordedEvent> events = record(false);
        Asserts.assertFalse(events.isEmpty(), "No events recorded");

        for (RecordedEvent e : events) {
            RecordedStackTrace st = e.getStackTrace();
            if (st == null) {
                continue;
            }
            for (RecordedFrame frame : st.getFrames()) {
                Asserts.assertTrue(frame.isJavaFrame(), "No native frames expected: " + frame);
            }
        }
    }

    private static boolean checkNativeFunction(RecordedObject function) {
        String name = function.getString("name");
        Asserts.assertNotNull(name, "Name must not be null");
        long offset = function.getLong("offset");
        Asserts.assertGreaterThanOrEqual(offset, 0L, "Offset must be non-negative");

        RecordedObject library = function.getValue("library");
        if (library != null) {
            String libName = library.getString("name");
            Asserts.assertTrue(libName != null && !libName.isEmpty(), "Library must have a name");
            Asserts.assertNotEquals(library.getLong("baseAddress"), 0L, "Library must have a base address");
            String buildId = library.getString("buildId");
            Asserts.assertNotNull(buildId, "Library must have a build id");
            Asserts.assertTrue(buildId.matches("([0-9a-f]{2})*"), "Build id must be a hex string" + buildId);
        }
        return !name.isEmpty() && library != null;
    }
}
