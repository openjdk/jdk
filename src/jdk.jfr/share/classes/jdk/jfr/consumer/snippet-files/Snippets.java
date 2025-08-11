/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.jfr.snippets.consumer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordingFile;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;
import jdk.jfr.Configuration;
import jdk.jfr.EventType;
import jdk.jfr.consumer.RecordedEvent;

public class Snippets {

    class PackageOverview {
        // @start region="PackageOverview"
        public static void main(String[] args) throws IOException {
            if (args.length != 1) {
                System.err.println("Must specify a recording file.");
                return;
            }

            RecordingFile.readAllEvents(Path.of(args[0])).stream()
                .filter(e -> e.getEventType().getName().equals("jdk.ExecutionSample"))
                .map(e -> e.getStackTrace())
                .filter(s -> s != null)
                .map(s -> s.getFrames().getFirst())
                .filter(f -> f.isJavaFrame())
                .map(f -> f.getMethod())
                .collect(
                    Collectors.groupingBy(m -> m.getType().getName() + "." + m.getName() + " " + m.getDescriptor(),
                    Collectors.counting()))
                .entrySet()
                .stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .forEach(e -> System.out.printf("%8d %s\n", e.getValue(), e.getKey()));
        }
        // @end
    }

    void EventStreamOverview() throws Exception {
        // @start region="EventStreamOverview"
        try (var es = EventStream.openRepository()) {
            es.onEvent("jdk.CPULoad", event -> {
                System.out.println("CPU Load " + event.getEndTime());
                System.out.println(" Machine total: " + 100 * event.getFloat("machineTotal") + "%");
                System.out.println(" JVM User: " + 100 * event.getFloat("jvmUser") + "%");
                System.out.println(" JVM System: " + 100 * event.getFloat("jvmSystem") + "%");
                System.out.println();
            });
            es.onEvent("jdk.GarbageCollection", event -> {
                System.out.println("Garbage collection: " + event.getLong("gcId"));
                System.out.println(" Cause: " + event.getString("cause"));
                System.out.println(" Total pause: " + event.getDuration("sumOfPauses"));
                System.out.println(" Longest pause: " + event.getDuration("longestPause"));
                System.out.println();
            });
            es.start();
        }
        // @end
    }

    class EventStreamMetadata {
        // @start region="EventStreamMetadata"
        static long count = 0;
        public static void main(String... args) throws IOException {
            Path file = Path.of(args[0]);
            String regExp = args[1];
            var pr = Pattern.compile(regExp).asMatchPredicate();
            try (var s = EventStream.openFile(file)) {
                s.setOrdered(false);
                s.onMetadata(metadata -> metadata.getAddedEventTypes()
                 .stream().map(EventType::getName).filter(pr)
                 .forEach(eventName -> s.onEvent(eventName, event -> count++)));
                s.start();
                System.out.println(count + " events matches " + regExp);
            }
        }
        // @end
    }

    class RecordingStreamMetadata {
        // @start region="RecordingStreamMetadata"
        static long count = 0;
        public static void main(String... args) throws Exception {
            String regExp = args[0];
            var pr = Pattern.compile(regExp).asMatchPredicate();
            Configuration c = Configuration.getConfiguration("default");
            try (var s = new RecordingStream(c)) {
                s.setOrdered(false);
                s.onMetadata(metadata -> metadata.getAddedEventTypes()
                 .stream().map(EventType::getName).filter(pr)
                 .forEach(eventName -> s.onEvent(eventName, event -> count++)));
                s.startAsync();
                System.out.println("Running recording for 5 s. Please wait.");
                s.awaitTermination(Duration.ofSeconds(5));
                System.out.println(count + " events matches " + regExp);
            }
        }
        // @end
    }

    void RecordingFileOverview() throws Exception {
        // @start region="RecordingFileOverview"
        try (RecordingFile recordingFile = new RecordingFile(Paths.get("recording.jfr"))) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                System.out.println(event);
            }
        }
        // @end
    }

    void RecordedObjectGetValue() {
        RecordedEvent event = null;
        // @start region="RecordedObjectGetValue"
        if (event.hasField("intValue")) {
            int intValue = event.getValue("intValue");
            System.out.println("Int value: " + intValue);
        }

        if (event.hasField("objectClass")) {
            RecordedClass clazz = event.getValue("objectClass");
            System.out.println("Class name: " + clazz.getName());
        }

        if (event.hasField("sampledThread")) {
            RecordedThread sampledThread = event.getValue("sampledThread");
            System.out.println("Sampled thread: " + sampledThread.getJavaName());
        }
        // @end
    }

    void RecordingStreamOverview() throws Exception {
        // @start region="RecordingStreamOverview"
        Configuration c = Configuration.getConfiguration("default");
        try (var rs = new RecordingStream(c)) {
            rs.onEvent("jdk.GarbageCollection", System.out::println);
            rs.onEvent("jdk.CPULoad", System.out::println);
            rs.onEvent("jdk.JVMInformation", System.out::println);
            rs.start();
        }
        // @end
    }

    void RecordingStreamConstructor() throws Exception {
        // @start region="RecordingStreamConstructor"
        var c = Configuration.getConfiguration("default");
        try (var rs = new RecordingStream(c)) {
            rs.onEvent(System.out::println);
            rs.start();
        }
        // @end
    }

    void RecordingStreamSetSettings() throws Exception {
        // @start region="RecordingStreamSetSettings"
        Configuration defaultConfiguration = Configuration.getConfiguration("default");
        Configuration profileConfiguration = Configuration.getConfiguration("profile");
        try (var rs = new RecordingStream(defaultConfiguration)) {
            rs.onEvent(System.out::println);
            rs.startAsync();
            Thread.sleep(20_000);
            rs.setSettings(profileConfiguration.getSettings());
            Thread.sleep(20_000);
        }
        // @end
    }

    void RecordingStreamStartAsync() throws Exception {
        // @start region="RecordingStreamStartAsync"
        try (var stream = new RecordingStream()) {
            stream.enable("jdk.CPULoad").withPeriod(Duration.ofSeconds(1));
            stream.onEvent("jdk.CPULoad", event -> {
                System.out.println(event);
            });
            stream.startAsync();
            Thread.sleep(10_000);
        }
        // @end
    }

    void RecordingStreamStop() throws Exception {
        // @start region="RecordingStreamStop"
        AtomicBoolean socketUse = new AtomicBoolean();
        try (var r = new RecordingStream()) {
            r.setMaxSize(Long.MAX_VALUE);
            r.enable("jdk.SocketWrite").withoutThreshold();
            r.enable("jdk.SocketRead").withoutThreshold();
            r.onEvent(event -> socketUse.set(true));
            r.startAsync();
            testFoo();
            r.stop();
            if (socketUse.get()) {
                r.dump(Path.of("socket-events.jfr"));
                throw new AssertionError("testFoo() should not use network");
            }
        }
        // @end
    }

    void testFoo() {
    }
}
