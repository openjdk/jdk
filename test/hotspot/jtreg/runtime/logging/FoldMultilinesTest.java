/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 NTT DATA.
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
 * @bug 8271186
 * @library /test/lib
 * @run driver FoldMultilinesTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class FoldMultilinesTest {

    private static Path EXCEPTION_LOG_FILE = Path.of("exceptions.log");
    private static String XLOG_BASE = "-Xlog:exceptions=info:file=" + EXCEPTION_LOG_FILE.toString();

    private static void analyzeFoldMultilinesOn(ProcessBuilder pb) throws Exception {
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String logs = Files.readString(EXCEPTION_LOG_FILE);
        if (!logs.contains("line 1\\nline 2\\\\nstring")) {
            throw new RuntimeException("foldmultilines=true did not work.");
        }
    }

    private static void analyzeFoldMultilinesOff(ProcessBuilder pb) throws Exception {
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String logs = Files.readString(EXCEPTION_LOG_FILE);
        if (!logs.contains("line 1" + System.lineSeparator() + "line 2\\nstring")) {
            throw new RuntimeException("foldmultilines=false did not work.");
        }
    }

    private static void analyzeFoldMultilinesInvalid(ProcessBuilder pb) throws Exception {
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Invalid option: foldmultilines must be 'true' or 'false'.");
        output.shouldNotHaveExitValue(0);
    }

    public static void main(String[] args) throws Exception {
        String Xlog;
        ProcessBuilder pb;

        Xlog = XLOG_BASE + "::foldmultilines=true";
        pb = ProcessTools.createJavaProcessBuilder(Xlog, InternalClass.class.getName());
        analyzeFoldMultilinesOn(pb);

        Xlog = XLOG_BASE + "::foldmultilines=false";
        pb = ProcessTools.createJavaProcessBuilder(Xlog, InternalClass.class.getName());
        analyzeFoldMultilinesOff(pb);

        Xlog = XLOG_BASE + "::foldmultilines=invalid";
        pb = ProcessTools.createJavaProcessBuilder(Xlog, InternalClass.class.getName());
        analyzeFoldMultilinesInvalid(pb);
    }

    public static class InternalClass {
        public static void main(String[] args) {
            try {
                throw new RuntimeException("line 1\nline 2\\nstring");
            } catch (Exception e) {
                // Do nothing to return exit code 0
            }
        }
    }

}
