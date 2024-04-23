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
import static org.junit.jupiter.api.Assertions.assertFalse;

/*
 * @test
 * @bug 8305457
 * @summary java.io.Console.Basic tests
 * @library /test/lib
 * @run junit Basic
 */
public class Basic {

    @ParameterizedTest
    @ValueSource(strings = {"println", "print"})
    public void printTest(String mode) throws Exception {
        var file = Path.of(System.getProperty("test.src", "."), "Print.java")
                .toAbsolutePath().toString();
        var pb = ProcessTools.createTestJavaProcessBuilder("--enable-preview", file, mode);
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        assertEquals(0, output.getExitValue());
        output.reportDiagnosticSummary();
        String out = output.getOutput();
        // The first half of the output is produced by Console, the second
        // half is produced by Console.Basic: those halves must match.
        // Executing Console and Console.Basic in the same VM (as opposed to
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

    @ParameterizedTest
    @ValueSource(strings = {"println", "print", "input"})
    public void nullConsole(String method) throws Exception {
        var file = Path.of(System.getProperty("test.src", "."), "Methods.java")
                .toAbsolutePath().toString();
        var pb = ProcessTools.createTestJavaProcessBuilder("-Djdk.console=java.base",
                "--enable-preview", file, method);
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.reportDiagnosticSummary();
        assertEquals(1, output.getExitValue());
        output.shouldContain("Exception in thread \"main\" java.io.IOError");
    }
}
