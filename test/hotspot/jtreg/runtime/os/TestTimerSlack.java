/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @summary Check that timer slack options work
 * @requires os.family == "linux"
 * @requires vm.flagless
 * @library /test/lib
 * @run driver TestTimerSlack
 */
public class TestTimerSlack {

    public static void main(String[] args) throws Exception {
        int defaultSlack;

        // Check the timer slack value is good by default
        {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xlog:os+thread",
                                                      "TestTimerSlack$Hello");

            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldHaveExitValue(0);
            output.shouldContain("timer slack:");

            defaultSlack = parseSlackValue(output);

            if (defaultSlack == 0) {
                output.reportDiagnosticSummary();
                throw new IllegalStateException("Default slack value (" + defaultSlack + ") is unexpected");
            }
        }

        // Check the timer slack value is still default when explicitly disabled/default
        for (int slack : new int[] {-1, 0}) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xlog:os+thread",
                                                      "-XX:+UnlockExperimentalVMOptions",
                                                      "-XX:TimerSlack=" + slack,
                                                      "TestTimerSlack$Hello");

            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldHaveExitValue(0);
            output.shouldContain("timer slack:");

            int disabledSlack = parseSlackValue(output);
            if (disabledSlack != defaultSlack) {
                output.reportDiagnosticSummary();
                throw new IllegalStateException("Disabled slack value (" + disabledSlack +
                    ") is not default (" + defaultSlack + ") when requested (" + slack + ")");
            }
        }

        // Check the timer slack value is accepted by all threads
        for (int slack : new int[] {1, 10, 100, 1000, 10000, 100000, 1000000}) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-Xlog:os+thread",
                                                      "-XX:+UnlockExperimentalVMOptions",
                                                      "-XX:TimerSlack=" + slack,
                                                      "TestTimerSlack$Hello");

            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldHaveExitValue(0);
            output.shouldContain("timer slack:");

            int actualSlack = parseSlackValue(output);
            if (actualSlack != slack) {
                output.reportDiagnosticSummary();
                throw new IllegalStateException("Actual slack value (" + actualSlack +
                    ") is not the requested one (" + slack + ")");
            }
        }
    }

    static final Pattern EXTRACT_PATTERN = Pattern.compile("(.*)timer slack: ([0-9]+)ns(.*)");

    public static int parseSlackValue(OutputAnalyzer output) {
        Integer value = null;
        for (String s : output.asLines()) {
            Matcher m = EXTRACT_PATTERN.matcher(s);
            if (m.matches()) {
                Integer parsedValue = Integer.parseInt(m.group(2));
                if (value == null) {
                    value = parsedValue;
                } else if (!value.equals(parsedValue)) {
                    output.reportDiagnosticSummary();
                    throw new IllegalStateException("Multiple timer slack values detected");
                }
            }
        }
        if (value == null) {
            output.reportDiagnosticSummary();
            throw new IllegalStateException("No timer slack values detected");
        }
        return value;
    }

    public static class Hello {
        public static void main(String... args) {
            System.out.println("Hello!");
        }
    }

}
