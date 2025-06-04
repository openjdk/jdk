/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304147
 * @summary Test the exit race for dynamic dumping at exit
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @build ExitRace jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar exitRace.jar ExitRace ExitRace$1 ExitRace$1$1
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. ExitRaceTest
 */

import jdk.test.lib.helpers.ClassFileInstaller;

public class ExitRaceTest extends DynamicArchiveTestBase {

    public static void main(String[] args) throws Exception {
        runTest(ExitRaceTest::test);
    }

    static void test() throws Exception {
        String topArchiveName = getNewArchiveName();
        String appJar = ClassFileInstaller.getJarPath("exitRace.jar");
        String mainClass = "ExitRace";

        dump(topArchiveName,
             "-Xlog:cds+dynamic=debug",
             "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                                output.shouldHaveExitValue(0)
                                      .shouldContain("Preparing for dynamic dump")
                                      .reportDiagnosticSummary();
                              });
    }
}
