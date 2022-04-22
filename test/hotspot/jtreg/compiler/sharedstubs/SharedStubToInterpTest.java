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

/**
 * @test SharedStubToInterpTest
 * @summary Checks that stubs to the interpreter can be shared for static or final method.
 * @bug 8280481
 * @library /test/lib
 *
 * @requires os.arch=="amd64" | os.arch=="x86_64" | os.arch=="i386" | os.arch=="x86" | os.arch=="aarch64"
 *
 * @run driver compiler.sharedstubs.SharedStubToInterpTest
 */

package compiler.sharedstubs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class SharedStubToInterpTest {
    private final static int ITERATIONS_TO_HEAT_LOOP = 20_000;

    private static void runTest(String compiler, String test) throws Exception {
        String testClassName = SharedStubToInterpTest.class.getName() + "$" + test;
        ArrayList<String> command = new ArrayList<String>();
        command.add(compiler);
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-Xbatch");
        command.add("-XX:CompileCommand=compileonly," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=print," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=exclude," + testClassName + "::" + "log01");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "log01");
        command.add("-XX:CompileCommand=exclude," + testClassName + "::" + "log02");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "log02");
        command.add(testClassName);

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        System.out.println(analyzer.getOutput());

        checkOutput(analyzer);
    }

    public static void main(String[] args) throws Exception {
        List<String> compilers = java.util.Arrays.asList("-XX:-TieredCompilation" /* C2 */,
            "-XX:TieredStopAtLevel=1" /* C1 */);
        List<String> tests = java.util.Arrays.asList("StaticMethodTest",
            "FinalClassTest", "FinalMethodTest");
        for (String compiler : compilers) {
            for (String test : tests) {
                runTest(compiler, test);
            }
        }
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

        String match = skipTo(iter, "Compiled method");
        while (match != null && !match.contains("Test::test")) {
            match = skipTo(iter, "Compiled method");
        }
        if (match == null) {
            throw new RuntimeException("Missing compiler output for the method 'test'");
        }

        while (iter.hasNext()) {
            String nextLine = iter.next();
            if (nextLine.contains("{static_stub}")) {
                // Static stubs must be created at the end of the Stub section.
                throw new RuntimeException("Found {static_stub} before Deopt Handler Code");
            } else if (nextLine.contains("{runtime_call DeoptimizationBlob}")) {
                // Shared static stubs are put after Deopt Handler Code.
                break;
            }
        }

        int foundStaticStubs = 0;
        while (iter.hasNext()) {
            if (iter.next().contains("{static_stub}")) {
                foundStaticStubs += 1;
            }
        }

        final int expectedStaticStubs = 2;
        if (foundStaticStubs != expectedStaticStubs) {
            throw new RuntimeException("Found static stubs: " + foundStaticStubs + "; Expected static stubs: " + expectedStaticStubs);
        }
    }

    public static class StaticMethodTest {
        static void log01(int i) {
        }
        static void log02(int i) {
        }

        static void test(int i) {
            if (i % 3 == 0) {
                log01(i);
                log02(i);
            } else {
                log01(i);
                log02(i);
            }
        }

        public static void main(String[] args) {
            for (int i = 1; i < ITERATIONS_TO_HEAT_LOOP; ++i) {
                test(i);
            }
        }
    }

    public static final class FinalClassTest {
        void log01(int i) {
        }
        void log02(int i) {
        }

        void test(int i) {
            if (i % 3 == 0) {
                log01(i);
                log02(i);
            } else {
                log01(i);
                log02(i);
            }
        }

        public static void main(String[] args) {
            FinalClassTest tFC = new FinalClassTest();
            for (int i = 1; i < ITERATIONS_TO_HEAT_LOOP; ++i) {
                tFC.test(i);
            }
        }
    }

    public static class FinalMethodTest {
        final void log01(int i) {
        }
        final void log02(int i) {
        }

        void test(int i) {
            if (i % 3 == 0) {
                log01(i);
                log02(i);
            } else {
                log01(i);
                log02(i);
            }
        }

        public static void main(String[] args) {
            FinalMethodTest tFM = new FinalMethodTest();
            for (int i = 1; i < ITERATIONS_TO_HEAT_LOOP; ++i) {
                tFM.test(i);
            }
        }
    }
}
