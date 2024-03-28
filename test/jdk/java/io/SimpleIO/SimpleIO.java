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

/*
 * @test
 * @bug 8305457
 * @summary java.io.SimpleIO tests
 * @library /test/lib
 * @run junit SimpleIO
 */

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleIO {

    @ParameterizedTest
    @ValueSource(strings = {"print", "println"})
    public void printTest(String mode) throws Exception {
        var file = Path.of(System.getProperty("test.src", "."), "Print.java")
                .toAbsolutePath().toString();
        var pb = ProcessTools.createTestJavaProcessBuilder(file, mode);
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.reportDiagnosticSummary();
        assertEquals(0, output.getExitValue());
        String stdout = output.getOutput();
        // the first half of the output is produced by System.out, the second
        // half is produced by SimpleIO: these halves must match
        assertEquals(stdout.substring(0, stdout.length() / 2),
                stdout.substring(stdout.length() / 2));
    }

    /*
     * This tests simulates terminal interaction (isatty), to check that the
     * prompt is output.
     *
     * To simulate a terminal, the test currently uses the EXPECT(1) Unix
     * command, which does not work for Windows. Later, a library like pty4j
     * or JPty might be used instead EXPECT, to cover both Unix and Windows.
     */
    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    public void inputTestInteractive() throws Exception {
        var expect = Paths.get("/usr/bin/expect"); // os-specific path
        if (!Files.exists(expect) || !Files.isExecutable(expect)) {
            throw new SkippedException("'" + expect + "' not found");
        }
        var testSrc = System.getProperty("test.src", ".");
        OutputAnalyzer output = ProcessTools.executeProcess(
                "expect",
                Path.of(testSrc, "script.exp").toAbsolutePath().toString(),
                System.getProperty("test.jdk") + "/bin/java",
                Path.of(testSrc, "Input.java").toAbsolutePath().toString());
        output.reportDiagnosticSummary();
        assertEquals(0, output.getExitValue());
    }

    /*
     * This test checks that a prompt is NOT output if stdin is redirected.
     *
     * The test uses different console "modes", to make sure that none of the
     * them affect how SimpleIO reads from a redirected stdin.
     *
     * In general, a console shouldn't be used if stdin is redirected, because
     * that console might not be designed for the redirection use. In particular,
     * the JLine implementation is very picky in regard to newline.
     * If a redirection file consists of a line not terminated with whatever
     * JLine likes, it will return null from readLine().
     */
    @ParameterizedTest
    @ValueSource(strings = {"null", "java.base", "jdk.internal.le"})
    public void inputTestRedirected(String mode) throws Exception {
        final var expected = "hello";
        // write bytes not string, for clarity
        var f = Files.write(Path.of("input"), expected.getBytes(StandardCharsets.UTF_8)).toFile();
        var file = Path.of(System.getProperty("test.src", "."), "Input.java")
                .toAbsolutePath().toString();
        String[] command = mode.equals("null") ? new String[]{file}
                : new String[]{"-Djdk.console=" + mode, file};
        var pb = ProcessTools.createTestJavaProcessBuilder(command);
        pb.redirectInput(ProcessBuilder.Redirect.from(f));
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.reportDiagnosticSummary();
        assertEquals(0, output.getExitValue());
        String stdout = output.getOutput();
        assertEquals(expected, stdout);
    }
}
