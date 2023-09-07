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
 * @test id=C1
 * @bug 8280481
 * @summary Checks that stubs to the interpreter can be shared for static or final method.
 * @library /test/lib
 * @requires vm.opt.TieredStopAtLevel == null & vm.opt.TieredCompilation == null
 * @requires vm.simpleArch == "x86" | vm.simpleArch == "x64" | vm.simpleArch == "aarch64" | vm.simpleArch == "riscv64"
 * @requires vm.debug
 * @run driver compiler.sharedstubs.SharedStubToInterpTest -XX:TieredStopAtLevel=1
 *
 * @test id=C2
 * @requires vm.opt.TieredStopAtLevel == null & vm.opt.TieredCompilation == null
 * @requires vm.simpleArch == "x86" | vm.simpleArch == "x64" | vm.simpleArch == "aarch64" | vm.simpleArch == "riscv64"
 * @requires vm.debug
 * @run driver compiler.sharedstubs.SharedStubToInterpTest -XX:-TieredCompilation
 *
 */

package compiler.sharedstubs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class SharedStubToInterpTest {
    private final static int ITERATIONS_TO_HEAT_LOOP = 20_000;

    private static void runTest(String test) throws Exception {
        String testClassName = SharedStubToInterpTest.class.getName() + "$" + test;
        ArrayList<String> command = new ArrayList<String>();
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-Xbatch");
        command.add("-XX:+PrintRelocations");
        command.add("-XX:CompileCommand=compileonly," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=print," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=exclude," + testClassName + "::" + "log01");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "log01");
        command.add("-XX:CompileCommand=exclude," + testClassName + "::" + "log02");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "log02");
        command.add(testClassName);

        ProcessBuilder pb = ProcessTools.createTestJvm(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        System.out.println(analyzer.getOutput());

        checkOutput(analyzer);
    }

    public static void main(String[] args) throws Exception {
        String[] methods = new String[] { "StaticMethodTest", "FinalClassTest", "FinalMethodTest"};
        for (String methodName : methods) {
            runTest(methodName);
        }
    }

    private static void checkOutput(OutputAnalyzer output) {
        List<String> addrs = Pattern.compile("\\(static_stub\\) addr=(\\w+) .*\\[static_call=")
            .matcher(output.getStdout())
            .results()
            .map(m -> m.group(1))
            .toList();
        if (addrs.stream().distinct().count() >= addrs.size()) {
            throw new RuntimeException("No static stubs reused: distinct " + addrs.stream().distinct().count() + ", in total " + addrs.size());
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
