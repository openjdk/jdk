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
 *
 */

/*
 * @test
 * @summary VM unsafe anonymous classes will not be archived.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @modules java.base/jdk.internal.misc
 * @compile test-classes/UnsafeAnonymousApp.java
 *          ../../../../../../lib/jdk/test/lib/compiler/InMemoryJavaCompiler.java
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller -jar unsafe.jar UnsafeAnonymousApp
 *                 jdk/test/lib/compiler/InMemoryJavaCompiler
 *                 jdk/test/lib/compiler/InMemoryJavaCompiler$FileManagerWrapper$1
 *                 jdk/test/lib/compiler/InMemoryJavaCompiler$FileManagerWrapper
 *                 jdk/test/lib/compiler/InMemoryJavaCompiler$MemoryJavaFileObject
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. UnsafeAnonymous
 */

public class UnsafeAnonymous extends DynamicArchiveTestBase {
    public static void main(String[] args) throws Exception {
        runTest(UnsafeAnonymous::test);
    }

    static void test() throws Exception {
        String topArchiveName = getNewArchiveName();
        String appJar = ClassFileInstaller.getJarPath("unsafe.jar");
        String mainClass = "UnsafeAnonymousApp";

        dump(topArchiveName,
            "-Xlog:class+load=debug,cds+dynamic,cds",
            "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
            "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                output.shouldContain("Skipping TestClass: Unsafe anonymous class")
                      .shouldHaveExitValue(0);
            });

        run(topArchiveName,
            "-Xlog:class+load=debug,cds+dynamic,cds",
            "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
            "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                output.shouldMatch("class.load.*TestClass/0x.*source.*UnsafeAnonymousApp")
                      .shouldHaveExitValue(0);
            });
    }
}
