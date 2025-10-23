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

import java.nio.file.Files;
import java.nio.file.Paths;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static jdk.test.lib.Utils.*;

/**
 * @test
 * @bug 8341975 8351435 8361613
 * @summary Tests the default charset. It should honor `stdout.encoding`
 *          which should be the same as System.out.charset()
 * @requires (os.family == "linux") | (os.family == "mac")
 * @library /test/lib
 * @build jdk.test.lib.Utils
 *        jdk.test.lib.JDKToolFinder
 *        jdk.test.lib.process.ProcessTools
 * @run junit DefaultCharsetTest
 */
public class DefaultCharsetTest {
    @BeforeAll
    static void checkExpectAvailability() {
        // check "expect" command availability
        var expect = Paths.get("/usr/bin/expect");
        Assumptions.assumeTrue(Files.exists(expect) && Files.isExecutable(expect),
            "'" + expect + "' not found. Test ignored.");
    }
    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "ISO-8859-1", "US-ASCII", "foo", ""})
    void testDefaultCharset(String stdoutEncoding) throws Exception {
        // invoking "expect" command
        OutputAnalyzer oa = ProcessTools.executeProcess(
            "expect",
            "-n",
            TEST_SRC + "/defaultCharset.exp",
            TEST_CLASSES,
            TEST_JDK + "/bin/java",
            "-Dstdout.encoding=" + stdoutEncoding,
            getClass().getName());
        oa.reportDiagnosticSummary();
        oa.shouldHaveExitValue(0);
    }

    public static void main(String... args) {
        var stdoutEncoding = System.getProperty("stdout.encoding");
        var sysoutCharset = System.out.charset();
        var consoleCharset = System.console().charset();
        System.out.printf("""
                stdout.encoding = %s
                System.out.charset() = %s
                System.console().charset() = %s
            """, stdoutEncoding, sysoutCharset.name(), consoleCharset.name());
        if (!consoleCharset.equals(sysoutCharset)) {
            System.err.printf("Charsets for System.out and Console differ for stdout.encoding: %s%n", stdoutEncoding);
            System.exit(-1);
        }
    }
}
