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
import java.util.regex.Pattern;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class FoldMultilinesTest {

    private static String EXCEPTION_LOG_FILE = "exceptions.log";
    private static String XLOG_BASE = "-Xlog:exceptions=info:";
    private static String EXCEPTION_MESSAGE = "line 1\nline 2\\nstring";
    private static String FOLDED_EXCEPTION_MESSAGE = "line 1\\nline 2\\\\nstring";
    // Windows may out "\r\n" even though UL outs "\n" only, so we need to evaluate regex with \R.
    private static Pattern NEWLINE_LOG_PATTERN = Pattern.compile("line 1\\Rline 2\\\\nstring", Pattern.MULTILINE);

    private static void analyzeFoldMultilinesOn(ProcessBuilder pb, String out) throws Exception {
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String logs = switch (out) {
            case "stdout" -> output.getStdout();
            case "stderr" -> output.getStderr();
            default -> Files.readString(Path.of(EXCEPTION_LOG_FILE));
        };

        if (!logs.contains(FOLDED_EXCEPTION_MESSAGE)) {
            throw new RuntimeException(out + ": foldmultilines=true did not work.");
        }
    }

    private static void analyzeFoldMultilinesOff(ProcessBuilder pb, String out) throws Exception {
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        String logs = switch (out) {
            case "stdout" -> output.getStdout();
            case "stderr" -> output.getStderr();
            default -> Files.readString(Path.of(EXCEPTION_LOG_FILE));
        };

        if (!NEWLINE_LOG_PATTERN.matcher(logs).find()) {
            throw new RuntimeException(out + ": foldmultilines=false did not work.");
        }
    }

    private static void analyzeFoldMultilinesInvalid(ProcessBuilder pb) throws Exception {
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldContain("Invalid option: foldmultilines must be 'true' or 'false'.");
        output.shouldNotHaveExitValue(0);
    }

    private static void test(String out) throws Exception {
        String Xlog;
        ProcessBuilder pb;

        Xlog = XLOG_BASE + out +  "::foldmultilines=true";
        pb = ProcessTools.createJavaProcessBuilder(Xlog, InternalClass.class.getName());
        analyzeFoldMultilinesOn(pb, out);

        Xlog = XLOG_BASE + out + "::foldmultilines=false";
        pb = ProcessTools.createJavaProcessBuilder(Xlog, InternalClass.class.getName());
        analyzeFoldMultilinesOff(pb, out);

        Xlog = XLOG_BASE + out + "::foldmultilines=invalid";
        pb = ProcessTools.createJavaProcessBuilder(Xlog, InternalClass.class.getName());
        analyzeFoldMultilinesInvalid(pb);
    }

    public static void main(String[] args) throws Exception {
        test("file=" + EXCEPTION_LOG_FILE);
        test("stdout");
        test("stderr");
    }

    public static class InternalClass {
        public static void main(String[] args) {
            try {
                throw new RuntimeException(EXCEPTION_MESSAGE);
            } catch (Exception e) {
                // Do nothing to return exit code 0
            }
        }
    }

}
