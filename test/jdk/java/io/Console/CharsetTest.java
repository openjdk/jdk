/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Console;
import java.nio.file.Files;
import java.nio.file.Paths;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import static jdk.test.lib.Utils.*;

/**
 * @test
 * @bug 8264208 8265918 8356985
 * @summary Tests Console.charset() method. "expect" command in Windows/Cygwin
 *          does not work as expected. Ignoring tests on Windows.
 * @requires (os.family == "linux") | (os.family == "mac")
 * @library /test/lib
 * @run main CharsetTest en_US.ISO8859-1 ISO-8859-1
 * @run main CharsetTest en_US.US-ASCII US-ASCII
 * @run main CharsetTest en_US.UTF-8 UTF-8
 */
public class CharsetTest {
    public static void main(String... args) throws Throwable {
        if (args.length == 0) {
            // no arg means child java process being tested.
            Console con = System.console();
            System.out.println(con.charset());
            return;
        } else {
            // check "expect" command availability
            var expect = Paths.get("/usr/bin/expect");
            if (!Files.exists(expect) || !Files.isExecutable(expect)) {
                throw new jtreg.SkippedException("'expect' command not found. Test ignored.");
            }

            // invoking "expect" command
            OutputAnalyzer output = ProcessTools.executeProcess(
                    "expect",
                    "-n",
                    TEST_SRC + "/script.exp",
                    TEST_JDK + "/bin/java",
                    args[0],
                    args[1],
                    TEST_CLASSES);
            output.reportDiagnosticSummary();
            var eval = output.getExitValue();
            if (eval != 0) {
                throw new RuntimeException("Test failed. Exit value from 'expect' command: " + eval);
            }
        }
    }
}
