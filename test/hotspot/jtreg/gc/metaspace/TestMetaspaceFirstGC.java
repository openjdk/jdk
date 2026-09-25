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
 * @bug 8208250 8391711 8392597
 * @summary Verify that the first metadata GC request seen after startup is made when metaspace reaches the MetaspaceSize threshold
 * @requires vm.hasJFR
 * @requires vm.gc != "Shenandoah"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xms200m -XX:MinMetaspaceFreeRatio=0 -XX:StartFlightRecording:name=startup TestMetaspaceFirstGC
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xms200m -XX:MinMetaspaceFreeRatio=0 -XX:MetaspaceSize=10m -XX:StartFlightRecording:name=startup TestMetaspaceFirstGC 10m
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xms200m -XX:MinMetaspaceFreeRatio=0 -XX:MetaspaceSize=50m -XX:StartFlightRecording:name=startup TestMetaspaceFirstGC 50m
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xms200m -XX:MinMetaspaceFreeRatio=0 -XX:MetaspaceSize=99m -XX:StartFlightRecording:name=startup TestMetaspaceFirstGC 99m
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

import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.consumer.RecordingStream;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

public class TestMetaspaceFirstGC {

    private static int classCounter = 0;
    // written on the loading thread right before every class load, on the same clock as the
    // VM events, the one right before the failed allocation is the closest look at the request
    @Name("TestMetaspaceFirstGC.LoadSample")
    @StackTrace(false)
    private static class LoadSample extends Event {
        long committed;
        long threshold;
    }

    // the candidate allocation failure happened inside loadOneClass
    private static boolean fromLoadOneClass(RecordedEvent event) {
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null) {
            return false;
        }
        for (RecordedFrame frame : stackTrace.getFrames()) {
            if (frame.getMethod().getType().getName().equals("TestMetaspaceFirstGC")
                    && frame.getMethod().getName().equals("loadOneClass")) {
                return true;
            }
        }
        return false;
    }
    // kept alive so no collection can unload them before the threshold is reached
    private static final List<ClassLoader> loaders = new ArrayList<>();

    // Counted down from the JFR stream when a metadata allocation failure inside loadOneClass
    // arrives, whether it was the first request is decided afterwards from the recording.
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
            // a concurrent collector may fold the requested GC into a cycle that is already
            // running and never emit the cause, the failed allocation inside loadOneClass is the
            // candidate, whether a GC was really asked for is checked afterwards
            rs.enable(EventNames.MetaspaceAllocationFailure).withStackTrace();
            rs.onEvent(EventNames.MetaspaceAllocationFailure, event -> {
                if (fromLoadOneClass(event)) {
                    metadataGC.countDown();
                }
            });
            rs.startAsync();
            // load what the measured window touches before it opens, the recording is checked
            // afterwards for any other allocation failure inside it
            WhiteBox.getWhiteBox().metaspaceCapacityUntilGC();
            getMetaspaceCommitted();
            getMetaspaceUsed();
            new LoadSample().commit();
            Instant loadingStart = Instant.now();
            // metaspace commits in 64K granules and one class load allocates a few KB, the sample
            // is taken right before the load that failed
            long tolerance = 1024 * 1024;
            long initialThreshold = WhiteBox.getWhiteBox().metaspaceCapacityUntilGC();
            System.out.println("Initial metaspace GC threshold: " + initialThreshold);
            long metaspaceSize = WhiteBox.getWhiteBox().getSizeTVMFlag("MetaspaceSize");
            if (expectedSize > 0) {
                Asserts.assertEquals(metaspaceSize, expectedSize, "MetaspaceSize as set on the command line");
            } else {
                // the platform dependent ergonomic default
                Asserts.assertGreaterThan(metaspaceSize, 11_500_000L, "default MetaspaceSize (" + metaspaceSize + ") too small");
                Asserts.assertLessThan(metaspaceSize, 22_500_000L, "default MetaspaceSize (" + metaspaceSize + ") too large");
            }
            if (initialThreshold != metaspaceSize) {
                // startup already moved the threshold, the first metadata GC request can't be measured
                throw new SkippedException("threshold already at " + initialThreshold
                    + ", not MetaspaceSize " + metaspaceSize);
            }

            // Load classes until the metadata allocation failure shows up
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
            // one more sample closes the window of the last candidate before anything is released
            LoadSample end = new LoadSample();
            end.committed = getMetaspaceCommitted();
            end.threshold = WhiteBox.getWhiteBox().metaspaceCapacityUntilGC();
            end.commit();
            loaders.clear();
            rs.stop();

            // The startup recording begins during VM initialization, before this stream existed,
            // so it holds the earlier events too, anything before JFR started is out of reach.
            Recording startup = FlightRecorder.getFlightRecorder().getRecordings().stream()
                .filter(r -> "startup".equals(r.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("startup recording not found"));
            startup.stop();
            Path dump = Path.of("metaspace-first-gc-" + (expectedSize > 0 ? args[0] : "default") + ".jfr");
            startup.dump(dump);
            events = new ArrayList<>(RecordingFile.readAllEvents(dump));
            events.sort(Comparator.comparing(RecordedEvent::getStartTime));

            // The candidate is the first failed metadata allocation inside loadOneClass. A threshold
            // change or a metadata GC starting before the next sample shows the collector was asked,
            // the window may hold more failures and the collection may finish later. The sample before
            // the failing load gives committed and the threshold, committed has to be at the threshold
            // within the tolerance, below it the request was premature. A later threshold change is not
            // guaranteed, when there is one it starts at or above the sampled threshold. A failed
            // allocation elsewhere or a threshold change before the candidate skips the run, a sampled
            // threshold that differs from the initial one with no change recorded is a failure.
            // Shenandoah without class unloading expands without asking and is excluded.
            RecordedEvent request = null;
            int startupFailures = 0;
            int earlierFailures = 0;
            for (RecordedEvent event : events) {
                if (!event.getEventType().getName().equals(EventNames.MetaspaceAllocationFailure)) {
                    continue;
                }
                if (!event.getStartTime().isAfter(loadingStart)) {
                    startupFailures++;
                    continue;
                }
                if (fromLoadOneClass(event)) {
                    request = event;
                    break;
                }
                earlierFailures++;
            }
            Asserts.assertNotNull(request, "no metaspace allocation failure inside loadOneClass");
            Instant nextLoad = null;
            for (RecordedEvent event : events) {
                if (event.getEventType().getName().equals("TestMetaspaceFirstGC.LoadSample")
                        && event.getStartTime().isAfter(request.getStartTime())) {
                    nextLoad = event.getStartTime();
                    break;
                }
            }
            long thresholdAfterRequest = -1;
            boolean metadataGcSeen = false;
            boolean summaryLogged = false;
            int changesBefore = 0;
            for (RecordedEvent event : events) {
                if (!event.getStartTime().isAfter(loadingStart)) {
                    continue;
                }
                String type = event.getEventType().getName();
                boolean beforeRequest = event.getStartTime().isBefore(request.getStartTime());
                if (type.equals(EventNames.MetaspaceGCThreshold)) {
                    if (beforeRequest) {
                        changesBefore++;
                        System.out.println("Threshold changed before the candidate: " + event.getLong("oldValue")
                            + " -> " + event.getLong("newValue") + " by " + event.getString("updater"));
                    } else if (thresholdAfterRequest < 0
                            && (nextLoad == null || !event.getStartTime().isAfter(nextLoad))) {
                        thresholdAfterRequest = event.getLong("oldValue");
                    }
                } else if (type.equals(EventNames.GarbageCollection) && !beforeRequest
                        && (nextLoad == null || !event.getStartTime().isAfter(nextLoad))
                        && "Metadata GC Threshold".equals(event.getString("cause"))) {
                    metadataGcSeen = true;
                } else if (type.equals(EventNames.MetaspaceSummary) && !beforeRequest && !summaryLogged) {
                    summaryLogged = true;
                    System.out.println("Summary after the candidate: " + event.getString("when") + " gcId="
                        + event.getLong("gcId") + " committed=" + event.getLong("metaspace.committed")
                        + " gcThreshold=" + event.getLong("gcThreshold"));
                }
            }
            if (startupFailures > 0 || earlierFailures > 0 || changesBefore > 0) {
                throw new SkippedException(startupFailures + " allocation failures before loading started, "
                    + earlierFailures + " other allocation failures and " + changesBefore
                    + " threshold changes before the candidate, the first metadata GC request can't be measured");
            }
            if (thresholdAfterRequest < 0 && !metadataGcSeen) {
                // nothing shows a GC was asked for, the retry may have gone through without one
                throw new SkippedException("no threshold change and no metadata GC starting in the candidate's window");
            }
            RecordedEvent atRequest = null;
            for (RecordedEvent event : events) {
                if (!event.getEventType().getName().equals("TestMetaspaceFirstGC.LoadSample")) {
                    continue;
                }
                if (event.getStartTime().isAfter(request.getStartTime())) {
                    break;
                }
                atRequest = event;
            }
            Asserts.assertNotNull(atRequest, "no sample before the request");
            long committedAtRequest = atRequest.getLong("committed");
            long thresholdAtRequest = atRequest.getLong("threshold");
            System.out.println("Before the failing load: committed=" + committedAtRequest
                + " threshold=" + thresholdAtRequest
                + (thresholdAfterRequest < 0 ? ", no threshold change in the window" : ", next change in the window from " + thresholdAfterRequest));
            if (thresholdAfterRequest >= 0) {
                Asserts.assertGreaterThanOrEqual(thresholdAfterRequest, thresholdAtRequest,
                    "the threshold change after the request should start at or above the sampled threshold");
            }
            Asserts.assertLessThanOrEqual(Math.abs(thresholdAtRequest - committedAtRequest), tolerance,
                "committed before the request (" + committedAtRequest + ") should be at the threshold (" + thresholdAtRequest + ")");
            Asserts.assertEquals(thresholdAtRequest, initialThreshold,
                "the first metadata GC should have been requested at the initial threshold");

            System.out.println("PASSED");
        }
    }

    private static void loadClassesUntilGC(int maxIterations) throws InterruptedException {
        for (int i = 0; i < maxIterations; i++) {
            LoadSample sample = new LoadSample();
            sample.committed = getMetaspaceCommitted();
            sample.threshold = WhiteBox.getWhiteBox().metaspaceCapacityUntilGC();
            sample.commit();
            loadOneClass();
            if (metadataGC.getCount() == 0) {
                System.out.println("Metadata allocation failure seen after " + (i + 1) + " load attempts, metaspace used=" + getMetaspaceUsed());
                return;
            }
        }
        // the event may still be on its way from the stream
        if (metadataGC.await(60, TimeUnit.SECONDS)) {
            System.out.println("Metadata allocation failure seen after " + maxIterations + " load attempts, metaspace used=" + getMetaspaceUsed());
            return;
        }
        throw new RuntimeException("No metadata allocation failure inside loadOneClass after " + maxIterations + " load attempts");
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

    private static long getMetaspaceCommitted() {
        return ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(p -> p.getName().equals("Metaspace"))
            .mapToLong(p -> p.getUsage().getCommitted())
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Metaspace pool not found"));
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
