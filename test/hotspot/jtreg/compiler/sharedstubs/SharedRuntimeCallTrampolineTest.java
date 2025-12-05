/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * Copyright 2025 Arm Limited and/or its affiliates.
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
 * @test SharedRuntimeCallTrampolineTest id=C2
 * @summary Checks that trampolines can be shared between runtime calls.
 * @bug 8280152
 * @library /test/lib
 *
 * @requires vm.compiler2.enabled
 * @requires os.arch=="aarch64"
 * @requires vm.debug
 *
 * @run driver compiler.sharedstubs.SharedRuntimeCallTrampolineTest -XX:-TieredCompilation
 */

package compiler.sharedstubs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class SharedRuntimeCallTrampolineTest {
    private final static int ITERATIONS_TO_HEAT_LOOP = 20_000;

    private static void runTest(String compiler, String test) throws Exception {
        String testClassName = SharedRuntimeCallTrampolineTest.class.getName() + "$" + test;
        ArrayList<String> command = new ArrayList<String>();
        command.add(compiler);
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-Xbatch");
        command.add("-XX:+PrintRelocations");
        command.add("-XX:CompileCommand=compileonly," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "test");
        command.add(testClassName);
        command.add("a");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        System.out.println(analyzer.getOutput());

        checkOutput(analyzer);
    }

    public static void main(String[] args) throws Exception {
        String[] tests = new String[] { "RuntimeCallTest" };
        for (String test : tests) {
            runTest(args[0], test);
        }
    }

    private static String getTestMethodStdout(OutputAnalyzer output) {
        return Pattern.compile("Compiled method.*RuntimeCallTest::test").split(output.getStdout(), 2)[1];
    }

    // Look for runtime_call and trampoline_stub relocation records in the relocations section
    // output enabled with -XX:+PrintRelocations. We expect there to exist two runtime calls to
    // new_instance_blob emitted for 'new' expressions and a trampoline stub shared by the calls.
    // At the time of writing this, matched output lines look like the following:
    // relocInfo@0x<addr64> [type=6(runtime_call) addr=0x<addr64> offset=<int>] | [destination=0x<addr64>] C2 Runtime new_instance_blob
    // relocInfo@0x<addr64> [type=13(trampoline_stub) addr=0x<addr64> offset=<int> data=<int>] | [trampoline owner=0x<addr64>]
    private static void checkOutput(OutputAnalyzer output) {
        String testMethodStdout = getTestMethodStdout(output);
        List<String> callAddrs = Pattern.compile("relocInfo.*\\(runtime_call\\) addr=(\\w+).*new_instance_blob")
                .matcher(testMethodStdout)
                .results()
                .map(m -> m.group(1))
                .toList();

        record TrampolineReloc(String addr, String owner) {
        }
        List<TrampolineReloc> trampolineRelocs = Pattern
                .compile("relocInfo.*\\(trampoline_stub\\) addr=(\\w+) .*\\[trampoline owner=(\\w+)]")
                .matcher(testMethodStdout)
                .results()
                .map(m -> new TrampolineReloc(m.group(1), m.group(2)))
                .toList();

        List<String> trampolineAddrs = trampolineRelocs.stream()
                .filter(reloc -> callAddrs.contains(reloc.owner()))
                .map(reloc -> new String(reloc.addr()))
                .collect(Collectors.toList());
        Long distinctTrampolineAddrsCount = trampolineAddrs.stream().distinct().count();
        if (distinctTrampolineAddrsCount >= trampolineAddrs.size()) {
            throw new RuntimeException("No runtime trampoline stubs reused: distinct "
                    + distinctTrampolineAddrsCount + ", in total " + trampolineAddrs.size());
        }
    }

    public static class RuntimeCallTest {
        private static volatile Object blackholeObj;

        static class Dummy {
            int x;

            Dummy(int x) {
                this.x = x;
            }
        }

        // Use 'new' to generate runtime calls to 'new_instance_blob'
        static void test(int i) {
            Object obj = new Dummy(i);
            blackholeObj = obj;
            obj = new Dummy(i % 2);
            blackholeObj = obj;
        }

        public static void main(String[] args) {
            for (int i = 1; i < ITERATIONS_TO_HEAT_LOOP; ++i) {
                test(i);
            }
        }
    }
}
