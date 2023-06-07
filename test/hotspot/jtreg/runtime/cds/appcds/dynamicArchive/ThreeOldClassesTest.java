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
 * @summary CDS support of an old class with a method returning an old class, and another old
 * class within that class with major version < JDK_6 (50) for dynamic archive.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @compile test-classes/Foo.jasm
 * @compile test-classes/Bar.jasm
 * @compile test-classes/Bam.jasm
 * @build Foo jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar three_old_classes_test_app.jar Foo Foo$Bar Foo$Bar$Bam
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. ThreeOldClassesTest
 */

 import jdk.test.lib.cds.CDSTestUtils;
 import jdk.test.lib.process.OutputAnalyzer;
 import jdk.test.lib.helpers.ClassFileInstaller;

 public class ThreeOldClassesTest extends DynamicArchiveTestBase {
     private static final String mainClass = "Foo";
     private static final String jarFile = ClassFileInstaller.getJarPath("three_old_classes_test_app.jar");
     private static void doTest(String topArchiveName) throws Exception {

         dump(topArchiveName,
             "-Xlog:class+load,cds=debug",
             "-Xlog:cds",
             "-Xlog:cds+class=debug",
             "-cp", jarFile, mainClass)
             .assertNormalExit(output -> {
                 output.shouldContain("Excluding old class Foo: has been regenerated")
                     .shouldContain("Excluding old class Foo$Bar: has been regenerated")
                     .shouldContain("Excluding old class Foo$Bar$Bam: has been regenerated")
                     .shouldContain("Foo ** generated")
                     .shouldContain("Foo$Bar ** generated")
                     .shouldContain("Foo$Bar$Bam ** generated");
             });

         run(topArchiveName,
             "-Xlog:class+load=info",
             "-cp", jarFile, mainClass, "RandomArg1")
             .assertNormalExit(output -> {
                 output.shouldContain("[class,load] Foo source: shared objects file (top)")
                     .shouldContain("[class,load] Foo$Bar source: shared objects file (top)")
                     .shouldContain("[class,load] Foo$Bar$Bam source: shared objects file (top)");
             });
     }

     static void testDefaultBase() throws Exception {
         String topArchiveName = getNewArchiveName("top");
         doTest(topArchiveName);
     }

     public static void main(String[] args) throws Exception {
         runTest(ThreeOldClassesTest::testDefaultBase);
     }
 }