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

package jdk.jfr.jcmd;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @summary The test verifies JFR.view command
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib /test/jdk
 * @run main/othervm jdk.jfr.jcmd.TestJcmdViewMissingData
 */
public class TestJcmdViewMissingData {

    public static void main(String... args) throws Exception {
        testNotInitialized();
        testInMemory();
        testClosed();
    }

    private static void testNotInitialized() {
        OutputAnalyzer output = JcmdHelper.jcmd("JFR.view", "jvm-information");
        output.shouldContain("No recording data available. Start a recording with JFR.start.");
    }

    private static void testInMemory() throws Exception {
        Configuration c = Configuration.getConfiguration("default");
        try (Recording r = new Recording(c)) {
            r.setToDisk(false);
            r.start();
            OutputAnalyzer output = JcmdHelper.jcmd("JFR.view", "hot-methods");
            output.shouldContain("No recording data found on disk.");
            r.stop();
        }
    }

    private static void testClosed() throws Exception {
        Configuration c = Configuration.getConfiguration("default");
        try (Recording r = new Recording(c)) {
            r.start();
            r.stop();
        }
        OutputAnalyzer output = JcmdHelper.jcmd("JFR.view", "hot-methods");
        output.shouldContain("No recording data found on disk.");
    }
}