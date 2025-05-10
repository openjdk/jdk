/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2024, Red Hat, Inc. and/or its affiliates.
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

import org.testng.annotations.Test;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;
import jdk.test.lib.process.OutputAnalyzer;

import java.io.*;
import java.util.HashSet;
import java.util.regex.Pattern;

/*
 * @test
 * @summary Test of diagnostic command System.map
 * @library /test/lib
 * @requires (os.family == "linux" | os.family == "windows" | os.family == "mac")
 * @modules java.base/jdk.internal.misc
 *          java.compiler
 *          java.management
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run testng/othervm -XX:+UsePerfData SystemDumpMapBadFileTest
 */
public class SystemDumpMapBadFileTest extends SystemMapTestBase {

    private void run_test(CommandExecutor executor, String fnStr) {

        try {
            OutputAnalyzer output = executor.execute("System.dump_map " + fnStr);
            output.reportDiagnosticSummary();
            boolean hasEmptyMsg = output.contains(".*filename is empty or not specified.");
            boolean hasMappingMsg = output.contains("Memory map dumped to");
            if (fnStr.isEmpty() || fnStr.equals("-F=foo")) {
                if (hasEmptyMsg) {
                    throw new RuntimeException("did not expect empty filename error when filename was not specified");
                }
                if (!hasMappingMsg) {
                    throw new RuntimeException("expected mapping message when filename was not specified");
                }
            } else {
                if (!hasEmptyMsg) {
                    throw new RuntimeException("expected empty filename error when filename was blank (" + fnStr + ")");
                }
                if (hasMappingMsg) {
                    throw new RuntimeException("did not expect mapping message when filename was blank (" + fnStr + ")");
                }
            }
        } catch (Exception e) {
            if (fnStr.isEmpty()) {
                throw new RuntimeException("should not get exception if no filename specified: " + e);
            } else if (fnStr.equals("-F=foo")) {
                throw new RuntimeException("should not get exception if filename foo specified: " + e);
            } else {
                if (!e.toString().contains("Filename is empty or not specified.")) {
                    throw new RuntimeException("exception thrown as expected but wrong error message:" + e);
                }
            }
        }
    }

    public void run(CommandExecutor executor) {
        run_test(executor, "");
        run_test(executor, "-F=foo");
        run_test(executor, "-F=");
        run_test(executor, "-F");
    }

    @Test
    public void jmx() {
        run(new JMXExecutor());
    }
}
