/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jfr.tool;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
/**
 * @test
 * @summary Test jfr view
 * @key jfr
 * @requires vm.hasJFR
 * @requires (vm.gc == "G1" | vm.gc == null)
 *           & vm.opt.ExplicitGCInvokesConcurrent != false
 * @library /test/lib /test/jdk
 * @run main/othervm -XX:-ExplicitGCInvokesConcurrent -XX:-DisableExplicitGC
 *                   -XX:+UseG1GC jdk.jfr.tool.TestView
 */
public class TestView {

    public static void main(String... args) throws Throwable {
        testIncorrectUsage();
        String recordingFile = ExecuteHelper.createProfilingRecording().toAbsolutePath().toString();
        testEventType(recordingFile);
        testFormView(recordingFile);
        testTableView(recordingFile);
    }

    private static void testIncorrectUsage() throws Throwable {
        OutputAnalyzer output = ExecuteHelper.jfr("view");
        output.shouldContain("missing file");

        output = ExecuteHelper.jfr("view", "missing.jfr");
        output.shouldContain("could not open file ");

        Path file = Utils.createTempFile("faked-file", ".jfr");
        FileWriter fw = new FileWriter(file.toFile());
        fw.write('d');
        fw.close();
        output = ExecuteHelper.jfr("view", "--wrongOption", file.toAbsolutePath().toString());
        output.shouldContain("unknown option");
        Files.delete(file);
    }

    private static void testFormView(String recording) throws Throwable {
        OutputAnalyzer output = ExecuteHelper.jfr("view", "jvm-information", recording);
        // Verify title
        output.shouldContain("JVM Information");
        // Verify field label
        output.shouldContain("VM Arguments:");
        // Verify field value
        long pid = ProcessHandle.current().pid();
        String lastThreeDigits = String.valueOf(pid % 1000);
        output.shouldContain(lastThreeDigits);
    }

    private static void testTableView(String recording) throws Throwable {
        OutputAnalyzer output = ExecuteHelper.jfr("view", "--verbose", "gc", recording);
        // Verify heading
        output.shouldContain("Longest Pause");
        // Verify verbose heading
        output.shouldContain("(longestPause)");
        // Verify row contents
        output.shouldContain("Old Garbage Collection");
        // Verify verbose query
        output.shouldContain("SELECT");
    }

    private static void testEventType(String recording) throws Throwable {
        OutputAnalyzer output = ExecuteHelper.jfr(
             "view", "--verbose", "--width", "300", "--cell-height", "100", "ThreadSleep", recording);
        // Verify title
        output.shouldContain("Thread Sleep");
        // Verify headings
        output.shouldContain("Sleep Time");
        // Verify verbose headings
        output.shouldContain("time");
        // Verify thread value
        output.shouldContain(Thread.currentThread().getName());
        // Verify stack frame
        output.shouldContain("ExecuteHelper.createProfilingRecording");
    }
}
