/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright Red Hat, Inc. All Rights Reserved.
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
 * @bug 8319301
 * @summary Tests various ways to call memlimit
 * @library /test/lib /
 *
 * @run driver compiler.compilercontrol.commands.MemLimitTest
 */

package compiler.compilercontrol.commands;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class MemLimitTest {

    static void do_test(String option, boolean expectSuccess, int expectedValue) throws Exception {
        OutputAnalyzer output = ProcessTools.executeTestJvm("-Xmx64m", "-XX:CompileCommand=" + option, "-version");
        if (expectSuccess) {
            output.shouldHaveExitValue(0);
            output.shouldNotContain("error occurred");
            // On success, we expect the command to be registered with the expected value
            output.shouldContain("CompileCommand: MemLimit *.* intx MemLimit = " + expectedValue);
        } else {
            // On error, we don't expec a command registered.
            output.shouldNotHaveExitValue(0);
            output.shouldNotMatch("# A fatal error.*");
            output.shouldContain("CompileCommand: An error occurred during parsing");
            output.shouldNotContain("CompileCommand: MemStat"); // on error, no command should be registered!
        }
    }

    public static void main(String[] args) throws Exception {
        // Check parsing of the MemLimit option. For functional tests, please see
        // test/hotspot/jtreg/compiler/print/CompileCommandMemLimit.java

        // Negative tests

        // Missing value
        do_test("MemLimit,*.*", false, 0);

        // Not a parseable number. Should not crash
        do_test("MemLimit,*.*,hallo", false, 0);

        // Invalid option.
        do_test("MemLimit,*.*,444m~hallo", false, 0);

        // Positive tests

        // "stop" mode is encoded as positive size
        do_test("MemLimit,*.*,444m~stop", true, 465567744);

        // "crash" mode is encoded as negative size
        do_test("MemLimit,*.*,444m~crash", true, -465567744);

        // if omitted, stop is default
        do_test("MemLimit,*.*,444m", true, 465567744);

    }
}
