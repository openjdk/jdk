/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8285914
 * @summary A lambda proxy class should not be archived if its nest host implements an
 *          old (with major version < JDK_6 (50)) interface which cannot be verified during dump time.
 * @requires vm.cds
 * @requires vm.cds.custom.loaders
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile ../test-classes/OldInf.jasm ../test-classes/ChildOldInf.java ../test-classes/NestHostOldInfApp.java
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar oldclassapp.jar NestHostOldInfApp OldInf ChildOldInf ChildOldInf$InnerChild
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:./WhiteBox.jar NestHostOldInf
 */

import java.io.File;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class NestHostOldInf extends DynamicArchiveTestBase {
    private static final String ARCHIVE_NAME = CDSTestUtils.getOutputFileName("oldclass-top.jsa");
    private static String wbJar = ClassFileInstaller.getJarPath("WhiteBox.jar");
    private static String use_whitebox_jar = "-Xbootclasspath/a:" + wbJar;
    private static String appJar = ClassFileInstaller.getJarPath("oldclassapp.jar");
    private static String mainAppClass = "NestHostOldInfApp";

    public static void main(String[] args) throws Exception {
        runTest(NestHostOldInf::doTest);
    }

    private static void doTest() throws Exception {
        dump(ARCHIVE_NAME,
             use_whitebox_jar,
             "-XX:+UnlockDiagnosticVMOptions",
             "-XX:+WhiteBoxAPI",
             "-Xlog:cds",
             "-Xlog:cds+dynamic=debug",
             "-cp", appJar,
             mainAppClass)
             .assertNormalExit(output -> {
                 output.shouldContain("Written dynamic archive 0x")
                       .shouldContain("Skipping ChildOldInf: Old class has been linked")
                       .shouldContain("Skipping OldInf: Old class has been linked")
                       .shouldHaveExitValue(0);
                 });

        run(ARCHIVE_NAME,
            use_whitebox_jar,
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI",
            "-Xlog:class+load",
            "-Xlog:cds=debug",
            "-Xlog:cds+dynamic=info",
            "-cp", appJar,
            mainAppClass)
            .assertNormalExit(output -> {
                output.shouldHaveExitValue(0)
                      .shouldMatch(".class.load. OldInf source:.*oldclassapp.jar")
                      .shouldMatch(".class.load. ChildOldInf source:.*oldclassapp.jar")
                      .shouldMatch(".class.load. ChildOldInf[$]InnerChild source:.*oldclassapp.jar")
                      .shouldMatch(".class.load. ChildOldInf[$]InnerChild[$][$]Lambda.*/0x.*source:.ChildOldInf");
                });
    }
}
