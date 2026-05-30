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

/**
 * @test
 * @bug 8373578
 * @summary Verify that the JFR CPU-time thread sampler and the JVMTI
 *          RequestStackTrace extension function work independently.
 *          Tests all four combinations: both disabled, each enabled
 *          separately, and both enabled simultaneously.
 * @requires vm.hasJFR & vm.jvmti & os.family == "linux"
 * @library /test/lib
 * @run main/othervm/native/timeout=60 -agentlib:TestSamplerIndependence TestSamplerIndependence
 */

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.Events;

public class TestSamplerIndependence {

    static final String CPU_TIME_SAMPLE = "jdk.CPUTimeSample";
    static final String ASYNC_STACK_TRACE = "jdk.AsyncStackTrace";

    // Duration in ms for CPU-intensive workloads.
    static final int WORKLOAD_DURATION_MS = 1000;

    // Duration in ms over which JVMTI stack trace requests are emitted.
    static final int JVMTI_REQUEST_DURATION_MS = 1000;

    // Grace period in ms for async processing of queued requests.
    static final int ASYNC_GRACE_MS = 500;

    // Sentinel value to identify our JVMTI-requested events.
    static final long MAGIC_USER_DATA = 0xCAFEBABEL;

    static native void enableJvmtiStackTrace();
    static native void disableJvmtiStackTrace();
    static native int requestStackTrace(long userData);

    public static void main(String[] args) throws Exception {
        System.loadLibrary("TestSamplerIndependence");

        testBothDisabled();
        testCpuTimeOnly();
        testJvmtiOnly();
        testBothEnabled();

        System.out.println("All scenarios passed.");
    }

    /**
     * Scenario 1: Both disabled.
     * Neither the JFR CPUTimeSample event nor JVMTI RequestStackTrace is active.
     * Expects zero events of both types.
     */
    static void testBothDisabled() throws Exception {
        System.out.println("=== Scenario 1: Both disabled ===");
        try (Recording recording = new Recording()) {
            // Enable event types in JFR so they would appear IF generated,
            // but do not activate the mechanisms that produce them.
            recording.enable(ASYNC_STACK_TRACE);
            // Deliberately do NOT enable CPU_TIME_SAMPLE (sampler won't start).
            // Deliberately do NOT call enableJvmtiStackTrace().
            recording.start();

            wasteCPU(WORKLOAD_DURATION_MS);

            recording.stop();

            List<RecordedEvent> events = Events.fromRecording(recording);
            long cpuCount = countEvents(events, CPU_TIME_SAMPLE);
            long asyncCount = countEvents(events, ASYNC_STACK_TRACE);

            Asserts.assertEquals(cpuCount, 0L,
                "Scenario 1: expected 0 CPUTimeSample events, got " + cpuCount);
            Asserts.assertEquals(asyncCount, 0L,
                "Scenario 1: expected 0 AsyncStackTrace events, got " + asyncCount);
        }
        System.out.println("Scenario 1 passed.");
    }

    /**
     * Scenario 2: CPU-time sampler only.
     * The JFR CPUTimeSample event is enabled with a fast throttle.
     * JVMTI RequestStackTrace is NOT enabled.
     * Expects CPUTimeSample events but zero AsyncStackTrace events.
     */
    static void testCpuTimeOnly() throws Exception {
        System.out.println("=== Scenario 2: CPU-time sampler only ===");
        try (Recording recording = new Recording()) {
            recording.enable(CPU_TIME_SAMPLE).with("throttle", "1ms");
            recording.enable(ASYNC_STACK_TRACE);
            // Do NOT call enableJvmtiStackTrace().
            recording.start();

            wasteCPU(WORKLOAD_DURATION_MS);

            recording.stop();

            List<RecordedEvent> events = Events.fromRecording(recording);
            long cpuCount = countEvents(events, CPU_TIME_SAMPLE);
            long asyncCount = countEvents(events, ASYNC_STACK_TRACE);

            Asserts.assertGreaterThan(cpuCount, 0L,
                "Scenario 2: expected >0 CPUTimeSample events, got " + cpuCount);
            Asserts.assertEquals(asyncCount, 0L,
                "Scenario 2: expected 0 AsyncStackTrace events, got " + asyncCount);
        }
        System.out.println("Scenario 2 passed.");
    }

    /**
     * Scenario 3: JVMTI RequestStackTrace only.
     * The JFR CPUTimeSample event is NOT enabled (sampler does not start).
     * JVMTI RequestStackTrace is enabled and requests are emitted.
     * Expects AsyncStackTrace events but zero CPUTimeSample events.
     */
    static void testJvmtiOnly() throws Exception {
        System.out.println("=== Scenario 3: JVMTI only ===");
        try (Recording recording = new Recording()) {
            // Do NOT enable CPU_TIME_SAMPLE.
            recording.enable(ASYNC_STACK_TRACE);
            recording.start();

            enableJvmtiStackTrace();
            Thread.sleep(100); // Allow sampler thread to initialize.

            int emitted = emitJvmtiRequests(JVMTI_REQUEST_DURATION_MS);
            System.out.println("  Emitted " + emitted + " JVMTI requests.");
            Thread.sleep(ASYNC_GRACE_MS); // Allow async processing.

            disableJvmtiStackTrace();
            recording.stop();

            List<RecordedEvent> events = Events.fromRecording(recording);
            long cpuCount = countEvents(events, CPU_TIME_SAMPLE);
            long asyncCount = countEvents(events, ASYNC_STACK_TRACE);

            Asserts.assertEquals(cpuCount, 0L,
                "Scenario 3: expected 0 CPUTimeSample events, got " + cpuCount);
            Asserts.assertGreaterThan(asyncCount, 0L,
                "Scenario 3: expected >0 AsyncStackTrace events, got " + asyncCount);
        }
        System.out.println("Scenario 3 passed.");
    }

    /**
     * Scenario 4: Both enabled simultaneously.
     * The JFR CPUTimeSample event is enabled and JVMTI RequestStackTrace
     * is active concurrently.
     * Expects events of both types.
     */
    static void testBothEnabled() throws Exception {
        System.out.println("=== Scenario 4: Both enabled ===");
        try (Recording recording = new Recording()) {
            recording.enable(CPU_TIME_SAMPLE).with("throttle", "1ms");
            recording.enable(ASYNC_STACK_TRACE);
            recording.start();

            enableJvmtiStackTrace();
            Thread.sleep(100); // Allow sampler thread to initialize.

            // Run CPU workload on a separate thread while emitting JVMTI
            // requests from the main thread.
            Thread worker = new Thread(
                () -> wasteCPU(WORKLOAD_DURATION_MS), "cpu-worker");
            worker.start();

            int emitted = emitJvmtiRequests(JVMTI_REQUEST_DURATION_MS);
            System.out.println("  Emitted " + emitted + " JVMTI requests.");

            worker.join();
            Thread.sleep(ASYNC_GRACE_MS); // Allow async processing.

            disableJvmtiStackTrace();
            recording.stop();

            List<RecordedEvent> events = Events.fromRecording(recording);
            long cpuCount = countEvents(events, CPU_TIME_SAMPLE);
            long asyncCount = countEvents(events, ASYNC_STACK_TRACE);

            Asserts.assertGreaterThan(cpuCount, 0L,
                "Scenario 4: expected >0 CPUTimeSample events, got " + cpuCount);
            Asserts.assertGreaterThan(asyncCount, 0L,
                "Scenario 4: expected >0 AsyncStackTrace events, got " + asyncCount);
        }
        System.out.println("Scenario 4 passed.");
    }

    // ---- Helpers ----

    /**
     * Counts events of a given type from a pre-read event list.
     */
    static long countEvents(List<RecordedEvent> events, String eventName) {
        return events.stream()
                .filter(e -> e.getEventType().getName().equals(eventName))
                .count();
    }

    /**
     * Burns CPU for the specified duration (measured in CPU time, not wall time).
     * This produces a workload that the CPU-time thread sampler can observe.
     */
    static void wasteCPU(int durationMs) {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long start = bean.getCurrentThreadCpuTime();
        double x = 0;
        while (bean.getCurrentThreadCpuTime() - start < durationMs * 1_000_000L) {
            for (int j = 0; j < 100_000; j++) {
                x = Math.sqrt(x * Math.pow(Math.sqrt(Math.random()), Math.random()));
            }
        }
    }

    /**
     * Emits JVMTI RequestStackTrace calls over the given duration in milliseconds.
     * Between requests, performs some Java computation to allow the sampler thread
     * to process queued requests asynchronously.
     * Returns the number of successfully emitted requests.
     */
    static int emitJvmtiRequests(int durationMs) {
        long end = System.currentTimeMillis() + durationMs;
        int count = 0;
        while (System.currentTimeMillis() < end) {
            int err = requestStackTrace(MAGIC_USER_DATA + count);
            if (err == 0) {
                count++;
            }
            // Interleave Java work so the thread transitions back from native,
            // giving the sampler thread a chance to process pending requests.
            double x = 0;
            for (int j = 0; j < 5_000; j++) {
                x = Math.sqrt(x + 1);
            }
        }
        return count;
    }
}
