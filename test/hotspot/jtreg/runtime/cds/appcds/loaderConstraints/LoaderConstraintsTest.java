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
 * @summary Test class loader constraint checks for archived classes
 * @library /test/lib
 *          /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @modules java.base/jdk.internal.misc
 *          jdk.httpserver
 * @run driver LoaderConstraintsTest
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import jdk.test.lib.Asserts;

public class LoaderConstraintsTest  {
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

    static void doTest() throws Exception  {
        appJar = ClassFileInstaller.writeJar("loader_constraints.jar", appClasses);
        TestCommon.dump(appJar, appClasses, "-Xlog:cds+load");
        String joptsMain[] = TestCommon.concat("-cp", appJar,
                                          "-Xlog:cds",
                                          "-Xlog:class+loader+constraints=debug",
                                          "--add-exports",
                                          "java.base/jdk.internal.misc=ALL-UNNAMED",
                                          mainClass);
        runWithArchive(joptsMain, "1");
        runWithArchive(joptsMain, "2");
        runWithArchive(joptsMain, "3");
    }

    static void runWithArchive(String[] optsMain, String arg) throws Exception {
        String cmd[] = TestCommon.concat(optsMain, arg);
        TestCommon.run(cmd).assertNormalExit();
    }

    public static void main(String... args) throws Exception {
        doTest();
    }
}

