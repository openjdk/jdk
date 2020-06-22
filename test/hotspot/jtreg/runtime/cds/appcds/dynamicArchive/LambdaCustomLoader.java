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
 * @summary Lambda proxy class loaded by a custom class loader will not be archived.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @build CustomLoaderApp LambHello sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller -jar custom_loader_app.jar CustomLoaderApp LambHello
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. LambdaCustomLoader
 */

public class LambdaCustomLoader extends DynamicArchiveTestBase {
    public static void main(String[] args) throws Exception {
        runTest(LambdaCustomLoader::test);
    }

    static void test() throws Exception {
        String topArchiveName = getNewArchiveName();
        String appJar = ClassFileInstaller.getJarPath("custom_loader_app.jar");
        String mainClass = "CustomLoaderApp";

        dump(topArchiveName,
            "-Xlog:class+load,cds,cds+dynamic",
            "-cp", appJar, mainClass, appJar)
            .assertNormalExit(output -> {
                output.shouldMatch("Skipping.LambHello[$][$]Lambda[$].*0x.*:.Hidden.class")
                      .shouldHaveExitValue(0);
            });

        run(topArchiveName,
            "-Xlog:class+load,class+unload=info",
            "-cp", appJar, mainClass, appJar)
            .assertNormalExit(output -> {
                output.shouldMatch("class.load.*LambHello[$][$]Lambda[$].*0x.*source:.LambHello")
                      .shouldHaveExitValue(0);
            });
    }
}
