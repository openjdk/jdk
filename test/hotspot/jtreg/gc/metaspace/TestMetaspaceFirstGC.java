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
 * @run main/othervm -Xms200m -XX:StartFlightRecording:name=startup TestMetaspaceFirstGC
 * @run main/othervm -Xms200m -XX:MetaspaceSize=10m -XX:StartFlightRecording:name=startup TestMetaspaceFirstGC 10m
 * @run main/othervm -Xms200m -XX:MetaspaceSize=50m -XX:StartFlightRecording:name=startup TestMetaspaceFirstGC 50m
 * @run main/othervm -Xms200m -XX:MetaspaceSize=99m -XX:StartFlightRecording:name=startup TestMetaspaceFirstGC 99m
 */

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
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
import jtreg.SkippedException;

public class TestMetaspaceFirstGC {

    private static int classCounter = 0;

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

        long committedAtStart = ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(p -> p.getName().equals("Metaspace"))
            .mapToLong(p -> p.getUsage().getCommitted())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Metaspace pool not found"));
        System.out.println("Metaspace committed at start: " + committedAtStart);
        if (expectedSize > 0 && committedAtStart >= expectedSize) {
            // the first metadata GC already happened during VM startup, nothing left to observe
            throw new SkippedException("metaspace committed at start (" + committedAtStart
                + ") already at MetaspaceSize (" + expectedSize + ")");
        }

        List<RecordedEvent> events;
        try (RecordingStream rs = new RecordingStream()) {
            rs.enable(EventNames.GarbageCollection);
            rs.onEvent(EventNames.GarbageCollection, event -> {
                if ("Metadata GC Threshold".equals(event.getString("cause"))) {
                    metadataGC.countDown();
                }
            });
            rs.startAsync();

            // Load classes until a metaspace-triggered GC happens
            loadClassesUntilGC(50000);
            rs.stop();

            // The startup recording has run since VM start, so it holds the GC's "Before GC"
            // summary and the first threshold change even when the first metadata GC happens
            // during JFR initialization, before this stream existed.
            Recording startup = FlightRecorder.getFlightRecorder().getRecordings().stream()
                .filter(r -> "startup".equals(r.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("startup recording not found"));
            startup.stop();
            Path dump = Path.of("metaspace-first-gc.jfr");
            startup.dump(dump);
            events = new ArrayList<>(RecordingFile.readAllEvents(dump));
            events.sort(Comparator.comparing(RecordedEvent::getStartTime));

            // Find first GarbageCollection with cause "Metadata GC Threshold"
            RecordedEvent gcEvent = null;
            for (RecordedEvent event : events) {
                if (event.getEventType().getName().equals(EventNames.GarbageCollection)) {
                    String cause = event.getString("cause");
                    if ("Metadata GC Threshold".equals(cause)) {
                        gcEvent = event;
                        break;
                    }
                }
            }

            if (gcEvent == null) {
                throw new RuntimeException("No GC with cause 'Metadata GC Threshold' found");
            }

            int gcId = gcEvent.getInt("gcId");
            System.out.println("Found Metadata GC Threshold GC, gcId=" + gcId);

            // Find matching MetaspaceSummary with same gcId and when="Before GC"
            RecordedEvent msEvent = null;
            for (RecordedEvent event : events) {
                if (event.getEventType().getName().equals(EventNames.MetaspaceSummary)) {
                    if (event.getInt("gcId") == gcId && "Before GC".equals(event.getString("when"))) {
                        msEvent = event;
                        break;
                    }
                }
            }

            if (msEvent == null) {
                for (RecordedEvent e : events) {
                    if (e.getEventType().getName().equals(EventNames.MetaspaceSummary)) {
                        System.out.println("MetaspaceSummary gcId=" + e.getInt("gcId") + " when=" + e.getString("when"));
                    }
                }
                throw new RuntimeException("No MetaspaceSummary 'Before GC' found for gcId=" + gcId);
            }

            long committed = msEvent.getLong("metaspace.committed");
            long gcThreshold = msEvent.getLong("gcThreshold");
            System.out.println("MetaspaceSummary: committed=" + committed + " gcThreshold=" + gcThreshold);

            // committed should be reasonably close to gcThreshold
            long tolerance = 5 * 1024 * 1024; // 5MB tolerance
            Asserts.assertLessThanOrEqual(Math.abs(committed - gcThreshold), tolerance,
                "committed (" + committed + ") should be close to gcThreshold (" + gcThreshold + ")");
            // The threshold at which the first metadata GC was requested. A concurrent collector
            // expands it and keeps allocating during the GC, the earliest change keeps the original value.
            long firstThreshold = gcThreshold;
            for (RecordedEvent event : events) {
                if (event.getEventType().getName().equals(EventNames.MetaspaceGCThreshold)) {
                    firstThreshold = event.getLong("oldValue");
                    System.out.println("First threshold change: " + firstThreshold + " -> "
                        + event.getLong("newValue") + " by " + event.getString("updater"));
                    break;
                }
            }

            // If explicit MetaspaceSize given, the first GC must have been triggered at it
            if (expectedSize > 0) {
                Asserts.assertLessThanOrEqual(Math.abs(firstThreshold - expectedSize), tolerance,
                    "first threshold (" + firstThreshold + ") should be close to MetaspaceSize (" + expectedSize + ")");
                System.out.println("first threshold matches expected MetaspaceSize=" + expectedSize);
            } else {
                // No explicit MetaspaceSize, check default range (~12MB to ~20MB per tuning guide)
                Asserts.assertGreaterThan(firstThreshold, 11_500_000L,
                    "default threshold (" + firstThreshold + ") too small");
                Asserts.assertLessThan(firstThreshold, 22_500_000L,
                    "default threshold (" + firstThreshold + ") too large");
                System.out.println("first threshold in expected default range");
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
