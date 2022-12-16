/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test Loading CDS archived heap objects into SerialGC
 * @bug 8234679
 * @requires vm.cds
 * @requires vm.gc.Serial
 * @requires vm.gc.G1
 *
 * @comment don't run this test if any -XX::+Use???GC options are specified, since they will
 *          interfere with the test.
 * @requires vm.gc == null
 *
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/Hello.java
 * @run driver TestSerialGCWithCDS
 */

// Below is exactly the same as above, except:
// - requires vm.bits == "64"
// - extra argument "false"

/*
 * @test Loading CDS archived heap objects into SerialGC
 * @bug 8234679
 * @requires vm.cds
 * @requires vm.gc.Serial
 * @requires vm.gc.G1
 * @requires vm.bits == "64"
 *
 * @comment don't run this test if any -XX::+Use???GC options are specified, since they will
 *          interfere with the test.
 * @requires vm.gc == null
 *
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/Hello.java
 * @run driver TestSerialGCWithCDS false
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;

public class TestSerialGCWithCDS {
    public final static String HELLO = "Hello World";
    static String helloJar;
    static boolean useCompressedOops = true;

    public static void main(String... args) throws Exception {
        helloJar = JarBuilder.build("hello", "Hello");

        if (args.length > 0 && args[0].equals("false")) {
            useCompressedOops = false;
        }

        // Check if we can use SerialGC during dump time, or run time, or both.
        test(false, true);
        test(true,  false);
        test(true,  true);

        // We usually have 2 heap regions. To increase test coverage, we can have 3 heap regions
        // by using "-Xmx256m -XX:ObjectAlignmentInBytes=64"
        if (Platform.is64bit()) {
            test(false, true, /*useSmallRegions=*/true);
        }
    }

    final static String G1 = "-XX:+UseG1GC";
    final static String Serial = "-XX:+UseSerialGC";

    static void test(boolean dumpWithSerial, boolean execWithSerial) throws Exception {
        test(dumpWithSerial, execWithSerial, false);
    }

    static void test(boolean dumpWithSerial, boolean execWithSerial, boolean useSmallRegions) throws Exception {
        String DUMMY = "-showversion"; // A harmless option that doesn't doesn't do anything except for printing out the version
        String dumpGC = dumpWithSerial ? Serial : G1;
        String execGC = execWithSerial ? Serial : G1;
        String small1 = useSmallRegions ? "-Xmx256m" : DUMMY;
        String small2 = useSmallRegions ? "-XX:ObjectAlignmentInBytes=64" : DUMMY;
        String coops;
        if (Platform.is64bit()) {
            coops = useCompressedOops ? "-XX:+UseCompressedOops" : "-XX:-UseCompressedOops";
        } else {
            coops = DUMMY;
        }
        OutputAnalyzer out;

        System.out.println("0. Dump with " + dumpGC);
        out = TestCommon.dump(helloJar,
                              new String[] {"Hello"},
                              dumpGC,
                              small1,
                              small2,
                              coops,
                              "-Xlog:cds");
        out.shouldContain("Dumping shared data to file:");
        out.shouldHaveExitValue(0);

        System.out.println("1. Exec with " + execGC);
        out = TestCommon.exec(helloJar,
                              execGC,
                              small1,
                              small2,
                              coops,
                              "-Xlog:cds",
                              "Hello");
        checkExecOutput(dumpWithSerial, execWithSerial, out);

        System.out.println("2. Exec with " + execGC + " and test ArchiveRelocationMode");
        out = TestCommon.exec(helloJar,
                              execGC,
                              small1,
                              small2,
                              coops,
                              "-Xlog:cds,cds+heap",
                              "-XX:ArchiveRelocationMode=1", // always relocate shared metadata
                              "Hello");
        if (out.getOutput().contains("Trying to map heap") || out.getOutput().contains("Loaded heap")) {
            // The native data in the RO/RW regions have been relocated. If the CDS heap is
            // mapped/loaded, we must patch all the native pointers. (CDS heap is
            // not supported on all platforms)
            out.shouldContain("Patching native pointers in heap region");
        }
        checkExecOutput(dumpWithSerial, execWithSerial, out);

        int n = 2;
        if (dumpWithSerial == false && execWithSerial == true) {
            // We dumped with G1, so we have an archived heap. At exec time, try to load them into
            // a small SerialGC heap that may be too small.
            String[] sizes = {
                "4m",   // usually this will success load the archived heap
                "2m",   // usually this will fail to load the archived heap, but app can launch
                        // or fail with "GC triggered before VM initialization completed"
                "1m"    // usually this will cause VM launch to fail with "Too small maximum heap"
            };
            for (String sz : sizes) {
                String xmx = "-Xmx" + sz;
                System.out.println("=======\n" + n + ". Exec with " + execGC + " " + xmx);
                out = TestCommon.exec(helloJar,
                                      execGC,
                                      small1,
                                      small2,
                                      xmx,
                                      coops,
                                      "-Xlog:cds",
                                      "Hello");
                if (out.getExitValue() == 0) {
                    checkExecOutput(dumpWithSerial, execWithSerial, out);
                } else {
                    String output = out.getStdout() + out.getStderr();
                    String exp1 = "Too small maximum heap";
                    String exp2 = "GC triggered before VM initialization completed";
                    if (!output.contains(exp1) && !output.contains(exp2)) {
                        throw new RuntimeException("Either '" + exp1 + "' or '" + exp2 + "' must be in stdout/stderr \n");
                    }
                }
                n++;
            }
        }
    }

    static void checkExecOutput(boolean dumpWithSerial, boolean execWithSerial, OutputAnalyzer out) {
        String errMsg = "Cannot use CDS heap data. UseG1GC is required for -XX:-UseCompressedOops";
        if (Platform.is64bit() &&
            !Platform.isWindows() && // archive heap not supported on Windows.
            !dumpWithSerial && // Dumped with G1, so we have an archived heap
            execWithSerial && // Running with serial
            !useCompressedOops) { // ArchiveHeapLoader::can_load() always returns false when COOP is disabled
            out.shouldContain(errMsg);
        }
        if (!execWithSerial) {
            // We should never see this message with G1
            out.shouldNotContain(errMsg);
        }
    }
}
