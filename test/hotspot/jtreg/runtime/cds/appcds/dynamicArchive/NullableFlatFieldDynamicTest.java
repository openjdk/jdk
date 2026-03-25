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
 * @summary Nullable flat fields test for dynamic archive
 * @requires vm.cds
 * @requires vm.debug == true
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile ../test-classes/NullableFlatFieldApp.java
 * @compile ../../../../../../lib/jdk/test/lib/Asserts.java
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar nullable_flat.jar NullableFlatFieldApp
 *  NullableFlatFieldApp$Value0 NullableFlatFieldApp$Container0
 *  NullableFlatFieldApp$Value1a NullableFlatFieldApp$Value1b
 *  NullableFlatFieldApp$Value2a NullableFlatFieldApp$Value2b
 *  NullableFlatFieldApp$Container1 NullableFlatFieldApp$Container2
 *  NullableFlatFieldApp$Value3 NullableFlatFieldApp$Container3
 *  jdk/test/lib/Asserts
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. NullableFlatFieldDynamicTest
 */

import jdk.test.lib.helpers.ClassFileInstaller;

public class NullableFlatFieldDynamicTest extends DynamicArchiveTestBase {
    public static void main(String[] args) throws Exception {
        runTest(NullableFlatFieldDynamicTest::test);
    }

    static void test() throws Exception {
        String topArchiveName = getNewArchiveName("top");
        String baseArchiveName = getNewArchiveName("base");
        TestCommon.dumpBaseArchive(baseArchiveName, "--enable-preview", "-Xlog:cds");
        doTest(baseArchiveName, topArchiveName);
    }

    private static void doTest(String baseArchiveName, String topArchiveName) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("nullable_flat.jar");
        String mainClass = "NullableFlatFieldApp";
        dump2(baseArchiveName, topArchiveName,
             "--enable-preview",
             "-XX:+UseNullableValueFlattening",
             "-XX:+UnlockDiagnosticVMOptions",
             "-XX:+PrintInlineLayout",
             "-Xlog:cds",
             "-Xlog:cds+dynamic=debug",
             "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                    output.shouldContain("Written dynamic archive 0x");
                });
        run2(baseArchiveName, topArchiveName,
            "--enable-preview",
            "-XX:+UseNullableValueFlattening",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+PrintInlineLayout",
            "-Xlog:class+load",
            "-Xlog:cds+dynamic=debug,cds=debug",
            "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                    output.shouldContain("NullableFlatFieldApp source: shared objects file")
                          .shouldHaveExitValue(0);
              });
    }
}
