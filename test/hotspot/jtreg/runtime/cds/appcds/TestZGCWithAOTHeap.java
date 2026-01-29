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
 * @test Loading and writing AOT archived heap objects with ZGC
 * @requires vm.cds
 * @requires vm.gc.Z
 * @requires vm.gc.G1
 *
 * @comment don't run this test if any -XX::+Use???GC options are specified, since they will
 *          interfere with the test.
 * @requires vm.gc == null
 *
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/Hello.java
 * @run driver TestZGCWithAOTHeap
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;

public class TestZGCWithAOTHeap {
    public final static String HELLO = "Hello World";
    static String helloJar;

    public static void main(String... args) throws Exception {
        helloJar = JarBuilder.build("hello", "Hello");

        // Check if we can use ZGC during dump time, or run time, or both.
        test(false, true, true, true);
        test(true,  false, true, true);
        test(true, true, true, true);
        test(false, true, false, true);
        test(false, true, true, false);
        test(true,  false, true, false);
        test(true, true, true, false);
        test(false, true, false, false);
    }

    final static String G1 = "-XX:+UseG1GC";
    final static String Z = "-XX:+UseZGC";

    static void test(boolean dumpWithZ, boolean execWithZ, boolean shouldStream, boolean shouldUseCOH) throws Exception {
        String unlockDiagnostic = "-XX:+UnlockDiagnosticVMOptions";
        String dumpGC = dumpWithZ ? Z : G1;
        String execGC = execWithZ ? Z : G1;
        String generalErrMsg = "Cannot use CDS heap data.";
        String coopsErrMsg = generalErrMsg + " Selected GC not compatible -XX:-UseCompressedOops";
        String coops = "-XX:-UseCompressedOops";
        String coh = shouldUseCOH ? "-XX:+UseCompactObjectHeaders" : "-XX:-UseCompactObjectHeaders";
        String stream = shouldStream ? "-XX:+AOTStreamableObjects" : "-XX:-AOTStreamableObjects";
        String eagerLoading = "-XX:+AOTEagerlyLoadObjects";
        OutputAnalyzer out;

        System.out.println("0. Dump with " + dumpGC + ", " + coops + ", " + coh + ", " + stream);
        out = TestCommon.dump(helloJar,
                              new String[] {"Hello"},
                              dumpGC,
                              coops,
                              coh,
                              "-XX:+UnlockDiagnosticVMOptions",
                              stream,
                              "-Xlog:cds,aot,aot+heap");
        out.shouldContain("Dumping shared data to file:");
        out.shouldHaveExitValue(0);

        System.out.println("1. Exec with " + execGC + ", " + coops + ", " + coh);
        out = TestCommon.exec(helloJar,
                              unlockDiagnostic,
                              execGC,
                              coops,
                              coh,
                              "-Xlog:cds,aot,aot+heap",
                              "Hello");
        if (!shouldStream && execWithZ) {
            // Only when dumping without streaming and executing with ZGC do we expect there
            // to be a problem. With -XX:+AOTClassLinking, the problem is worse.
            if (out.getExitValue() == 0) {
                out.shouldContain(HELLO);
                out.shouldContain(generalErrMsg);
            } else {
                out.shouldHaveExitValue(1);
            }
        } else {
            out.shouldContain(HELLO);
            out.shouldNotContain(generalErrMsg);
            out.shouldHaveExitValue(0);
        }

        // Regardless of which GC dumped the heap, there will be an object archive, either
        // created with mapping if dumped with G1, or streaming if dumped with parallel GC.
        // At exec time, try to load them into a small ZGC heap that may be too small.
        System.out.println("2. Exec with " + execGC + ", " + coops + ", " + coh);
        out = TestCommon.exec(helloJar,
                              unlockDiagnostic,
                              execGC,
                              "-Xmx4m",
                              coops,
                              coh,
                              "-Xlog:cds,aot,aot+heap",
                              "Hello");
        if (out.getExitValue() == 0) {
            if (!shouldStream && execWithZ) {
                out.shouldContain(coopsErrMsg);
            } else {
                out.shouldNotContain(generalErrMsg);
            }
        } else {
            out.shouldHaveExitValue(1);
        }
        out.shouldNotHaveFatalError();

        if (shouldStream) {
            System.out.println("3. Exec with " + execGC + ", " + coops + ", " + coh + ", " + eagerLoading);
            out = TestCommon.exec(helloJar,
                                  unlockDiagnostic,
                                  execGC,
                                  coops,
                                  coh,
                                  eagerLoading,
                                  "-Xlog:cds,aot,aot+heap",
                                  "Hello");
            if (!shouldStream && execWithZ) {
                // Only when dumping without streaming and executing with ZGC do we expect there
                // to be a problem. With -XX:+AOTClassLinking, the problem is worse.
                if (out.getExitValue() == 0) {
                    out.shouldContain(HELLO);
                    out.shouldContain(generalErrMsg);
                } else {
                    out.shouldHaveExitValue(1);
                }
            } else {
                out.shouldContain(HELLO);
                out.shouldNotContain(generalErrMsg);
                out.shouldHaveExitValue(0);
            }
        }
    }
}
