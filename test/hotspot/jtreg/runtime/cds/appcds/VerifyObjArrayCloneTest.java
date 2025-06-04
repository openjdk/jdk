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
 * @library /test/lib /test/hotspot/jtreg/runtime/verifier /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build InvokeCloneValid InvokeCloneInvalid VerifyObjArrayCloneTestApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar VerifyObjArrayCloneTestApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar tests.jar InvokeCloneValid InvokeCloneInvalid
 * @run driver VerifyObjArrayCloneTest
 */

import java.io.File;
import jdk.test.lib.Platform;
import jdk.test.lib.helpers.ClassFileInstaller;

public class VerifyObjArrayCloneTest {
    private static String appJar = ClassFileInstaller.getJarPath("app.jar");
    private static String testsJar = ClassFileInstaller.getJarPath("tests.jar");
    private static String mainAppClass = "VerifyObjArrayCloneTestApp";

    public static void main(String... args) throws Exception {
        testInAppPath();
        if (Platform.areCustomLoadersSupportedForCDS()) {
            testInCustomLoader();
        }
    }

    // Try to load InvokeCloneValid and InvokeCloneInvalid from the AppClassLoader
    static void testInAppPath() throws Exception {
        String cp = appJar + File.pathSeparator + testsJar;
        TestCommon.dump(cp, TestCommon.list(mainAppClass,
                                            "InvokeCloneValid",
                                            "InvokeCloneInvalid"));

        TestCommon.run("-cp", cp, "-Xlog:cds+verification=trace",
                       mainAppClass)
            .assertNormalExit();
    }

    // Try to load InvokeCloneValid and InvokeCloneInvalid from a custom class loader
    static void testInCustomLoader() throws Exception {
        String cp = appJar;

        String classlist[] = new String[] {
            mainAppClass,
            "java/lang/Object id: 1",
            "InvokeCloneValid id: 2 super: 1 source: " + testsJar,
            "InvokeCloneInvalid id: 3 super: 1 source: " + testsJar,
        };

        TestCommon.dump(cp, classlist);
        TestCommon.run("-cp", cp, "-Xlog:cds+verification=trace",
                       mainAppClass, testsJar)
            .assertNormalExit();
    }
}
