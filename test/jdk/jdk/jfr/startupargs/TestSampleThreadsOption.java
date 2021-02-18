/*
 * Copyright (c) 2021, Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Alibaba designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package jdk.jfr.startupargs;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib
 * @modules jdk.jfr/jdk.jfr.internal
 * @run main jdk.jfr.startupargs.TestSampleThreadsOption
 */
public class TestSampleThreadsOption {
    private static final String START_FLIGHT_RECORDING = "-XX:StartFlightRecording";
    private static final String FLIGHT_RECORDER_OPTIONS = "-XX:FlightRecorderOptions";

    public static void main(String[] args) throws Throwable {
        String recording = START_FLIGHT_RECORDING + "=filename=recording.jfr,dumponexit=true";

        // turn on
        List<RecordedEvent> events;
        events = testWithJFROption(FLIGHT_RECORDER_OPTIONS + "=samplethreads=true", recording, Main.class.getName());
        List<String> names = events.stream()
                .map(e -> e.getEventType().getName())
                .filter(eventName -> eventName.equals(EventNames.ExecutionSample) || eventName.equals(EventNames.NativeMethodSample))
                .collect(Collectors.toList());
        Asserts.assertTrue(names.size() != 0, "must be");

        // turn off
        events = testWithJFROption(FLIGHT_RECORDER_OPTIONS + "=samplethreads=false", recording, Main.class.getName());
        names = events.stream()
                .map(e -> e.getEventType().getName())
                .filter(eventName -> eventName.equals(EventNames.ExecutionSample) || eventName.equals(EventNames.NativeMethodSample))
                .collect(Collectors.toList());
        Asserts.assertTrue(names.size() == 0, "must be");

    }

    private static List<RecordedEvent> testWithJFROption(String... options) throws Throwable {
        ProcessBuilder pb = ProcessTools.createTestJvm(options);
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldHaveExitValue(0);

        Path dumpPath = Paths.get(".", "recording.jfr");
        Asserts.assertTrue(Files.isRegularFile(dumpPath), "No recording dumped " + dumpPath);
        List<RecordedEvent> events = RecordingFile.readAllEvents(dumpPath);
        return events;
    }

    private static class Main {
        public static void main(String... args) {
            for (int i = 0; i < 500000; i++) {
                System.out.println(new Object().hashCode() * new Object().hashCode());
            }
        }
    }
}