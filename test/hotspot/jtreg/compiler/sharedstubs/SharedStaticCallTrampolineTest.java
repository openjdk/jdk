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
 * @test SharedStaticCallTrampolineTest id=C2
 * @summary Checks that trampolines can be shared between static calls.
 * @bug 8359359
 * @library /test/lib
 *
 * @requires vm.compiler2.enabled
 * @requires os.arch=="aarch64"
 * @requires vm.debug
 *
 * @run driver compiler.sharedstubs.SharedStaticCallTrampolineTest -XX:-TieredCompilation
 */

package compiler.sharedstubs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class SharedStaticCallTrampolineTest {
    private final static int ITERATIONS_TO_HEAT_LOOP = 20_000;

    private static void runTest(String compiler, String test) throws Exception {
        String testClassName = SharedStaticCallTrampolineTest.class.getName() + "$" + test;
        ArrayList<String> command = new ArrayList<String>();
        command.add(compiler);
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-Xbatch");
        command.add("-XX:+PrintRelocations");
        command.add("-XX:CompileCommand=compileonly," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "test");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "foo");
        command.add("-XX:CompileCommand=dontinline," + testClassName + "::" + "bar");
        command.add(testClassName);
        command.add("a");

        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        System.out.println(analyzer.getOutput());

        checkOutput(analyzer);
    }

    public static void main(String[] args) throws Exception {
        String[] tests = new String[] { "StaticCallTest" };
        for (String test : tests) {
            runTest(args[0], test);
        }
    }

    private static String getTestMethodStdout(OutputAnalyzer output) {
        return Pattern.compile("Compiled method.*StaticCallTest::test").split(output.getStdout(), 2)[1];
    }

    // Look for static_call and trampoline_stub relocation records in the relocations section output
    // enabled with -XX:+PrintRelocations. We expect there to exist two static calls to foo() and
    // another static call to bar() and a trampoline stub shared by the foo() calls.
    // At the time of writing this, matched output lines look like the following:
    // relocInfo@0x<addr64> [type=4(static_call) addr=0x<addr64> offset=<int>] | [destination=0x<addr64> metadata=0x<addr64>] Blob::Shared Runtime resolve_static_call_blob
    // relocInfo@0x<addr64> [type=13(trampoline_stub) addr=0x<addr64> offset=<int> data=<int>] | [trampoline owner=0x<addr64>]
    private static void checkOutput(OutputAnalyzer output) {
        String testMethodStdout = getTestMethodStdout(output);
        List<String> callAddrs = Pattern.compile("relocInfo.*\\(static_call\\) addr=(\\w+).*resolve_static_call_blob")
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
        if (trampolineAddrs.stream().distinct().count() >= trampolineAddrs.size()) {
            throw new RuntimeException("No trampoline stubs shared across static calls: static calls "
                    + callAddrs.size() + ", trampoline_stubs " + trampolineAddrs.size());
        }

        List<String> unsharedTrampolineAddrs = trampolineAddrs.stream()
                .filter(addr -> Collections.frequency(trampolineAddrs, addr) == 1)
                .collect(Collectors.toList());
        if (unsharedTrampolineAddrs.size() == 0) {
            throw new RuntimeException(
                    "A trampoline stub is unexpectedly shared with a unique static call: static calls"
                            + callAddrs.size() + ", trampoline_stubs " + trampolineAddrs.size());
        }
    }

    public static class StaticCallTest {
        private static void foo() {
        }

        private static void bar() {
        }

        static void test(int i, String[] args) {
            foo();
            foo();
            bar();
        }

        public static void main(String[] args) {
            for (int i = 1; i < ITERATIONS_TO_HEAT_LOOP; ++i) {
                test(i, args);
            }
        }
    }
}
