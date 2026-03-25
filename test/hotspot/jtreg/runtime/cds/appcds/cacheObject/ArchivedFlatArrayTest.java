/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test archived flat arrays
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.debug
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @compile ArchivedFlatArrayApp.java ArchivedArrayLayoutsApp.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar archived_flat_array.jar ArchivedFlatArrayApp
 *                                                                                  ArchivedFlatArrayApp$ArchivedData
 *                                                                                  ArchivedFlatArrayApp$CharPair
 *                                                                                  ArchivedArrayLayoutsApp
 *                                                                                  ArchivedArrayLayoutsApp$Point
 *                                                                                  ArchivedArrayLayoutsApp$ArchivedData
 * @run main/othervm ArchivedFlatArrayTest
 */

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class ArchivedFlatArrayTest {

    static String appJar = ClassFileInstaller.getJarPath("archived_flat_array.jar");
    static String mainClass = "ArchivedFlatArrayApp";
    static String mainClass2 = "ArchivedArrayLayoutsApp";

    public static void test(String className, String[] classlist) throws Exception {
        String[] suffix = TestCommon.list("--enable-preview",
                                          "-Xbootclasspath/a:" + appJar,
                                          "-XX:ArchiveHeapTestClass=" + className,
                                          "--add-exports",
                                          "java.base/jdk.internal.value=ALL-UNNAMED",
                                          "-Xlog:aot+heap");

        OutputAnalyzer output = TestCommon.dump(appJar, classlist, suffix);
        output.shouldHaveExitValue(0);
        output.shouldContain("Archived field " + className + "::archivedObjects");

        output = TestCommon.exec(appJar, TestCommon.concat(suffix, className));
        output.shouldHaveExitValue(0);
        output.shouldContain("init subgraph " + className);
        output.shouldContain("Initialized from CDS");
    }

    public static void main(String[] args) throws Exception {
        test(mainClass, TestCommon.list(mainClass, "ArchivedFlatArrayApp$ArchivedData", "ArchivedFlatArrayApp$CharPair"));
        test(mainClass, TestCommon.list(mainClass2, "ArchivedArrayLayoutsApp$ArchivedData", "ArchivedArrayLayoutsApp$Point"));
    }
}
