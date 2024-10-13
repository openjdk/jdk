/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8303697
 * @summary Test verifies that ProcessTools.startProcess() print all lines even the last line doesn't end with '\n'
 * @library /test/lib
 * @run main ProcessToolsLastLineTest
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Asserts;

public class ProcessToolsLastLineTest {

    static void test(String output) throws Exception {
        final StringBuffer sb = new StringBuffer();
        Process p = ProcessTools.startProcess("process",
                ProcessTools.createLimitedTestJavaProcessBuilder(ProcessToolsLastLineTest.class.getName(), output),
                line -> { sb.append(line);});
        p.waitFor();
        String expectedOutput = output.replace("\n", "");
        Asserts.assertEQ(sb.toString(), expectedOutput);
    }

    public static void main(String[] args) throws Exception {

        // The line which exceeds internal StreamPumper buffer (256 bytes)
        String VERY_LONG_LINE = "X".repeat(257);
        if (args.length > 0) {
            System.out.print(args[0]);
        } else {
            test("\n");
            test("\nARG1");
            test("\nARG1\n");
            test("ARG1\n");
            test("ARG1");
            test("ARG1\nARG2");
            test("ARG1\nARG2\n");
            test("\nARG1\nARG2\n");
            test("\nARG1\n" + VERY_LONG_LINE + "\nARG2\n");
            test("\nARG1\n" + VERY_LONG_LINE);
            test("\nARG1\n" + VERY_LONG_LINE + VERY_LONG_LINE + VERY_LONG_LINE + "\nARG2\n");
            test("\nARG1\n" + VERY_LONG_LINE + VERY_LONG_LINE + VERY_LONG_LINE);

        }

    }
}
