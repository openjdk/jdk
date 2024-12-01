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
 *
 */

package compiler.c2.aarch64;

import java.util.ArrayList;
import java.util.Iterator;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/**
 * @test TestTrampoline
 * @summary Checks that trampolines to runtime code are not generated if they are not needed.
 * @bug 8285487
 * @library /test/lib
 *
 * @requires vm.flagless & os.arch=="aarch64" &
 *           vm.debug == false & vm.compiler2.enabled
 *
 * @run driver compiler.c2.aarch64.TestTrampoline
 */

public class TestTrampoline {
    private final static int ITERATIONS_TO_HEAT_LOOP = 20_000;

    public static void main(String[] args) throws Exception {
        String testClassName = TestTrampoline.Test.class.getName();
        ArrayList<String> command = new ArrayList<String>();
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-Xbatch");
        command.add("-XX:CompileCommand=print," + testClassName + "::" + "test");
        // ReservedCodeCacheSize=130M causes generation of trampolines.
        // As the non-nmethod segment is put between other two segments,
        // runtime calls will be within 128M range.
        // So there is no need for trampolines for runtime calls.
        command.add("-XX:ReservedCodeCacheSize=130M");
        command.add("-XX:+SegmentedCodeCache");
        command.add(testClassName);
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(command);
        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);
        System.out.println(analyzer.getOutput());
        checkOutput(analyzer);
    }

    private static String skipTo(Iterator<String> iter, String substring) {
        while (iter.hasNext()) {
            String nextLine = iter.next();
            if (nextLine.contains(substring)) {
                return nextLine;
            }
        }
        return null;
    }

    private static void checkOutput(OutputAnalyzer output) {
        Iterator<String> iter = output.asLines().listIterator();

        String match = skipTo(iter, "Compiled method (c2)");
        if (match == null || !match.contains("Test::test")) {
            throw new RuntimeException("Missing compiler output for the method 'test'");
        }

        match = skipTo(iter, "[Stub Code]");
        if (match != null && skipTo(iter, "{trampoline_stub}") != null) {
            throw new RuntimeException("Found unexpected {trampoline_stub}");
        }
    }

    static class Test {
        private static void test(String s, int i) {
            if (s.charAt(i) > 128)
                throw new RuntimeException();
        }

        public static void main(String[] args) {
            String s = "Returns the char value at the specified index.";
            for (int i = 0; i < ITERATIONS_TO_HEAT_LOOP; ++i) {
                test(s, i % s.length());
            }
        }
    }
}
