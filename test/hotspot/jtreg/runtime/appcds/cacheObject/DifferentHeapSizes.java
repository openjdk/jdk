/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary Test automatic relocation of archive heap regions dur to heap size changes.
 * @requires vm.cds.archived.java.heap
 * @requires (vm.gc=="null")
 * @library /test/lib /test/hotspot/jtreg/runtime/appcds
 * @modules jdk.jartool/sun.tools.jar
 * @compile ../test-classes/Hello.java
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. DifferentHeapSizes
 */

import jdk.test.lib.process.OutputAnalyzer;
import sun.hotspot.WhiteBox;
import jdk.test.lib.cds.CDSTestUtils;

public class DifferentHeapSizes {
    static class Scenario {
        int dumpSize;   // in MB
        int runSizes[]; // in MB
        Scenario(int ds, int... rs) {
            dumpSize = ds;
            runSizes = rs;
        }
    }

    static Scenario[] scenarios = {
        //           dump -Xmx ,         run -Xmx
        new Scenario(        32,         32, 64, 512, 2048, 4097, 16374, 31000),
        new Scenario(       128,         32, 64, 512, 2048, 4097, 16374, 31000, 40000),
        new Scenario(      2048,         32, 512, 2600, 4097, 8500, 31000,      40000),
        new Scenario(     17000,         32, 512, 2048, 4097, 8500, 31000,      40000),
        new Scenario(     31000,         32, 512, 2048, 4097, 8500, 17000,      40000)
    };

    public static void main(String[] args) throws Exception {
        String dedup = "-XX:+UseStringDeduplication"; // This increases code coverage.
        JarBuilder.getOrCreateHelloJar();
        String appJar = TestCommon.getTestJar("hello.jar");
        String appClasses[] = TestCommon.list("Hello");

        for (Scenario s : scenarios) {
            String dumpXmx = "-Xmx" + s.dumpSize + "m";
            OutputAnalyzer output = TestCommon.dump(appJar, appClasses, dumpXmx);

            for (int runSize : s.runSizes) {
                String runXmx = "-Xmx" + runSize + "m";
                CDSTestUtils.Result result = TestCommon.run("-cp", appJar, "-showversion",
                        "-Xlog:cds", runXmx, dedup, "Hello");
                if (runSize < 32768) {
                    result
                        .assertNormalExit("Hello World")
                        .assertNormalExit(out -> {
                            out.shouldNotContain(CDSTestUtils.MSG_RANGE_NOT_WITHIN_HEAP);
                            out.shouldNotContain(CDSTestUtils.MSG_RANGE_ALREADT_IN_USE);
                        });
                } else {
                    result.assertAbnormalExit("Unable to use shared archive: UseCompressedOops and UseCompressedClassPointers must be on for UseSharedSpaces.");
                }
            }
        }
        String flag = "HeapBaseMinAddress";
        String xxflag = "-XX:" + flag + "=";
        String mx = "-Xmx128m";
        long base = WhiteBox.getWhiteBox().getSizeTVMFlag(flag).longValue();

        TestCommon.dump(appJar, appClasses, mx, xxflag + base);
        TestCommon.run("-cp", appJar, "-showversion", "-Xlog:cds", mx, xxflag + (base + 256 * 1024 * 1024), dedup, "Hello")
            .assertNormalExit("Hello World")
            .assertNormalExit(out -> {
                    out.shouldNotContain(CDSTestUtils.MSG_RANGE_NOT_WITHIN_HEAP);
                    out.shouldNotContain(CDSTestUtils.MSG_RANGE_ALREADT_IN_USE);
                });
    }
}
