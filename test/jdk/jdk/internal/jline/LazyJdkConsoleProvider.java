/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8333086 8344706 8361613
 * @summary Verify the JLine backend is not initialized for simple printing.
 * @modules java.base/jdk.internal.io
 *          jdk.internal.le/jdk.internal.org.jline
 *          jdk.internal.le/jdk.internal.org.jline.reader
 *          jdk.internal.le/jdk.internal.org.jline.terminal
 * @library /test/lib
 * @run main LazyJdkConsoleProvider
 */

import java.nio.charset.StandardCharsets;

import jdk.internal.org.jline.JdkConsoleProviderImpl;
import jdk.internal.org.jline.reader.LineReader;
import jdk.internal.org.jline.terminal.Terminal;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class LazyJdkConsoleProvider {

    public static void main(String... args) throws Throwable {
        // directly instantiate JLine JdkConsole, simulating isTTY=true
        switch (args.length > 0 ? args[0] : "default") {
            case "write" -> {
                var impl = new JdkConsoleProviderImpl().console(true, StandardCharsets.UTF_8, StandardCharsets.UTF_8);
                impl.println("Hello!\n");
                impl.println("Hello!");
                impl.format(null, "\nHello!\n");
                impl.flush();
            }
            case "read" -> new JdkConsoleProviderImpl()
                .console(true, StandardCharsets.UTF_8, StandardCharsets.UTF_8)
                .readLine(null, "Hello!");
            case "default" -> {
                new LazyJdkConsoleProvider().runTest();
            }
        }
    }

    void runTest() throws Exception {
        record TestCase(String testKey, String expected, String notExpected) {}
        TestCase[] testCases = new TestCase[] {
            new TestCase("write", null, Terminal.class.getName()),
            new TestCase("read", LineReader.class.getName(), null)
        };
        for (TestCase tc : testCases) {
            ProcessBuilder builder =
                    ProcessTools.createTestJavaProcessBuilder("-verbose:class",
                                                              "--add-exports",
                                                              "java.base/jdk.internal.io=ALL-UNNAMED",
                                                              "--add-exports",
                                                              "jdk.internal.le/jdk.internal.org.jline=ALL-UNNAMED",
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
