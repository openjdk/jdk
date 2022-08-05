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
 * @test id=global_limit
 * @summary Verify -XX:MallocLimit with a global limit
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest global_limit
 */

/*
 * @test id=compiler_limit
 * @summary Verify -XX:MallocLimit with a compiler-specific limit (for "mtCompiler" category)
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest compiler_limit
 */

/*
 * @test id=test_multi_limit
 * @summary Verify -XX:MallocLimit with multiple limits
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest multi_limit
 */

/*
 * @test id=test_valid_settings
 * @summary Verify -XX:MallocLimit rejects invalid settings
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest valid_settings
 */

/*
 * @test id=test_invalid_settings
 * @summary Verify -XX:MallocLimit rejects invalid settings
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest invalid_settings
 */

/*
 * @test id=test_limit_without_nmt
 * @summary Verify that the VM warns if -XX:MallocLimit is given but NMT is disabled
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MallocLimitTest limit_without_nmt
 */


import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;

public class MallocLimitTest {

    private static ProcessBuilder processBuilderWithSetting(String... extra_settings) {
        String[] vmargs = new String[] {
            "-XX:+UnlockDiagnosticVMOptions", // MallocLimit is diagnostic
            "-Xmx64m", "-XX:-CreateCoredumpOnCrash", "-Xlog:nmt",
            "-XX:NativeMemoryTracking=summary"
        };
        String[] vmargs2 = new String[] { "-version" };
        String[] both = Stream.concat(Stream.concat(Arrays.stream(vmargs), Arrays.stream(extra_settings)), Arrays.stream(vmargs2))
                .toArray(String[]::new);
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(both);
        return pb;
    }

    private static void test_global_limit() throws IOException {
        long small_memory_size = 1024*1024; // 1m
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=" + small_memory_size);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.reportDiagnosticSummary();
        output.shouldNotHaveExitValue(0);
        output.shouldContain("[nmt] MallocLimit: total limit: " + small_memory_size);
        String s = output.firstMatch(".*MallocLimit: reached limit \\(size: (\\d+), limit: " + small_memory_size + "\\).*", 1);
        Asserts.assertNotNull(s);
        long size = Long.parseLong(s);
        Asserts.assertGreaterThan(size, small_memory_size);
    }

    private static void test_compiler_limit() throws IOException {
        // Here, we count on the VM, running with -Xcomp and with 1m of arena space allowed, will start a compilation
        // and then trip over the limit.
        // If limit is too small, Compiler stops too early and we won't get a Retry file (see below, we check that).
        // If limit is too large, we may not trigger it for java -version.
        // 1m seems to work out fine.
        long small_memory_size = 1024*1024; // 1m
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=compiler:" + small_memory_size,
                "-Xcomp" // make sure we hit the compiler category limit
        );
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.reportDiagnosticSummary();
        output.shouldNotHaveExitValue(0);
        output.shouldContain("[nmt] MallocLimit: category \"Compiler\" limit: " + small_memory_size);
        String s = output.firstMatch(".*MallocLimit: category \"Compiler\" reached limit \\(size: (\\d+), limit: " + small_memory_size + "\\).*", 1);
        Asserts.assertNotNull(s);
        long size = Long.parseLong(s);
        output.shouldContain("Compiler replay data is saved as");
        Asserts.assertGreaterThan(size, small_memory_size);
    }

    private static void test_multi_limit() throws IOException {
        long small_memory_size = 1024; // 1k
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=mtOther:2g,compiler:1g,internal:" + small_memory_size);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.reportDiagnosticSummary();
        output.shouldNotHaveExitValue(0);
        output.shouldContain("[nmt] MallocLimit: category \"Compiler\" limit: 1073741824");
        output.shouldContain("[nmt] MallocLimit: category \"Internal\" limit: " + small_memory_size);
        output.shouldContain("[nmt] MallocLimit: category \"Other\" limit: 2147483648");
        String s = output.firstMatch(".*MallocLimit: category \"Internal\" reached limit \\(size: (\\d+), limit: " + small_memory_size + "\\).*", 1);
        long size = Long.parseLong(s);
        Asserts.assertGreaterThan(size, small_memory_size);
    }

    private static void test_valid_setting(String setting, String... expected_output) throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=" + setting);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.reportDiagnosticSummary();
        output.shouldHaveExitValue(0);
        for (String expected : expected_output) {
            output.shouldContain(expected);
        }
    }

    private static void test_valid_settings() throws IOException {
        // Test a number of valid settings.
        test_valid_setting(
                "2097152k",
                "[nmt] MallocLimit: total limit: 2147483648",
                "[nmt] NMT initialized: summary"
        );
        test_valid_setting(
                "gc:1234567891,mtInternal:987654321,Object Monitors:1g",
                "[0.001s][info][nmt] MallocLimit: category \"GC\" limit: 1234567891",
                "[nmt] MallocLimit: category \"Internal\" limit: 987654321",
                "[nmt] MallocLimit: category \"Object Monitors\" limit: 1073741824",
                "[nmt] NMT initialized: summary"
        );
        // Set all categories individually:
        test_valid_setting(
                "JavaHeap:1024m,Class:1025m,Thread:1026m,ThreadStack:1027m,Code:1028m,GC:1029m,GCCardSet:1030m,Compiler:1031m,JVMCI:1032m," +
                        "Internal:1033m,Other:1034m,Symbol:1035m,NMT:1036m,ClassShared:1037m,Chunk:1038m,Test:1039m,Tracing:1040m,Logging:1041m," +
                        "Statistics:1042m,Arguments:1043m,Module:1044m,Safepoint:1045m,Synchronizer:1046m,Serviceability:1047m,Metaspace:1048m,StringDedup:1049m,ObjectMonitor:1050m",
                "[nmt] MallocLimit: category \"Java Heap\" limit: 1073741824",
                "[nmt] MallocLimit: category \"Class\" limit: 1074790400",
                "[nmt] MallocLimit: category \"Thread\" limit: 1075838976",
                "[nmt] MallocLimit: category \"Thread Stack\" limit: 1076887552",
                "[nmt] MallocLimit: category \"Code\" limit: 1077936128",
                "[nmt] MallocLimit: category \"GC\" limit: 1078984704",
                "[nmt] MallocLimit: category \"GCCardSet\" limit: 1080033280",
                "[nmt] MallocLimit: category \"Compiler\" limit: 1081081856",
                "[nmt] MallocLimit: category \"JVMCI\" limit: 1082130432",
                "[nmt] MallocLimit: category \"Internal\" limit: 1083179008",
                "[nmt] MallocLimit: category \"Other\" limit: 1084227584",
                "[nmt] MallocLimit: category \"Symbol\" limit: 1085276160",
                "[nmt] MallocLimit: category \"Native Memory Tracking\" limit: 1086324736",
                "[nmt] MallocLimit: category \"Shared class space\" limit: 1087373312",
                "[nmt] MallocLimit: category \"Arena Chunk\" limit: 1088421888",
                "[nmt] MallocLimit: category \"Test\" limit: 1089470464",
                "[nmt] MallocLimit: category \"Tracing\" limit: 1090519040",
                "[nmt] MallocLimit: category \"Logging\" limit: 1091567616",
                "[nmt] MallocLimit: category \"Statistics\" limit: 1092616192",
                "[nmt] MallocLimit: category \"Arguments\" limit: 1093664768",
                "[nmt] MallocLimit: category \"Module\" limit: 1094713344",
                "[nmt] MallocLimit: category \"Safepoint\" limit: 1095761920",
                "[nmt] MallocLimit: category \"Synchronization\" limit: 1096810496",
                "[nmt] MallocLimit: category \"Serviceability\" limit: 1097859072",
                "[nmt] MallocLimit: category \"Metaspace\" limit: 1098907648",
                "[nmt] MallocLimit: category \"String Deduplication\" limit: 1099956224",
                "[nmt] MallocLimit: category \"Object Monitors\" limit: 1101004800",
                "[nmt] NMT initialized: summary"
        );
    }

    private static void test_invalid_setting(String setting, String expected_error) throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:MallocLimit=" + setting);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.reportDiagnosticSummary();
        output.shouldNotHaveExitValue(0);
        output.shouldContain(expected_error);
    }

    private static void test_invalid_settings() throws IOException {
        // Test a number of invalid settings the parser should catch. VM should abort in initialization.
        test_invalid_setting("gc", "MallocLimit: colon missing: gc");
        test_invalid_setting("gc:abc", "Invalid MallocLimit size: abc");
        test_invalid_setting("abcd:10m", "MallocLimit: invalid nmt category: abcd");
        test_invalid_setting("nmt:100m,abcd:10m", "MallocLimit: invalid nmt category: abcd");
        test_invalid_setting("0", "MallocLimit: limit must be > 0");
        test_invalid_setting("GC:0", "MallocLimit: limit must be > 0");
    }

    private static void test_limit_without_nmt() throws IOException {
        ProcessBuilder pb = processBuilderWithSetting("-XX:NativeMemoryTracking=off", // overrides "summary" from processBuilderWithSetting()
                "-XX:MallocLimit=3g");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.reportDiagnosticSummary();
        output.shouldHaveExitValue(0); // Not a fatal error, just a warning
        output.shouldContain("MallocLimit will be ignored since NMT is disabled");
    }

    public static void main(String args[]) throws Exception {

        if (args[0].equals("global_limit")) {
            test_global_limit();
        } else if (args[0].equals("compiler_limit")) {
            test_compiler_limit();
        } else if (args[0].equals("multi_limit")) {
            test_multi_limit();
        } else if (args[0].equals("valid_settings")) {
            test_valid_settings();
        } else if (args[0].equals("invalid_settings")) {
            test_invalid_settings();
        } else if (args[0].equals("limit_without_nmt")) {
            test_limit_without_nmt();
        } else {
            throw new RuntimeException("invalid test: " + args[0]);
        }
    }
}
