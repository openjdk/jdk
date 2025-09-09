/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test id=aot
 * @bug 8362566
 * @summary Test the contents of -Xlog:aot+map with AOT workflow
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds
 * @build AOTMapTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTMapTestApp
 * @run driver AOTMapTest AOT --two-step-training
 */

/**
 * @test id=dynamic
 * @bug 8362566
 * @summary Test the contents of -Xlog:aot+map with AOT workflow
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @build AOTMapTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTMapTestApp
 * @run  main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. AOTMapTest DYNAMIC
 */


import java.util.ArrayList;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.Platform;

public class AOTMapTest {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "AOTMapTestApp";

    public static void main(String[] args) throws Exception {
        doTest(args);
    }

    public static void doTest(String[] args) throws Exception {
        Tester tester = new Tester();
        tester.run(args);

        validate(tester.dumpMapFile);
        validate(tester.runMapFile);
    }

    static void validate(String mapFileName) {
        CDSMapReader.MapFile mapFile = CDSMapReader.read(mapFileName);
        CDSMapReader.validate(mapFile);
    }

    static class Tester extends CDSAppTester {
        String dumpMapFile;
        String runMapFile;

        public Tester() {
            super(mainClass);

            dumpMapFile = "test" + "0" + ".dump.aotmap";
            runMapFile  = "test" + "0" + ".run.aotmap";
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            ArrayList<String> vmArgs = new ArrayList<>();

            vmArgs.add("-Xmx128M");
            vmArgs.add("-Xlog:aot=debug");

            // filesize=0 ensures that a large map file not broken up in multiple files.
            String logMapPrefix = "-Xlog:aot+map=debug,aot+map+oops=trace:file=";
            String logMapSuffix = ":none:filesize=0";

            if (runMode == RunMode.ASSEMBLY || runMode == RunMode.DUMP_DYNAMIC) {
                vmArgs.add(logMapPrefix + dumpMapFile + logMapSuffix);
            } else if (runMode == RunMode.PRODUCTION) {
                vmArgs.add(logMapPrefix + runMapFile + logMapSuffix);
            }

            return vmArgs.toArray(new String[vmArgs.size()]);
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
            };
        }
    }
}

class AOTMapTestApp {
    public static void main(String[] args) {
        System.out.println("Hello AOTMapTestApp");
    }
}
