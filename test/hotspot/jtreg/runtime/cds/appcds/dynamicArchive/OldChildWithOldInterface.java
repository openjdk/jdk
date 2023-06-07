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
 * @summary CDS support of old child and old super classes with major version < JDK_6 (50) for dynamic archive.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @compile test-classes/DynamicOldInterface.jasm
 * @compile test-classes/DynamicOldChild2.jasm
 * @build DynamicOldChild jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar old_child_with_old_interface_app.jar DynamicOldInterface DynamicOldChild2
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. OldChildWithOldInterface
 */

 import jdk.test.lib.cds.CDSTestUtils;
 import jdk.test.lib.process.OutputAnalyzer;
 import jdk.test.lib.helpers.ClassFileInstaller;

 public class OldChildWithOldInterface extends DynamicArchiveTestBase {
     private static final String mainClass = "DynamicOldChild2";
     private static final String jarFile = ClassFileInstaller.getJarPath("old_child_with_old_interface_app.jar");
     private static void doTest(String topArchiveName) throws Exception {

         dump(topArchiveName,
             "-Xlog:class+load,cds=debug",
             "-Xlog:cds",
             "-Xlog:cds+class=debug",
             "-cp", jarFile, mainClass)
             .assertNormalExit(output -> {
                 output.shouldContain("Child.foo")
                     .shouldContain("Child.bar")
                     .shouldContain("Excluding old class DynamicOldChild2: has been regenerated")
                     .shouldContain("Excluding old class DynamicOldInterface: has been regenerated")
                     .shouldContain("DynamicOldChild2 ** generated")
                     .shouldContain("DynamicOldInterface ** generated");
             });

         run(topArchiveName,
             "-Xlog:class+load=info",
             "-cp", jarFile, mainClass)
             .assertNormalExit(output -> {
                 output.shouldContain("[class,load] DynamicOldChild2 source: shared objects file (top)")
                     .shouldContain("[class,load] DynamicOldInterface source: shared objects file (top)");
             });
     }

     static void testDefaultBase() throws Exception {
         String topArchiveName = getNewArchiveName("top");
         doTest(topArchiveName);
     }

     public static void main(String[] args) throws Exception {
         runTest(OldChildWithOldInterface::testDefaultBase);
     }
 }