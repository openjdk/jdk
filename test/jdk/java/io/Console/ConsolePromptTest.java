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
 * @bug 8331681 8351435
 * @summary Verify the java.base's console provider handles the prompt correctly.
 * @library /test/lib
 */

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

public class ConsolePromptTest {

    private static final List<List<String>> VARIANTS = List.of(
        List.of("--limit-modules", "java.base"),
        List.of("-Djdk.console=java.base")
    );

    public static void main(String... args) throws Throwable {
        for (Method m : ConsolePromptTest.class.getDeclaredMethods()) {
            if (m.getName().startsWith("test")) {
                for (List<String> variant : VARIANTS) {
                    try {
                        m.invoke(new ConsolePromptTest(variant));
                    } catch (InvocationTargetException e) {
                        if (e.getCause() instanceof SkippedException se) {
                            throw se;
                        } else {
                            throw e;
                        }
                    }
                }
            }
        }
    }

    private final List<String> extraParams;

    public ConsolePromptTest(List<String> extraParams) {
        this.extraParams = extraParams;
    }

    void testCorrectOutputReadLine() throws Exception {
        doRunConsoleTest("testCorrectOutputReadLine");
    }

    void testCorrectOutputReadPassword() throws Exception {
        doRunConsoleTest("testCorrectOutputReadPassword");
    }

    void doRunConsoleTest(String testName) throws Exception {
        // check "expect" command availability
        var expect = Paths.get("/usr/bin/expect");
        if (!Files.exists(expect) || !Files.isExecutable(expect)) {
            throw new SkippedException("'expect' command not found. Test ignored.");
        }

        // invoking "expect" command
        var testSrc = System.getProperty("test.src", ".");
        var jdkDir = System.getProperty("test.jdk");

        List<String> command = new ArrayList<>();

        command.add("expect");
        command.add("-n");
        command.add(testSrc + "/consolePrompt.exp");
        command.add("%s");
        command.add(jdkDir + "/bin/java");
        command.addAll(extraParams);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ConsoleTest.class.getName());
        command.add(testName);

        OutputAnalyzer output = ProcessTools.executeProcess(command.toArray(String[]::new));
        output.reportDiagnosticSummary();
        output.shouldHaveExitValue(0);
    }

    public static class ConsoleTest {
        public static void main(String... args) {
            switch (args[0]) {
                case "testCorrectOutputReadLine" ->
                    System.console().readLine("%%s");
                case "testCorrectOutputReadPassword" ->
                    System.console().readPassword("%%s");
                default -> throw new UnsupportedOperationException(args[0]);
            }

            System.exit(0);
        }
    }
}
