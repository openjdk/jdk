/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @requires vm.cds
 * @summary Test class loader constraint checks for archived classes (dynamic archive)
 * @library /test/lib
 *          /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive
 * @modules java.base/jdk.internal.misc
 *          jdk.httpserver
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. DynamicLoaderConstraintsTest
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import jdk.test.lib.Asserts;

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

    public static void main(String[] args) throws Exception {
        runTest(DynamicLoaderConstraintsTest::doTest);
    }

    static void doTest() throws Exception  {
        appJar = ClassFileInstaller.writeJar("loader_constraints.jar", appClasses);
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
            String topArchiveName = getNewArchiveName();
            String testCase = Integer.toString(i);
            String cmdLine[] = new String[] {
                "-cp", appJar,
                "--add-modules",
                "java.base,jdk.httpserver",
                "--add-exports",
                "java.base/jdk.internal.misc=ALL-UNNAMED",
                "-Xlog:class+load,class+loader+constraints",
                mainClass, testCase
            };

            String[] dumpCmdLine = cmdLine;
            if (!errorInDump) {
                dumpCmdLine = TestCommon.concat(dumpCmdLine, "loadClassOnly");
            }

            dump(topArchiveName, dumpCmdLine).assertNormalExit();
            run(topArchiveName, cmdLine).assertNormalExit();
        }
    }
}
