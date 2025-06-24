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

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Paths;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import static jdk.test.lib.Utils.*;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * @test
 * @bug 8356985
 * @summary Tests if "stdin.encoding" is reflected for reading
 *          the console. "expect" command in Windows/Cygwin does
 *          not work as expected. Ignoring tests on Windows.
 * @requires (os.family == "linux" | os.family == "mac")
 * @library /test/lib
 * @build csp/*
 * @run junit StdinEncodingTest
 */
public class StdinEncodingTest {

    @Test
    public void testStdinEncoding() throws Throwable {
        // check "expect" command availability
        var expect = Paths.get("/usr/bin/expect");
        Assumptions.assumeTrue(Files.exists(expect) && Files.isExecutable(expect),
            "'" + expect + "' not found");

        // invoking "expect" command
        OutputAnalyzer output = ProcessTools.executeProcess(
            "expect",
            "-n",
            TEST_SRC + "/stdinEncoding.exp",
            TEST_JDK + "/bin/java",
            "--module-path",
            TEST_CLASSES + "/modules",
            "-Dstdin.encoding=Uppercasing", // <- gist of this test
            "StdinEncodingTest");
        output.reportDiagnosticSummary();
        output.shouldHaveExitValue(0);
    }

    public static void main(String... args) throws Throwable {
        // check stdin.encoding
        if (!"Uppercasing".equals(System.getProperty("stdin.encoding"))) {
            throw new RuntimeException("Uppercasing charset was not set in stdin.encoding");
        }
        var con = System.console();

        // Console.readLine()
        System.out.print(con.readLine());

        // Console.readPassword()
        System.out.print(String.valueOf(con.readPassword()));

        // Console.reader()
        try (var br = new BufferedReader(con.reader())) {
            System.out.print(br.readLine());
        }

        // Wait till the test receives the result
        con.readLine();
    }
}
