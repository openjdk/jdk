/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=default-cl
 * @requires vm.cds
 * @summary Test class loader constraint checks for archived classes (dynamic archive)
 * @library /test/lib
 *          /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive
 * @modules java.base/jdk.internal.misc
 *          jdk.httpserver
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. DynamicLoaderConstraintsTest
 */

/**
 * @test id=custom-cl
 * @requires vm.cds.custom.loaders
 * @summary Test class loader constraint checks for archived classes (dynamic archive) with custom class loader
 * @bug 8267347
 * @library /test/lib
 *          /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive
 * @modules java.base/jdk.internal.misc
 *          jdk.httpserver
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. DynamicLoaderConstraintsTest custom
 */

/**
 * @test id=custom-cl-zgc
 * @requires vm.cds.custom.loaders
 * @requires vm.gc.Z
 * @summary Test dumptime_table entries are removed with zgc eager class unloading
 * @bug 8274935
 * @library /test/lib
 *          /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive
 * @modules java.base/jdk.internal.misc
 *          jdk.httpserver
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=180 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. DynamicLoaderConstraintsTest custom-zgc
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import jdk.test.lib.Asserts;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.Platform;

public class DynamicLoaderConstraintsTest extends DynamicArchiveTestBase {
    static String mainClass = LoaderConstraintsApp.class.getName();
    static String appJar = null;
    static String appClasses[] = {
        mainClass,
        HttpHandler.class.getName(),
        HttpExchange.class.getName(),
        Asserts.class.getName(),
        MyHttpHandler.class.getName(),
        MyHttpHandlerB.class.getName(),
        MyHttpHandlerC.class.getName(),
        MyClassLoader.class.getName()
    };

    static String loaderMainClass = CustomAppLoader.class.getName();
    static String loaderJar = null;
    static String loaderClasses[] = {
        loaderMainClass
    };

    /*
     * useCustomLoader: if true, load the LoaderConstraintsApp in a custom loader before executing it.
     *                  if false, LoaderConstraintsApp will be loaded by the built-in AppClassLoader.
     */
    static boolean useCustomLoader;
    static boolean useZGC;

    public static void main(String[] args) throws Exception {
        useCustomLoader = (args.length != 0);
        useZGC = (args.length != 0 && args[0].equals("custom-zgc"));
        runTest(DynamicLoaderConstraintsTest::doTest);
    }

    static void doTest() throws Exception  {
        appJar = ClassFileInstaller.writeJar("loader_constraints.jar", appClasses);
        if (useCustomLoader) {
            loaderJar = ClassFileInstaller.writeJar("custom_app_loader.jar", loaderClasses);
        }
        doTest(false);
        doTest(true);
    }

    /*
     * errorInDump:
     * true:  Even when dumping the archive, execute the code that would cause
     *        LinkageError, to see how the VM can handle such error during
     *        dump time.
     * false: At dump time, simply load all the necessary test classes without
     *        causing LinkageError. This ensures the test classes will be
     *        archived so we can test CDS's handling of loader constraints during
     *        run time.
     */
  static void doTest(boolean errorInDump) throws Exception  {
        for (int i = 1; i <= 3; i++) {
            System.out.println("========================================");
            System.out.println("errorInDump: " + errorInDump + ", useCustomLoader: " + useCustomLoader +
                               ", useZGC: " + useZGC + ", case: " + i);
            System.out.println("========================================");
            String topArchiveName = getNewArchiveName();
            String testCase = Integer.toString(i);
            String cmdLine[] = new String[] {
                "--add-modules",
                "java.base,jdk.httpserver",
                "--add-exports",
                "java.base/jdk.internal.misc=ALL-UNNAMED",
                "-Xlog:cds=debug,class+load,class+loader+constraints",
            };

            if (useCustomLoader) {
                if (useZGC) {
                    // Add options to force eager class unloading.
                    cmdLine = TestCommon.concat(cmdLine, "-cp", loaderJar,
                                                "-XX:+UseZGC", "-XX:ZCollectionInterval=0.01",
                                                loaderMainClass, appJar);
                    setBaseArchiveOptions("-XX:+UseZGC", "-Xlog:cds");
                } else {
                    cmdLine = TestCommon.concat(cmdLine, "-cp", loaderJar,
                                                loaderMainClass, appJar);
                }
            } else {
                cmdLine = TestCommon.concat(cmdLine, "-cp", appJar);
            }

            cmdLine = TestCommon.concat(cmdLine, mainClass, testCase);

            String[] dumpCmdLine = cmdLine;
            if (!errorInDump) {
                dumpCmdLine = TestCommon.concat(dumpCmdLine, "loadClassOnly");
            }

            dump(topArchiveName, dumpCmdLine).assertNormalExit();
            run(topArchiveName, cmdLine).assertNormalExit();
        }
    }
}
