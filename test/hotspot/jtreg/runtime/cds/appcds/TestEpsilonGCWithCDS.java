/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @test Loading CDS archived heap objects into EpsilonGC
 * @bug 8234679
 * @requires vm.cds
 * @requires vm.gc.Epsilon
 * @requires vm.gc.G1
 *
 * @comment don't run this test if any -XX::+Use???GC options are specified, since they will
 *          interfere with the the test.
 * @requires vm.gc == null
 *
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/Hello.java
 * @run driver TestEpsilonGCWithCDS
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;

public class TestEpsilonGCWithCDS {
    public final static String HELLO = "Hello World";
    static String helloJar;

    public static void main(String... args) throws Exception {
        helloJar = JarBuilder.build("hello", "Hello");

        // Check if we can use EpsilonGC during dump time, or run time, or both.
        test(false, true);
        test(true,  false);
        test(true,  true);

        // We usually have 2 heap regions. To increase test coverage, we can have 3 heap regions
        // by using "-Xmx256m -XX:ObjectAlignmentInBytes=64"
        if (Platform.is64bit()) test(false, true, true);
    }

    final static String G1 = "-XX:+UseG1GC";
    final static String Epsilon = "-XX:+UseEpsilonGC";
    final static String experiment = "-XX:+UnlockExperimentalVMOptions";

    static void test(boolean dumpWithEpsilon, boolean execWithEpsilon) throws Exception {
        test(dumpWithEpsilon, execWithEpsilon, false);
    }

    static void test(boolean dumpWithEpsilon, boolean execWithEpsilon, boolean useSmallRegions) throws Exception {
        String dumpGC = dumpWithEpsilon ? Epsilon : G1;
        String execGC = execWithEpsilon ? Epsilon : G1;
        String small1 = useSmallRegions ? "-Xmx256m" : "-showversion";
        String small2 = useSmallRegions ? "-XX:ObjectAlignmentInBytes=64" : "-showversion";
        OutputAnalyzer out;

        System.out.println("0. Dump with " + dumpGC);
        out = TestCommon.dump(helloJar,
                              new String[] {"Hello"},
                              experiment,
                              dumpGC,
                              small1,
                              small2,
                              "-Xlog:cds");
        out.shouldContain("Dumping shared data to file:");
        out.shouldHaveExitValue(0);

        System.out.println("1. Exec with " + execGC);
        out = TestCommon.exec(helloJar,
                              experiment,
                              execGC,
                              small1,
                              small2,
                              "-Xlog:cds",
                              "Hello");
        out.shouldContain(HELLO);
        out.shouldHaveExitValue(0);
    }
}
