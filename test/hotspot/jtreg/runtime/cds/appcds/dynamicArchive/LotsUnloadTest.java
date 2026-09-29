/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8278602
 * @summary Lots of classes being unloaded while we try to dump a dynamic archive
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @build jdk.test.whitebox.WhiteBox
 * @build LotsUnloadApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar LotsUnloadApp.jar LotsUnloadApp DefinedAsHiddenKlass
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. LotsUnloadTest
 */

// Note: for https://bugs.openjdk.org/browse/JDK-8278602, this test case does NOT
// reliably reproduce the problem. Reproduction requires patching ZGC. Please see
// the bug report for instructions.
//
// This test case is included so that it may find a similar bug under stress conditions
// in the CI runs.
import jdk.test.lib.helpers.ClassFileInstaller;

public class LotsUnloadTest extends DynamicArchiveTestBase {
    public static void main(String[] args) throws Exception {
        runTest(LotsUnloadTest::test);
    }

    static void test() throws Exception {
        String topArchiveName = getNewArchiveName();
        String appJar = ClassFileInstaller.getJarPath("LotsUnloadApp.jar");
        String mainClass = "LotsUnloadApp";
        String logging;

        if (Boolean.getBoolean("verbose.LotsUnloadTest")) {
            // class+unload logs may change GC timing and cause the bug to be
            // less reproducible.
            logging = "-Xlog:cds,class+unload";
        } else {
            logging = "-Xlog:cds";
        }

        dump(topArchiveName,
             logging,
             "-Xmx256m", "-Xms32m",
             "-cp", appJar, mainClass)
          .assertNormalExit(output -> {
                output.shouldHaveExitValue(0);
            });

        run(topArchiveName,
            logging,
            "-Xmx256m", "-Xms32m",
            "-cp", appJar, mainClass)
          .assertNormalExit(output -> {
              output.shouldHaveExitValue(0);
            });
    }
}
