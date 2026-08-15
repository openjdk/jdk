/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Hello World test for dynamic archive
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @compile ../test-classes/HelloInlineClassApp.java
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello_inline.jar HelloInlineClassApp HelloInlineClassApp$Point HelloInlineClassApp$Rectangle HelloInlineClassApp$ValueRecord
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. HelloDynamicInlineClass
 */

import jdk.test.lib.helpers.ClassFileInstaller;

public class HelloDynamicInlineClass extends DynamicArchiveTestBase {
    public static void main(String[] args) throws Exception {
        runTest(HelloDynamicInlineClass::test);
    }

    static void test() throws Exception {
        String topArchiveName = getNewArchiveName("top");
        String baseArchiveName = getNewArchiveName("base");
        TestCommon.dumpBaseArchive(baseArchiveName, "--enable-preview", "-Xlog:cds");
        doTest(baseArchiveName, topArchiveName);
    }

    private static void doTest(String baseArchiveName, String topArchiveName) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("hello_inline.jar");
        String mainClass = "HelloInlineClassApp";
        dump2(baseArchiveName, topArchiveName,
             "--enable-preview",
             "-Xlog:cds",
             "-Xlog:cds+dynamic=debug",
             "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                    output.shouldContain("Written dynamic archive 0x");
                });
        run2(baseArchiveName, topArchiveName,
            "--enable-preview",
            "-Xlog:class+load",
            "-Xlog:cds+dynamic=debug,cds=debug",
            "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                    output.shouldContain("HelloInlineClassApp$Point source: shared objects file")
                          .shouldHaveExitValue(0);
              });
    }
}
