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
 * @test SharedTrampolineTest id=C2
 * @summary Checks that trampolines can be shared for static method.
 * @bug 8280152
 * @library /test/lib
 *
 * @requires vm.opt.TieredCompilation == null
 * @requires os.arch=="aarch64" | os.arch=="riscv64"
 * @requires vm.debug
 *
 * @run driver compiler.sharedstubs.SharedTrampolineTest -XX:-TieredCompilation
 */

package compiler.sharedstubs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class SharedTrampolineTest {
    private final static int ITERATIONS_TO_HEAT_LOOP = 20_000;

    private static void runTest(String test) throws Exception {
        String testClassName = SharedTrampolineTest.class.getName() + "$" + test;
        ArrayList<String> command = new ArrayList<String>();
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-Xbatch");
        command.add("-XX:+PrintRelocations");
        command.add("-XX:CompileCommand=compileonly," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "log");
        command.add(testClassName);
        command.add("a");

        ProcessBuilder pb = ProcessTools.createTestJvm(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        System.out.println(analyzer.getOutput());

        checkOutput(analyzer);
    }

    public static void main(String[] args) throws Exception {
        String[] tests = new String[] {"StaticMethodTest"};
        for (String test : tests) {
            runTest(test);
        }
    }

    private static void checkOutput(OutputAnalyzer output) {
        List<String> addrs = Pattern.compile("\\(trampoline_stub\\) addr=(\\w+) .*\\[trampoline owner")
            .matcher(output.getStdout())
            .results()
            .map(m -> m.group(1))
            .toList();
        if (addrs.stream().distinct().count() >= addrs.size()) {
            throw new RuntimeException("No runtime trampoline stubs reused: distinct " + addrs.stream().distinct().count() + ", in total " + addrs.size());
        }
    }

    public static class StaticMethodTest {
        private static void log(int i, String msg) {
        }

        static void test(int i, String[] args) {
            if (i % args.length == 0) {
                log(i, "args[0] = " + args[0]);
            } else {
                log(i, "No args");
            }
        }

        public static void main(String[] args) {
            for (int i = 1; i < ITERATIONS_TO_HEAT_LOOP; ++i) {
                test(i, args);
            }
        }
    }
}
