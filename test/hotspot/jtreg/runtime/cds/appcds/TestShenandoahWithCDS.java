/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

/*
 * @test
 * @bug 8293650
 * @requires vm.cds
 * @requires vm.bits == 64
 * @requires vm.gc.Shenandoah
 * @requires vm.gc.G1
 * @requires vm.gc == null
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/Hello.java
 * @run driver TestShenandoahWithCDS
 */

// Below is exactly the same as above, except:
// - requires vm.bits == "64"
// - extra argument "false"

/*
 * @test
 * @bug 8293650 8341371
 * @requires vm.cds
 * @requires vm.bits == 64
 * @requires vm.gc.Shenandoah
 * @requires vm.gc.G1
 * @requires vm.gc == null
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/Hello.java
 * @run driver TestShenandoahWithCDS false
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;

public class TestShenandoahWithCDS {
    public final static String HELLO = "Hello World";
    static String helloJar;
    static boolean useCompressedOops = true;

    public static void main(String... args) throws Exception {
        helloJar = JarBuilder.build("hello", "Hello");

        if (args.length > 0 && args[0].equals("false")) {
            useCompressedOops = false;
        }

        // Run with the variety of region sizes, and combinations
        // of G1/Shenandoah at dump/exec times. "-1" means to use G1.
        final int[] regionSizes = { -1, 256, 512, 1024, 2048 };

        for (int dumpRegionSize : regionSizes) {
            for (int execRegionSize : regionSizes) {
                test(dumpRegionSize, execRegionSize);
            }
        }
    }

    static void test(int dumpRegionSize, int execRegionSize) throws Exception {
        String exp = "-XX:+UnlockExperimentalVMOptions";
        String optDumpGC = (dumpRegionSize != -1) ? "-XX:+UseShenandoahGC" : "-XX:+UseG1GC";
        String optExecGC = (execRegionSize != -1) ? "-XX:+UseShenandoahGC" : "-XX:+UseG1GC";
        String optDumpRegionSize = (dumpRegionSize != -1) ? "-XX:ShenandoahRegionSize=" + dumpRegionSize + "K" : exp;
        String optExecRegionSize = (execRegionSize != -1) ? "-XX:ShenandoahRegionSize=" + execRegionSize + "K" : exp;
        String errMsg = "Cannot use CDS heap data. Selected GC not compatible -XX:-UseCompressedOops";
        String coops = useCompressedOops ? "-XX:+UseCompressedOops" : "-XX:-UseCompressedOops";
        OutputAnalyzer out;

        System.out.println("0. Dump with " + optDumpGC + " and " + optDumpRegionSize);
        out = TestCommon.dump(helloJar,
                              new String[] {"Hello"},
                              exp,
                              "-Xmx1g",
                              optDumpGC,
                              optDumpRegionSize,
                              coops,
                              "-Xlog:cds");
        out.shouldContain("Dumping shared data to file:");
        out.shouldHaveExitValue(0);

        System.out.println("1. Exec with " + optExecGC + " and " + optExecRegionSize);
        out = TestCommon.exec(helloJar,
                              exp,
                              "-Xmx1g",
                              optExecGC,
                              optExecRegionSize,
                              coops,
                              "-Xlog:cds",
                              "Hello");
        out.shouldContain(HELLO);
        out.shouldNotContain(errMsg);
        out.shouldHaveExitValue(0);
    }
}
