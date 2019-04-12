/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Paths;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;


// This class is intended to run inside a container
public class JfrReporter {
    public static final String TEST_REPORTED_CORES="TEST_REPORTED_CORES";
    public static final String TEST_REPORTED_MEMORY="TEST_REPORTED_MEMORY";
    public static final String TEST_REPORTED_PID="TEST_REPORTED_PID";
    public static final String TESTCASE_CPU="cpu";
    public static final String TESTCASE_MEMORY="memory";
    public static final String TESTCASE_PROCESS="process";

    public static void main(String[] args) throws Exception {
        String testCase = args[0];
        System.out.println("Testcase: " + testCase);
        switch (testCase) {
        case TESTCASE_CPU:
            RecordedEvent event = testEvent("jdk.CPUInformation", "cpu.jfr");
            System.out.println(TEST_REPORTED_CORES + "=" + event.getInt("cores"));
            break;
        case TESTCASE_MEMORY:
            event = testEvent("jdk.PhysicalMemory", "memory.jfr");
            System.out.println(TEST_REPORTED_MEMORY + "=" + event.getLong("totalSize"));
            break;
        case TESTCASE_PROCESS:
            event = testEvent("jdk.SystemProcess", "process.jfr");
            System.out.println(TEST_REPORTED_PID + "=" + event.getString("pid"));
            break;
        default:
            throw new IllegalArgumentException("Invalid test case");
        }
    }

    private static RecordedEvent testEvent(String event, String recordingPath) throws Exception {
        System.out.println("========= Testing event: " + event);
        Recording r = new Recording();
        r.enable(event);
        r.setDestination(Paths.get("tmp", recordingPath));
        r.start();
        r.stop();

        RecordedEvent recordedEvent = RecordingFile.readAllEvents(r.getDestination()).get(0);
        System.out.println("RecordedEvent: " + recordedEvent);
        return recordedEvent;
    }
}
