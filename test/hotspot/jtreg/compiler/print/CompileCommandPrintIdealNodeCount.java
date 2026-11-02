/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8393716
 * @summary Checks that -XX:CompileCommand=PrintIdealNodeCount,... works
 * @requires vm.debug & vm.compiler2.enabled & vm.flagless
 * @library /test/lib
 * @run driver ${test.main.class}
 */

package compiler.print;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class CompileCommandPrintIdealNodeCount {
    private static final String PARSE_NODE_COUNTS = "phase name='parse' nodes='[0-9]+' live='[0-9]+' live_graph_walk='[0-9]+'";

    public static void main(String[] args) throws Exception {
        // No PrintIdealNodeCount prints nothing
        List<String> testOptions = new ArrayList<>();
        test(testOptions)
            .shouldHaveExitValue(0)
            .shouldNotMatch(PARSE_NODE_COUNTS);

        testOptions = new ArrayList<>();
        testOptions.add("-XX:CompileCommand=PrintIdealNodeCount," + TestMain.class.getName() + "::method1");
        OutputAnalyzer result = test(testOptions);
        result.shouldHaveExitValue(0);
        int matches = result.matchersForAllMatchingLinesStdout(Pattern.compile(PARSE_NODE_COUNTS)).size();
        int expected_matches = 1;
        if (matches != expected_matches) {
            throw new RuntimeException("Unexpected number of matching lines: " + matches + ", expected " + expected_matches);
        }

        testOptions = new ArrayList<>();
        testOptions.add("-XX:CompileCommand=PrintIdealNodeCount," + TestMain.class.getName() + "::*");
        result = test(testOptions);
        result.shouldHaveExitValue(0);
        matches = result.matchersForAllMatchingLinesStdout(Pattern.compile(PARSE_NODE_COUNTS)).size();
        expected_matches = 2;
        if (matches != expected_matches) {
            throw new RuntimeException("Unexpected number of matching lines: " + matches + ", expected " + expected_matches);
        }
    }

    private static OutputAnalyzer test(List<String> testOptions) throws Exception {
        List<String> options = new ArrayList<>();
        options.add("-Xcomp");
        options.add("-Xbatch");
        options.add("-XX:-TieredCompilation");
        options.add("-XX:CompileCommand=compileonly," + TestMain.class.getName() + "::method*");
        options.addAll(testOptions);
        options.add(TestMain.class.getName());
        return ProcessTools.executeLimitedTestJava(options);
    }

    public static class TestMain {
        public static void main(String[] args) {
            method1();
            method2();
        }

        public static int method1() {
            return 1;
        }

        public static int method2() {
            return 2;
        }
    }
}
