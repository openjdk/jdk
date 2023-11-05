/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test Verifier handling of invoking java/lang/Object::clone() on object arrays.
 * @bug 8286277
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/verifier
 *          /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build jdk.test.whitebox.WhiteBox
 * @build InvokeCloneValid InvokeCloneInvalid VerifyObjArrayCloneTestApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar VerifyObjArrayCloneTestApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar tests.jar InvokeCloneValid InvokeCloneInvalid
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:./WhiteBox.jar VerifyObjArrayCloneTest
 */

import java.io.File;
import jdk.test.lib.helpers.ClassFileInstaller;

public class VerifyObjArrayCloneTest extends DynamicArchiveTestBase {
    private static String appJar = ClassFileInstaller.getJarPath("app.jar");
    private static String testsJar = ClassFileInstaller.getJarPath("tests.jar");
    private static String mainAppClass = "VerifyObjArrayCloneTestApp";

    public static void main(String[] args) throws Exception {
        runTest(VerifyObjArrayCloneTest::testInAppPath);
        runTest(VerifyObjArrayCloneTest::testInCustomLoader);
    }

    // Try to load InvokeCloneValid and InvokeCloneInvalid from the AppClassLoader
    private static void testInAppPath() throws Exception {
        String cp = appJar + File.pathSeparator + testsJar;
        String topArchiveName = getNewArchiveName("top");
        dump(topArchiveName,
             "-cp", cp,
             mainAppClass)
             .assertNormalExit();

        run(topArchiveName,
            "-cp", cp,
             mainAppClass)
            .assertNormalExit();
    }

    // Try to load InvokeCloneValid and InvokeCloneInvalid from a custom class loader
    private static void testInCustomLoader() throws Exception {
        String cp = appJar;
        String topArchiveName = getNewArchiveName("top");
        dump(topArchiveName,
             "-cp", cp,
             "-Xlog:cds+class=debug",
             mainAppClass, testsJar)
             .assertNormalExit();

        run(topArchiveName,
            "-cp", cp,
             mainAppClass, testsJar)
            .assertNormalExit();
    }
}
