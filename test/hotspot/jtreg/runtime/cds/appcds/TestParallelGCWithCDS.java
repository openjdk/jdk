/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test Loading CDS archived heap objects into ParallelGC
 * @bug 8274788
 * @requires vm.cds
 * @requires vm.gc.Parallel
 * @requires vm.gc.G1
 *
 * @comment don't run this test if any -XX::+Use???GC options are specified, since they will
 *          interfere with the test.
 * @requires vm.gc == null
 *
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/Hello.java
 * @run driver TestParallelGCWithCDS
 */

// Below is exactly the same as above, except:
// - requires vm.bits == "64"
// - extra argument "false"

 /*
 * @test Loading CDS archived heap objects into ParallelGC
 * @bug 8274788 8341371
 * @requires vm.cds
 * @requires vm.gc.Parallel
 * @requires vm.gc.G1
 * @requires vm.bits == "64"
 *
 * @comment don't run this test if any -XX::+Use???GC options are specified, since they will
 *          interfere with the test.
 * @requires vm.gc == null
 *
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/Hello.java
 * @run driver TestParallelGCWithCDS false
 */
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;

public class TestParallelGCWithCDS {
    public final static String HELLO = "Hello World";
    static String helloJar;
    static boolean useCompressedOops = true;

    public static void main(String... args) throws Exception {
        helloJar = JarBuilder.build("hello", "Hello");

        if (args.length > 0 && args[0].equals("false")) {
            useCompressedOops = false;
        }

        // Check if we can use ParallelGC during dump time, or run time, or both.
        test(false, true);
        test(true,  false);
        test(true,  true);

        // With G1 we usually have 2 heap regions. To increase test coverage, we can have 3 heap regions
        // by using "-Xmx256m -XX:ObjectAlignmentInBytes=64"
        if (Platform.is64bit()) test(false, true, true);
    }

    final static String G1 = "-XX:+UseG1GC";
    final static String Parallel = "-XX:+UseParallelGC";

    static void test(boolean dumpWithParallel, boolean execWithParallel) throws Exception {
        test(dumpWithParallel, execWithParallel, false);
    }

    static void test(boolean dumpWithParallel, boolean execWithParallel, boolean useSmallRegions) throws Exception {
        String dumpGC = dumpWithParallel ? Parallel : G1;
        String execGC = execWithParallel ? Parallel : G1;
        String small1 = useSmallRegions ? "-Xmx256m" : "-showversion";
        String small2 = useSmallRegions ? "-XX:ObjectAlignmentInBytes=64" : "-showversion";
        String errMsg = "Cannot use CDS heap data. Selected GC not compatible -XX:-UseCompressedOops";
        String coops = useCompressedOops ? "-XX:+UseCompressedOops" : "-XX:-UseCompressedOops";
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
        out.shouldContain(HELLO);
        out.shouldNotContain(errMsg);
        out.shouldHaveExitValue(0);

        if (!dumpWithParallel && execWithParallel) {
            // We dumped with G1, so we have an archived heap. At exec time, try to load them into
            // a small ParallelGC heap that may be too small.
            System.out.println("2. Exec with " + execGC);
            out = TestCommon.exec(helloJar,
                                    execGC,
                                    small1,
                                    small2,
                                    "-Xmx4m",
                                    coops,
                                    "-Xlog:cds",
                                    "Hello");
            if (out.getExitValue() == 0) {
                out.shouldContain(HELLO);
                out.shouldNotContain(errMsg);
            } else {
                out.shouldNotHaveFatalError();
            }
        }
    }
}
