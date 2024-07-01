/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8333086
 * @summary Verify the JLine backend is not initialized for simple printing.
 * @enablePreview
 * @modules jdk.internal.le/jdk.internal.org.jline.reader
 *          jdk.internal.le/jdk.internal.org.jline.terminal
 * @library /test/lib
 * @run main LazyJdkConsoleProvider
 */

import java.io.IO;
import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.terminal.Terminal;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class LazyJdkConsoleProvider {

    public static void main(String... args) throws Throwable {
        switch (args.length > 0 ? args[0] : "default") {
            case "write" -> {
                System.console().println("Hello!");
                System.console().print("Hello!");
                System.console().format("\nHello!\n");
                System.console().flush();
                IO.println("Hello!");
                IO.print("Hello!");
            }
            case "read" -> System.console().readLine("Hello!");
            case "IO-read" -> {
                IO.readln("Hello!");
            }
            case "default" -> {
                new LazyJdkConsoleProvider().runTest();
            }
        }
    }

    void runTest() throws Exception {
        record TestCase(String testKey, String expected, String notExpected) {}
        TestCase[] testCases = new TestCase[] {
            new TestCase("write", null, Terminal.class.getName()),
            new TestCase("read", LineReader.class.getName(), null),
            new TestCase("IO-read", LineReader.class.getName(), null)
        };
        for (TestCase tc : testCases) {
            ProcessBuilder builder =
                    ProcessTools.createTestJavaProcessBuilder("--enable-preview",
                                                              "-verbose:class",
                                                              "-Djdk.console=jdk.internal.le",
                                                              LazyJdkConsoleProvider.class.getName(),
                                                              tc.testKey());
            OutputAnalyzer output = ProcessTools.executeProcess(builder, "");

            output.waitFor();

            if (output.getExitValue() != 0) {
                throw new AssertionError("Unexpected return value: " + output.getExitValue() +
                                         ", actualOut: " + output.getStdout() +
                                         ", actualErr: " + output.getStderr());
            }
            if (tc.expected() != null) {
                output.shouldContain(tc.expected());
            }

            if (tc.notExpected() != null) {
                output.shouldNotContain(tc.notExpected());
            }
        }
    }

}
