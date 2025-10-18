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

import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8367976
 * @summary Verifies that the `jdk.httpclient.bufsize` system property is
 *          clamped correctly
 * @library /test/lib
 * @run junit BufferSizePropertyClampTest
 */

class BufferSizePropertyClampTest {

    private static Path scriptPath;

    @BeforeAll
    static void setUp() throws IOException {
        // Create a Java file that prints the `Utils::BUFSIZE` value
        scriptPath = Path.of("UtilsBUFSIZE.java");
        Files.write(scriptPath, List.of("void main() { IO.println(jdk.internal.net.http.common.Utils.BUFSIZE); }"));
    }

    @AfterAll
    static void tearDown() throws IOException {
        Files.deleteIfExists(scriptPath);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, (2 << 14) + 1})
    void test(int invalidBufferSize) throws Exception {

        // Run the Java file
        var outputAnalyzer = ProcessTools.executeTestJava(
                "--add-exports", "java.net.http/jdk.internal.net.http.common=ALL-UNNAMED",
                "-Djdk.httpclient.HttpClient.log=errors",
                "-Djdk.httpclient.bufsize=" + invalidBufferSize,
                scriptPath.toString());
        outputAnalyzer.shouldHaveExitValue(0);

        // Verify stderr
        List<String> stderrLines = outputAnalyzer.stderrAsLines();
        assertEquals(2, stderrLines.size(), "Expected 2 lines, found: " + stderrLines);
        assertTrue(
                stderrLines.get(0).endsWith("jdk.internal.net.http.common.Utils getIntegerNetProperty"),
                "Unexpected line: " + stderrLines.get(0));
        assertEquals(
                "INFO: ERROR: Property value for jdk.httpclient.bufsize=" + invalidBufferSize + " not in [1..16384]: using default=16384",
                stderrLines.get(1).replaceAll(",", ""));

        // Verify stdout
        var stdoutLines = outputAnalyzer.stdoutAsLines();
        assertEquals(1, stdoutLines.size(), "Expected one line, found: " + stdoutLines);
        assertEquals("16384", stdoutLines.get(0));

    }

}
