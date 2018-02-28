/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary When dumping the CDS archive, try to cause garbage collection while classes are being loaded.
 * @library /test/lib /test/hotspot/jtreg/runtime/appcds /test/hotspot/jtreg/runtime/appcds/test-classes
 * @requires vm.cds
 * @requires vm.flavor != "minimal"
 * @modules java.base/jdk.internal.misc
 *          jdk.jartool/sun.tools.jar
 *          java.management
 * @build GCDuringDumpTransformer Hello
 * @run main/othervm GCDuringDump
 */

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class GCDuringDump {
    public static String appClasses[] = {
        "Hello",
    };
    public static String agentClasses[] = {
        "GCDuringDumpTransformer",
    };

    public static void main(String[] args) throws Throwable {
        String agentJar =
            ClassFileInstaller.writeJar("GCDuringDumpTransformer.jar",
                                        ClassFileInstaller.Manifest.fromSourceFile("GCDuringDumpTransformer.mf"),
                                        agentClasses);

        String appJar =
            ClassFileInstaller.writeJar("GCDuringDumpApp.jar", appClasses);

        String gcLog = "-Xlog:gc*=info,gc+region=trace,gc+alloc+region=debug";

        for (int i=0; i<2; i++) {
            // i = 0 -- run without agent = no extra GCs
            // i = 1 -- run with agent = cause extra GCs

            String extraArg = (i == 0) ? "-showversion" : "-javaagent:" + agentJar;

            TestCommon.testDump(appJar, TestCommon.list("Hello"),
                                extraArg, "-Xmx32m", gcLog);

            TestCommon.run(
                "-cp", appJar,
                "-Xmx32m",
                "-XX:+PrintSharedSpaces",
                gcLog,
                "Hello")
              .assertNormalExit();
        }
    }
}

