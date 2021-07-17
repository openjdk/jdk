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
 *
 */

/*
 * @test
 * @bug 8265604
 * @summary Test unlinked classes during dynamic CDS dump.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @build UnlinkedApp sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar unlinked_app.jar UnlinkedApp UnlinkedApp$Super UnlinkedApp$Sub
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. UnlinkedTest
 */

import jdk.test.lib.helpers.ClassFileInstaller;

public class UnlinkedTest extends DynamicArchiveTestBase {
    public static void main(String[] args) throws Exception {
        runTest(UnlinkedTest::test);
    }

    static void test() throws Exception {
        String topArchiveName = getNewArchiveName();
        String appJar = ClassFileInstaller.getJarPath("unlinked_app.jar");
        String mainClass = "UnlinkedApp";
        String[] appClasses = { mainClass, mainClass + "$Super", mainClass + "$Sub" };

        // 1. Inner classes of UnlinkedApp are not being linked during dump time.
        dump(topArchiveName,
            "-Xlog:class+load,cds=debug,verification",
            "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
                for (String appClass : appClasses) {
                    output.shouldContain("Verifying class " +  appClass + " with new format");
                }
            });

        run(topArchiveName,
            "-Xlog:class+load",
            "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
                for (String appClass : appClasses) {
                    output.shouldContain("[class,load] " + appClass + " source: shared objects file (top)");
                }
            });

        // 2. Inner classes of UnlinkedApp are being linked during dump time.
        dump(topArchiveName,
            "-Xlog:class+load,cds=debug,verification",
            "-cp", appJar, mainClass, "link")
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
                for (String appClass : appClasses) {
                    output.shouldContain("Verifying class " + appClass + " with new format");
                }
            });

        run(topArchiveName,
            "-Xlog:class+load",
            "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
                for (String appClass : appClasses) {
                    output.shouldContain("[class,load] " + appClass + " source: shared objects file (top)");
                }
            });
    }
}
