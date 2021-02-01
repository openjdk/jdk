/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.tool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @summary Test jfr info
 * @key jfr
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.tool.TestMetadata
 */
public class TestMetadata {

    public static void main(String[] args) throws Throwable {
        testBasic();
        testEventTypeNum();
        testDeterministic();
        testWildcardAndAcronym();
    }

    static void testBasic() throws Throwable {
        Path f = ExecuteHelper.createProfilingRecording().toAbsolutePath();
        String file = f.toAbsolutePath().toString();

        OutputAnalyzer output = ExecuteHelper.jfr("metadata");
        output.shouldContain("@Name");
        output.shouldContain("jdk.jfr.Event");

        output = ExecuteHelper.jfr("metadata", "--wrongOption", file);
        output.shouldContain("unknown option --wrongOption");

        output = ExecuteHelper.jfr("metadata", file);
        try (RecordingFile rf = new RecordingFile(f)) {
            for (EventType t : rf.readEventTypes()) {
                String name = t.getName();
                name = name.substring(name.lastIndexOf(".") + 1);
                output.shouldContain(name);
            }
        }
        Set<String> annotations = new HashSet<>();
        int lineNumber = 1;
        for (String line : output.asLines()) {
            if (line.startsWith("@")) {
                if (annotations.contains(line)) {
                    throw new Exception("Line " + lineNumber + ":" +  line + " repeats annotation");
                }
                annotations.add(line);
            } else {
                annotations.clear();
            }
            lineNumber++;
        }
    }

    static void testEventTypeNum() throws Throwable {
        OutputAnalyzer output = ExecuteHelper.jfr("metadata");
        List<String> eventNames = new ArrayList<>();
        List<String> lines = output.asLines();

        for (String line : lines) {
            if (line.startsWith("@Name(\"")) {
                eventNames.add(line.substring(7, line.indexOf("\"", 7)));
            }
        }
        List<EventType> eventTypes = FlightRecorder.getFlightRecorder().getEventTypes();
        List<String> expectedNames = new ArrayList<>();
        for (EventType eventType : eventTypes) {
            expectedNames.add(eventType.getName());
        }
        Asserts.assertEQ(eventNames.size(), expectedNames.size());
    }

    static void testDeterministic() throws Throwable {
        OutputAnalyzer output = ExecuteHelper.jfr("metadata", "--events", "CPULoad,GarbageCollection");
        List<String> eventNames = new ArrayList<>();
        List<String> lines = output.asLines();

        for (String line : lines) {
            if (line.startsWith("@Name(\"")) {
                eventNames.add(line.substring(7, line.indexOf("\"", 7)));
            }
        }
        Asserts.assertEQ(eventNames.size(), 2);
    }

    static void testWildcardAndAcronym() throws Throwable {
        OutputAnalyzer output = ExecuteHelper.jfr("metadata", "--events", "Thread*");
        List<String> eventNames = new ArrayList<>();
        List<String> lines = output.asLines();
        for (String line : lines) {
            if (line.startsWith("@Name(\"")) {
                eventNames.add(line.substring(7, line.indexOf("\"", 7)));
            }
        }
        for (String eventName : eventNames) {
            Asserts.assertTrue(eventName.contains("Thread"));
        }

        output = ExecuteHelper.jfr("metadata", "--categories", "J*");
        lines = output.asLines();
        eventNames.clear();
        for (String line : lines) {
            if (line.startsWith("@Category(\"")) {
                eventNames.add(line.substring(11, line.indexOf("\"", 11)));
            }
        }
        for (String eventName : eventNames) {
            Asserts.assertTrue(eventName.startsWith("J"));
        }
    }
}