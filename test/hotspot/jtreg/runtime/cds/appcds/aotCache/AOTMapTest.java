/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /test/hotspot/jtreg/runtime/cds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build AOTMapTest Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTMapTestApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar cust.jar Hello
 * @run driver/timeout=240 AOTMapTest AOT --two-step-training
 */

/**
 * @test id=dynamic
 * @bug 8362566
 * @summary Test the contents of -Xlog:aot+map with dynamic CDS archive
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @build AOTMapTest Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTMapTestApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar cust.jar Hello
 * @run main/othervm/timeout=240 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. AOTMapTest DYNAMIC
 */

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.Platform;

public class AOTMapTest {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "AOTMapTestApp";
    static final String classLoadLogFile = "production.class.load.log";

    public static void main(String[] args) throws Exception {
        doTest(args);
    }

    public static void doTest(String[] args) throws Exception {
        Tester tester = new Tester();
        tester.run(args);

        if (tester.isDynamicWorkflow()) {
            // For dynamic workflow, the AOT map file doesn't include classes in the base archive, so
            // AOTMapReader.validateClasses() will fail.
            validate(tester.dumpMapFile, false);
        } else {
            validate(tester.dumpMapFile, true);
        }
        validate(tester.runMapFile, true);
    }

    static void validate(String mapFileName, boolean checkClases) throws Exception {
        AOTMapReader.MapFile mapFile = AOTMapReader.read(mapFileName);
        if (checkClases) {
            AOTMapReader.validate(mapFile, classLoadLogFile);
        } else {
            AOTMapReader.validate(mapFile, null);
        }
        mapFile.shouldHaveClass("AOTMapTestApp"); // built-in class
        mapFile.shouldHaveClass("Hello"); // unregistered class
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
            String logSuffix = ":none:filesize=0";

            if (runMode == RunMode.ASSEMBLY || runMode == RunMode.DUMP_DYNAMIC) {
                vmArgs.add(logMapPrefix + dumpMapFile + logSuffix);
            } else if (runMode == RunMode.PRODUCTION) {
                vmArgs.add(logMapPrefix + runMapFile + logSuffix);
                vmArgs.add("-Xlog:class+load:file=" + classLoadLogFile + logSuffix);
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
    static URLClassLoader loader; // keep Hello class alive
    public static void main(String[] args) throws Exception {
        System.out.println("Hello AOTMapTestApp");
        testCustomLoader();
    }

    static void testCustomLoader() throws Exception {
        File custJar = new File("cust.jar");
        URL[] urls = new URL[] {custJar.toURI().toURL()};
        loader = new URLClassLoader(urls, AOTMapTestApp.class.getClassLoader());
        Class<?> c = loader.loadClass("Hello");
        System.out.println(c);
    }
}
