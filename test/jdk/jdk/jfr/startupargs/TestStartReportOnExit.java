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

package jdk.jfr.startupargs;

import java.nio.file.Files;
import java.nio.file.Path;

import jdk.jfr.consumer.RecordingFile;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * @test
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main jdk.jfr.startupargs.TestStartReportOnExit
 */
public class TestStartReportOnExit {

    public static void main(String[] args) throws Exception {
        testSingleReport();
        testMultipleReports();
        testMultipleRecordings();
        testUnstopped();
        testInvalidName();
        testWithDump();
        testWithMemory();
    }

    private static void testSingleReport() throws Exception {
        OutputAnalyzer out = launch("-XX:StartFlightRecording:report-on-exit=jvm-information", "-version");
        expectJVMInformation(out);
        out.shouldHaveExitValue(0);
    }

    private static void testMultipleReports() throws Exception {
        OutputAnalyzer out = launch("-XX:StartFlightRecording:report-on-exit=jvm-information,report-on-exit=system-properties", "-version");
        expectJVMInformation(out);
        expectSystemProperties(out);
        out.shouldHaveExitValue(0);
    }

    private static void testMultipleRecordings() throws Exception {
        OutputAnalyzer out = launch("-XX:StartFlightRecording:report-on-exit=jvm-information", "-XX:StartFlightRecording:report-on-exit=system-properties", "-version");
        expectJVMInformation(out);
        expectSystemProperties(out);
        out.shouldHaveExitValue(0);
    }

    private static void testUnstopped() throws Exception {
        OutputAnalyzer out = launch("-XX:StartFlightRecording:delay=20h,report-on-exit=jvm-information", "-version");
        out.shouldNotContain("JVM Information");
        out.shouldHaveExitValue(0);
    }

    private static void testInvalidName() throws Exception {
        OutputAnalyzer out = launch("-XX:StartFlightRecording:report-on-exit=invalid", "-version");
        out.shouldContain("Unknown view 'invalid' specified for report-on-exit");
        out.shouldNotHaveExitValue(0);
    }

    private static void testWithDump() throws Exception {
        Path path = Path.of("dump.jfr").toAbsolutePath();
        OutputAnalyzer out = launch("-XX:StartFlightRecording:filename=" + path.toString() + ",report-on-exit=jvm-information", "-version");
        expectJVMInformation(out);
        out.shouldHaveExitValue(0);
        RecordingFile.readAllEvents(path);
    }

    private static void testWithMemory() throws Exception {
        OutputAnalyzer out = launch("-XX:StartFlightRecording:report-on-exit=jvm-information,disk=false", "-version");
        out.shouldContain("Option report-on-exit can only be used when recording to disk.");
        out.shouldNotHaveExitValue(0);
    }

    private static void expectJVMInformation(OutputAnalyzer out) throws Exception {
        out.shouldContain("JVM Information");
        out.shouldContain("PID:");
        out.shouldContain("Program Arguments:");
    }

    private static void expectSystemProperties(OutputAnalyzer out) throws Exception {
        out.shouldContain("System Properties at Startup");
        out.shouldContain("java.vm.specification.name");
    }

    private static OutputAnalyzer launch(String... arguments) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(arguments);
        return ProcessTools.executeProcess(pb);
    }
}
