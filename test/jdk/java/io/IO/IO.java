/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8305457
 * @summary java.io.IO tests
 * @library /test/lib
 * @run junit IO
 */
@ExtendWith(IO.TimingExtension.class)
public class IO {

    @Nested
    @EnabledOnOs({OS.LINUX, OS.MAC})
    public class OSSpecificTests {

        private static Path expect;

        @BeforeAll
        public static void prepareTTY() {
            expect = Paths.get("/usr/bin/expect"); // os-specific path
            if (!Files.exists(expect) || !Files.isExecutable(expect)) {
                Assumptions.abort("'" + expect + "' not found");
            }
            try {
                var outputAnalyzer = ProcessTools.executeProcess(
                        expect.toAbsolutePath().toString(), "-version");
                outputAnalyzer.reportDiagnosticSummary();
            } catch (Exception _) { }
        }

        /*
         * Unlike printTest, which tests a _default_ console that is normally
         * jdk.internal.org.jline.JdkConsoleProviderImpl, this test tests
         * jdk.internal.io.JdkConsoleImpl. Those console implementations operate
         * in different conditions and, thus, are tested separately.
         *
         * To test jdk.internal.io.JdkConsoleImpl one needs to ensure that both
         * conditions are met:
         *
         *   - a non-existent console provider is requested
         *   - isatty is true
         *
         * To achieve isatty, the test currently uses the EXPECT(1) Unix command,
         * which does not work for Windows. Later, a library like pty4j or JPty
         * might be used instead of EXPECT, to cover both Unix and Windows.
         */
        @ParameterizedTest
        @ValueSource(strings = {"println", "print"})
        public void outputTestInteractive(String mode) throws Exception {
            var testSrc = System.getProperty("test.src", ".");
            OutputAnalyzer output = ProcessTools.executeProcess(
                    expect.toString(),
                    Path.of(testSrc, "output.exp").toAbsolutePath().toString(),
                    System.getProperty("test.jdk") + "/bin/java",
                    "--enable-preview",
                    "-Djdk.console=gibberish",
                    Path.of(testSrc, "Output.java").toAbsolutePath().toString(),
                    mode);
            assertEquals(0, output.getExitValue());
            assertTrue(output.getStderr().isEmpty());
            output.reportDiagnosticSummary();
            String out = output.getStdout();
            // The first half of the output is produced by Console, the second
            // half is produced by IO: those halves must match.
            // Executing Console and IO in the same VM (as opposed to
            // consecutive VM runs, which are cleaner) to be able to compare string
            // representation of objects.
            assertFalse(out.isBlank());
            assertEquals(out.substring(0, out.length() / 2),
                    out.substring(out.length() / 2));
        }

        /*
         * This tests simulates terminal interaction (isatty), to check that the
         * prompt is output.
         *
         * To simulate a terminal, the test currently uses the EXPECT(1) Unix
         * command, which does not work for Windows. Later, a library like pty4j
         * or JPty might be used instead of EXPECT, to cover both Unix and Windows.
         */
        @ParameterizedTest
        @MethodSource("args")
        public void inputTestInteractive(String console, String prompt) throws Exception {
            var testSrc = System.getProperty("test.src", ".");
            var command = new ArrayList<String>();
            command.add(expect.toString());
            command.add(Path.of(testSrc, "input.exp").toAbsolutePath().toString());
            command.add(System.getProperty("test.jdk") + "/bin/java");
            command.add("--enable-preview");
            if (console != null)
                command.add("-Djdk.console=" + console);
            command.add(Path.of(testSrc, "Input.java").toAbsolutePath().toString());
            command.add(prompt == null ? "0" : "1");
            command.add(String.valueOf(prompt));
            OutputAnalyzer output = ProcessTools.executeProcess(command.toArray(new String[]{}));
            output.reportDiagnosticSummary();
            assertEquals(0, output.getExitValue());
        }

        public static Stream<Arguments> args() {
            // cross product: consoles x prompts
            return Stream.of(null, "gibberish").flatMap(console -> Stream.of(null, "?", "%s")
                    .map(prompt -> new String[]{console, prompt}).map(Arguments::of));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"println", "print"})
    public void printTest(String mode) throws Exception {
        var file = Path.of(System.getProperty("test.src", "."), "Output.java")
                .toAbsolutePath().toString();
        var pb = ProcessTools.createTestJavaProcessBuilder("--enable-preview", file, mode);
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        assertEquals(0, output.getExitValue());
        assertTrue(output.getStderr().isEmpty());
        output.reportDiagnosticSummary();
        String out = output.getStdout();
        // The first half of the output is produced by Console, the second
        // half is produced by IO: those halves must match.
        // Executing Console and IO in the same VM (as opposed to
        // consecutive VM runs, which are cleaner) to be able to compare string
        // representation of objects.
        assertFalse(out.isBlank());
        assertEquals(out.substring(0, out.length() / 2),
                out.substring(out.length() / 2));
    }


    @ParameterizedTest
    @ValueSource(strings = {"println", "print", "input"})
    public void nullConsole(String method) throws Exception {
        var file = Path.of(System.getProperty("test.src", "."), "Methods.java")
                .toAbsolutePath().toString();
        var pb = ProcessTools.createTestJavaProcessBuilder("-Djdk.console=gibberish",
                "--enable-preview", file, method);
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.reportDiagnosticSummary();
        assertEquals(1, output.getExitValue());
        output.shouldContain("Exception in thread \"main\" java.io.IOError");
    }


    // adapted from https://junit.org/junit5/docs/current/user-guide/#extensions-lifecycle-callbacks-timing-extension
    // remove after CODETOOLS-7903752 propagates to jtreg that this test is routinely run by

    public static class TimingExtension implements BeforeTestExecutionCallback,
            AfterTestExecutionCallback {

        private static final System.Logger logger = System.getLogger(
                TimingExtension.class.getName());

        private static final String START_TIME = "start time";

        @Override
        public void beforeTestExecution(ExtensionContext context) {
            getStore(context).put(START_TIME, time());
        }

        @Override
        public void afterTestExecution(ExtensionContext context) {
            Method testMethod = context.getRequiredTestMethod();
            long startTime = getStore(context).remove(START_TIME, long.class);
            long duration = time() - startTime;

            logger.log(System.Logger.Level.INFO, () ->
                    String.format("Method [%s] took %s ms.", testMethod.getName(), duration));
        }

        private ExtensionContext.Store getStore(ExtensionContext context) {
            return context.getStore(ExtensionContext.Namespace.create(getClass(),
                    context.getRequiredTestMethod()));
        }

        private long time() {
            return System.nanoTime() / 1_000_000;
        }
    }
}
