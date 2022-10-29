/*
 * Copyright (c) 2022 SAP SE. All rights reserved.
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test id=global-limit
 * @summary Verify -XX:MallocLimit with a global limit
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest global-limit
 */

/*
 * @test id=compiler-limit
 * @summary Verify -XX:MallocLimit with a compiler-specific limit (for "mtCompiler" category)
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest compiler-limit
 */

/*
 * @test id=multi-limit
 * @summary Verify -XX:MallocLimit with multiple limits
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest multi-limit
 */

/*
 * @test id=valid-settings
 * @summary Verify -XX:MallocLimit rejects invalid settings
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest valid-settings
 */

/*
 * @test id=invalid-settings
 * @summary Verify -XX:MallocLimit rejects invalid settings
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest invalid-settings
 */

/*
 * @test id=limit-without-nmt
 * @summary Verify that the VM warns if -XX:MallocLimit is given but NMT is disabled
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest limit-without-nmt
 */


import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MallocLimitTest {

    private static ProcessBuilder processBuilderWithSetting(String... extraSettings) {
        List<String> args = new ArrayList<>();
        args.add("-XX:+UnlockDiagnosticVMOptions"); // MallocLimit is diagnostic
        args.add("-Xmx64m");
        args.add("-XX:-CreateCoredumpOnCrash");
        args.add("-Xlog:nmt");
        args.add("-XX:NativeMemoryTracking=summary");
        args.addAll(Arrays.asList(extraSettings));
        args.add("-version");
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(args);
        return pb;
    }

    private static void testGlobalLimit() throws IOException {
        long smallMemorySize = 1024*1024; // 1m
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=" + smallMemorySize);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain("[nmt] MallocLimit: total limit: 1024K"); // printed by byte_size_in_proper_unit()
        String s = output.firstMatch(".*MallocLimit: reached limit \\(size: (\\d+), limit: " + smallMemorySize + "\\).*", 1);
        Asserts.assertNotNull(s);
        long size = Long.parseLong(s);
        Asserts.assertGreaterThan(size, smallMemorySize);
    }

    private static void testCompilerLimit() throws IOException {
        // Here, we count on the VM, running with -Xcomp and with 1m of arena space allowed, will start a compilation
        // and then trip over the limit.
        // If limit is too small, Compiler stops too early and we won't get a Retry file (see below, we check that).
        // If limit is too large, we may not trigger it for java -version.
        // 1m seems to work out fine.
        long smallMemorySize = 1024*1024; // 1m
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=compiler:" + smallMemorySize,
                "-Xcomp" // make sure we hit the compiler category limit
        );
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain("[nmt] MallocLimit: category \"Compiler\" limit: 1024K"); // printed by byte_size_in_proper_unit
        String s = output.firstMatch(".*MallocLimit: category \"Compiler\" reached limit \\(size: (\\d+), limit: " + smallMemorySize + "\\).*", 1);
        Asserts.assertNotNull(s);
        long size = Long.parseLong(s);
        output.shouldContain("Compiler replay data is saved as");
        Asserts.assertGreaterThan(size, smallMemorySize);
    }

    private static void testMultiLimit() throws IOException {
        long smallMemorySize = 1024; // 1k
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=mtOther:2g,compiler:1g,internal:" + smallMemorySize);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldNotHaveExitValue(0);
        output.shouldContain("[nmt] MallocLimit: category \"Compiler\" limit: 1024M");
        output.shouldContain("[nmt] MallocLimit: category \"Internal\" limit: 1024B");
        output.shouldContain("[nmt] MallocLimit: category \"Other\" limit: 2048M");
        String s = output.firstMatch(".*MallocLimit: category \"Internal\" reached limit \\(size: (\\d+), limit: " + smallMemorySize + "\\).*", 1);
        long size = Long.parseLong(s);
        Asserts.assertGreaterThan(size, smallMemorySize);
    }

    private static void testValidSetting(String setting, String... expected_output) throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=" + setting);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);
        for (String expected : expected_output) {
            output.shouldContain(expected);
        }
    }

    private static void testValidSettings() throws IOException {
        // Test a number of valid settings.
        testValidSetting(
                "2097152k",
                "[nmt] MallocLimit: total limit: 2048M",
                "[nmt] NMT initialized: summary"
        );
        testValidSetting(
                "gc:1234567891,mtInternal:987654321,Object Monitors:1g",
                "[nmt] MallocLimit: category \"GC\" limit: 1177M",
                "[nmt] MallocLimit: category \"Internal\" limit: 941M",
                "[nmt] MallocLimit: category \"Object Monitors\" limit: 1024M",
                "[nmt] NMT initialized: summary"
        );
        // Set all categories individually:
        testValidSetting(
                "JavaHeap:1024m,Class:1025m,Thread:1026m,ThreadStack:1027m,Code:1028m,GC:1029m,GCCardSet:1030m,Compiler:1031m,JVMCI:1032m," +
                        "Internal:1033m,Other:1034m,Symbol:1035m,NMT:1036m,ClassShared:1037m,Chunk:1038m,Test:1039m,Tracing:1040m,Logging:1041m," +
                        "Statistics:1042m,Arguments:1043m,Module:1044m,Safepoint:1045m,Synchronizer:1046m,Serviceability:1047m,Metaspace:1048m,StringDedup:1049m,ObjectMonitor:1050m",
                "[nmt] MallocLimit: category \"Java Heap\" limit: 1024M",
                "[nmt] MallocLimit: category \"Class\" limit: 1025M",
                "[nmt] MallocLimit: category \"Thread\" limit: 1026M",
                "[nmt] MallocLimit: category \"Thread Stack\" limit: 1027M",
                "[nmt] MallocLimit: category \"Code\" limit: 1028M",
                "[nmt] MallocLimit: category \"GC\" limit: 1029M",
                "[nmt] MallocLimit: category \"GCCardSet\" limit: 1030M",
                "[nmt] MallocLimit: category \"Compiler\" limit: 1031M",
                "[nmt] MallocLimit: category \"JVMCI\" limit: 1032M",
                "[nmt] MallocLimit: category \"Internal\" limit: 1033M",
                "[nmt] MallocLimit: category \"Other\" limit: 1034M",
                "[nmt] MallocLimit: category \"Symbol\" limit: 1035M",
                "[nmt] MallocLimit: category \"Native Memory Tracking\" limit: 1036M",
                "[nmt] MallocLimit: category \"Shared class space\" limit: 1037M",
                "[nmt] MallocLimit: category \"Arena Chunk\" limit: 1038M",
                "[nmt] MallocLimit: category \"Test\" limit: 1039M",
                "[nmt] MallocLimit: category \"Tracing\" limit: 1040M",
                "[nmt] MallocLimit: category \"Logging\" limit: 1041M",
                "[nmt] MallocLimit: category \"Statistics\" limit: 1042M",
                "[nmt] MallocLimit: category \"Arguments\" limit: 1043M",
                "[nmt] MallocLimit: category \"Module\" limit: 1044M",
                "[nmt] MallocLimit: category \"Safepoint\" limit: 1045M",
                "[nmt] MallocLimit: category \"Synchronization\" limit: 1046M",
                "[nmt] MallocLimit: category \"Serviceability\" limit: 1047M",
                "[nmt] MallocLimit: category \"Metaspace\" limit: 1048M",
                "[nmt] MallocLimit: category \"String Deduplication\" limit: 1049M",
                "[nmt] MallocLimit: category \"Object Monitors\" limit: 1050M",
                "[nmt] NMT initialized: summary"
        );
    }

    private static void testInvalidSetting(String setting, String expected_error) throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=" + setting);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.reportDiagnosticSummary();
        output.shouldNotHaveExitValue(0);
        output.shouldContain(expected_error);
    }

    private static void testInvalidSettings() throws IOException {
        // Test a number of invalid settings the parser should catch. VM should abort in initialization.
        testInvalidSetting("gc", "MallocLimit: colon missing: gc");
        testInvalidSetting("gc:abc", "Invalid MallocLimit size: abc");
        testInvalidSetting("abcd:10m", "MallocLimit: invalid nmt category: abcd");
        testInvalidSetting("nmt:100m,abcd:10m", "MallocLimit: invalid nmt category: abcd");
        testInvalidSetting("0", "MallocLimit: limit must be > 0");
        testInvalidSetting("GC:0", "MallocLimit: limit must be > 0");
    }

    private static void testLimitWithoutNmt() throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:NativeMemoryTracking=off", // overrides "summary" from processBuilderWithSetting()
                "-XX:MallocLimit=3g");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.reportDiagnosticSummary();
        output.shouldHaveExitValue(0); // Not a fatal error, just a warning
        output.shouldContain("MallocLimit will be ignored since NMT is disabled");
    }

    public static void main(String args[]) throws Exception {

        if (args[0].equals("global-limit")) {
            testGlobalLimit();
        } else if (args[0].equals("compiler-limit")) {
            testCompilerLimit();
        } else if (args[0].equals("multi-limit")) {
            testMultiLimit();
        } else if (args[0].equals("valid-settings")) {
            testValidSettings();
        } else if (args[0].equals("invalid-settings")) {
            testInvalidSettings();
        } else if (args[0].equals("limit-without-nmt")) {
            testLimitWithoutNmt();
        } else {
            throw new RuntimeException("invalid test: " + args[0]);
        }
    }
}
