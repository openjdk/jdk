/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test TestMetaspaceFirstGC
 * @bug 8208250 8391711
 * @summary Verify that the first metaspace GC is triggered when metaspace reaches the MetaspaceSize threshold
 * @requires vm.hasJFR
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xms200m -XX:StartFlightRecording:name=startup TestMetaspaceFirstGC
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xms200m -XX:MetaspaceSize=10m -XX:StartFlightRecording:name=startup TestMetaspaceFirstGC 10m
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xms200m -XX:MetaspaceSize=50m -XX:StartFlightRecording:name=startup TestMetaspaceFirstGC 50m
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xms200m -XX:MetaspaceSize=99m -XX:StartFlightRecording:name=startup TestMetaspaceFirstGC 99m
 */

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.consumer.RecordingStream;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

public class TestMetaspaceFirstGC {

    private static int classCounter = 0;
    // kept alive so no collection can unload them before the threshold is reached
    private static final List<ClassLoader> loaders = new ArrayList<>();

    // Counted down from the JFR stream when a collection with cause "Metadata GC Threshold"
    // arrives, so the event is already in hand when loading stops.
    private static final CountDownLatch metadataGC = new CountDownLatch(1);

    public interface Dummy {}

    static class DummyHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        long expectedSize = -1;
        if (args.length > 0) {
            expectedSize = parseSize(args[0]);
        }

        List<RecordedEvent> events;
        try (RecordingStream rs = new RecordingStream()) {
            rs.enable(EventNames.GarbageCollection);
            rs.onEvent(EventNames.GarbageCollection, event -> {
                if ("Metadata GC Threshold".equals(event.getString("cause"))) {
                    metadataGC.countDown();
                }
            });
            // a concurrent collector requests the GC and expands right away, the GC itself may
            // be folded into a cycle that is already running and never carry the cause
            rs.enable(EventNames.MetaspaceGCThreshold);
            rs.onEvent(EventNames.MetaspaceGCThreshold, event -> {
                if ("expand_and_allocate".equals(event.getString("updater"))) {
                    metadataGC.countDown();
                }
            });
            rs.startAsync();
            Instant loadingStart = Instant.now();
            long tolerance = 5 * 1024 * 1024;
            long initialThreshold = WhiteBox.getWhiteBox().metaspaceCapacityUntilGC();
            System.out.println("Initial metaspace GC threshold: " + initialThreshold);
            if (expectedSize > 0) {
                if (initialThreshold > expectedSize + tolerance) {
                    // the threshold was already moved before loading started
                    throw new SkippedException("threshold already at " + initialThreshold
                        + ", above MetaspaceSize " + expectedSize);
                }
                Asserts.assertLessThanOrEqual(Math.abs(initialThreshold - expectedSize), tolerance,
                    "initial threshold (" + initialThreshold + ") should be close to MetaspaceSize (" + expectedSize + ")");
            } else {
                // No explicit MetaspaceSize, check default range (~12MB to ~20MB per tuning guide)
                Asserts.assertGreaterThan(initialThreshold, 11_500_000L, "default threshold (" + initialThreshold + ") too small");
                Asserts.assertLessThan(initialThreshold, 22_500_000L, "default threshold (" + initialThreshold + ") too large");
            }


            // Load classes until a metaspace-triggered GC happens
            try {
                loadClassesUntilGC(50000);
            } catch (RuntimeException e) {
                System.out.println("Threshold now " + WhiteBox.getWhiteBox().metaspaceCapacityUntilGC()
                    + ", metaspace used=" + getMetaspaceUsed());
                for (Recording r : FlightRecorder.getFlightRecorder().getRecordings()) {
                    if ("startup".equals(r.getName())) {
                        r.dump(Path.of("metaspace-first-gc-" + (expectedSize > 0 ? args[0] : "default") + "-failed.jfr"));
                    }
                }
                throw e;
            }
            loaders.clear();
            rs.stop();

            // The startup recording has run since VM start, so it holds the GC's "Before GC"
            // summary and the first threshold change even when the first metadata GC happens
            // during JFR initialization, before this stream existed.
            Recording startup = FlightRecorder.getFlightRecorder().getRecordings().stream()
                .filter(r -> "startup".equals(r.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("startup recording not found"));
            startup.stop();
            Path dump = Path.of("metaspace-first-gc-" + (expectedSize > 0 ? args[0] : "default") + ".jfr");
            startup.dump(dump);
            events = new ArrayList<>(RecordingFile.readAllEvents(dump));
            events.sort(Comparator.comparing(RecordedEvent::getStartTime));

            // The first metadata GC is requested at the threshold in effect at that moment. On a
            // concurrent collector the request shows up as an expand_and_allocate threshold change,
            // on the others as the GC itself, its threshold being the old value of the first change
            // at or after the GC start. Any earlier change since loading started came from another
            // collection's compute_new_size and can only have raised the threshold.
            RecordedEvent request = null;
            RecordedEvent metadataGc = null;
            for (RecordedEvent event : events) {
                if (!event.getStartTime().isAfter(loadingStart)) {
                    continue;
                }
                String type = event.getEventType().getName();
                if (request == null && type.equals(EventNames.MetaspaceGCThreshold)
                        && event.getString("updater").equals("expand_and_allocate")) {
                    request = event;
                }
                if (metadataGc == null && type.equals(EventNames.GarbageCollection)
                        && event.getString("cause").equals("Metadata GC Threshold")) {
                    metadataGc = event;
                }
            }
            Asserts.assertTrue(request != null || metadataGc != null, "no metadata GC request after loading started");
            Instant requestTime = request != null ? request.getStartTime() : metadataGc.getStartTime();
            long thresholdAtRequest = request != null ? request.getLong("oldValue") : -1;
            int changesBefore = 0;
            for (RecordedEvent event : events) {
                if (!event.getEventType().getName().equals(EventNames.MetaspaceGCThreshold)
                        || !event.getStartTime().isAfter(loadingStart)) {
                    continue;
                }
                if (event.getStartTime().isBefore(requestTime)) {
                    changesBefore++;
                    System.out.println("Threshold changed before the request: " + event.getLong("oldValue")
                        + " -> " + event.getLong("newValue") + " by " + event.getString("updater"));
                } else if (thresholdAtRequest < 0) {
                    thresholdAtRequest = event.getLong("oldValue");
                }
            }
            Asserts.assertNotEquals(thresholdAtRequest, -1L, "no threshold change after the metadata GC");
            System.out.println("Metadata GC requested at threshold " + thresholdAtRequest
                + (request != null ? " (expand_and_allocate)" : " (gcId=" + metadataGc.getLong("gcId") + ")"));
            if (changesBefore == 0) {
                Asserts.assertEquals(thresholdAtRequest, initialThreshold,
                    "the first metadata GC should have been requested at the initial threshold");
            } else {
                Asserts.assertGreaterThanOrEqual(thresholdAtRequest, initialThreshold,
                    "the threshold can only be raised before the first metadata GC");
            }

            System.out.println("PASSED");
        }
    }

    private static void loadClassesUntilGC(int maxIterations) throws InterruptedException {
        for (int i = 0; i < maxIterations; i++) {
            loadOneClass();
            if (metadataGC.getCount() == 0) {
                System.out.println("Metadata GC seen after " + (i + 1) + " class loads, metaspace used=" + getMetaspaceUsed());
                return;
            }
        }
        // a concurrent collector may still be running the collection
        if (metadataGC.await(60, TimeUnit.SECONDS)) {
            System.out.println("Metadata GC seen after " + maxIterations + " class loads, metaspace used=" + getMetaspaceUsed());
            return;
        }
        throw new RuntimeException("No metaspace GC after " + maxIterations + " class loads");
    }

    private static void loadOneClass() {
        try {
            String jarUrl = "file:" + (classCounter++) + ".jar";
            URLClassLoader cl = new URLClassLoader(new URL[]{new URL(jarUrl)});
            loaders.add(cl);
            Proxy.newProxyInstance(cl, new Class[]{Dummy.class}, new DummyHandler());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static long getMetaspaceUsed() {
        return ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(p -> p.getName().equals("Metaspace"))
            .mapToLong(p -> p.getUsage().getUsed())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Metaspace pool not found"));
    }

    private static long parseSize(String size) {
        size = size.toLowerCase();
        long multiplier = 1;
        if (size.endsWith("m")) {
            multiplier = 1024 * 1024;
            size = size.substring(0, size.length() - 1);
        } else if (size.endsWith("k")) {
            multiplier = 1024;
            size = size.substring(0, size.length() - 1);
        } else if (size.endsWith("g")) {
            multiplier = 1024 * 1024 * 1024;
            size = size.substring(0, size.length() - 1);
        }
        return Long.parseLong(size) * multiplier;
    }
}
