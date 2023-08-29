/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary handling of the existence of InstanceKlass::array_klasses()
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @build ArrayKlassesApp
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar ArrayKlasses.jar ArrayKlassesApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. ArrayKlasses
 */

import jdk.test.lib.helpers.ClassFileInstaller;

public class ArrayKlasses extends DynamicArchiveTestBase {
    public static void main(String[] args) throws Exception {
        runTest(ArrayKlasses::test);
    }

    static void test() throws Exception {
        String topArchiveName = getNewArchiveName();
        final String appJar = ClassFileInstaller.getJarPath("ArrayKlasses.jar");
        final String mainClass = "ArrayKlassesApp";
        final String runtimeLogOptions =
            "-Xlog:class+load,class+load+array=debug,cds+dynamic=debug,cds=debug,cds+unshareable=trace";

        // Case 1
        // Create a dynamic archive with the ArrayKlassesApp app class and its
        // array classes.
        dump2(null, topArchiveName,
              "-Xlog:cds+dynamic=debug,cds+class=debug",
              "-cp", appJar, mainClass)
              .assertNormalExit(output -> {
                     output.shouldMatch("cds.class.*klasses.*array \\[LArrayKlassesApp;")
                           .shouldMatch("cds.class.*klasses.*array \\[\\[LArrayKlassesApp;")
                           .shouldMatch("cds.class.*klasses.*array \\[\\[\\[LArrayKlassesApp;");
                 });

        // Case 1
        // At runtime , the ArrayKlasesApp and its array class should be loaded
        // from the dynamic archive.
        run2(null, topArchiveName, runtimeLogOptions,
             "-cp", appJar, mainClass)
             .assertNormalExit(output -> {
                     output.shouldContain("ArrayKlassesApp source: shared objects file (top)")
                           .shouldContain("restore: ArrayKlassesApp with class loader: jdk.internal.loader.ClassLoaders$AppClassLoader")
                           .shouldContain("[LArrayKlassesApp; source: shared objects file (top)")
                           .shouldContain("restore: [LArrayKlassesApp; with class loader: jdk.internal.loader.ClassLoaders$AppClassLoader")
                           .shouldContain("[[LArrayKlassesApp; source: shared objects file (top)")
                           .shouldContain("restore: [[LArrayKlassesApp; with class loader: jdk.internal.loader.ClassLoaders$AppClassLoader")
                           .shouldContain("[[[LArrayKlassesApp; source: shared objects file (top)")
                           .shouldContain("restore: [[[LArrayKlassesApp; with class loader: jdk.internal.loader.ClassLoaders$AppClassLoader")
                           .shouldHaveExitValue(0);
                 });

        // Case 2
        // Create a dynamic archive with the array classes of java/util/Date which
        // is in the default CDS archive.
        topArchiveName = getNewArchiveName();
        dump2(null, topArchiveName,
              "-Xlog:class+load,cds+dynamic=debug,cds+class=debug",
              "-cp", appJar, mainClass, "system")
              .assertNormalExit(output -> {
                     output.shouldContain("java.util.Date source: shared objects file")
                           .shouldMatch("cds.class.*klasses.*array \\[Ljava.util.Date;")
                           .shouldMatch("cds.class.*klasses.*array \\[\\[Ljava.util.Date;")
                           .shouldMatch("cds.class.*klasses.*array \\[\\[\\[Ljava.util.Date;");
                 });

        // Case 2
        // At runtime, the java/util/Date class should be loaded from the default
        // CDS archive; its array class should be loaded from the dynamic archive.
        run2(null, topArchiveName, runtimeLogOptions,
             "-cp", appJar, mainClass, "system")
             .assertNormalExit(output -> {
                     output.shouldContain("java.util.Date source: shared objects file")
                           .shouldContain("restore: java.util.Date with class loader: boot")
                           .shouldContain("[Ljava.util.Date; source: shared objects file (top)")
                           .shouldContain("restore: [Ljava.util.Date; with class loader: boot")
                           .shouldContain("[[Ljava.util.Date; source: shared objects file (top)")
                           .shouldContain("restore: [[Ljava.util.Date; with class loader: boot")
                           .shouldContain("[[[Ljava.util.Date; source: shared objects file (top)")
                           .shouldContain("restore: [[[Ljava.util.Date; with class loader: boot")
                           .shouldHaveExitValue(0);
                 });

        // Case 3
        // Create a dynamic archive with primitive arrays [[J and [[[J with [J
        // already in the default CDS archive
        topArchiveName = getNewArchiveName();
        dump2(null, topArchiveName,
              "-Xlog:class+load,cds+dynamic=debug,cds+class=debug",
              "-cp", appJar, mainClass, "primitive")
              .assertNormalExit(output -> {
                     output.shouldMatch("cds.class.*klasses.*array \\[\\[J")
                           .shouldMatch("cds.class.*klasses.*array \\[\\[\\[J");
                 });

        // Case 3
        // At runtime, the [J should be loaded from the default CDS archive;
        // the higher-dimension array should be loaded from the dynamic archive.
        run2(null, topArchiveName, runtimeLogOptions,
             "-cp", appJar, mainClass, "primitive")
             .assertNormalExit(output -> {
                     output.shouldContain("[J source: shared objects file")
                           .shouldContain("restore: [J with class loader: boot")
                           .shouldContain("[[J source: shared objects file (top)")
                           .shouldContain("restore: [[J with class loader: boot")
                           .shouldContain("[[[J source: shared objects file (top)")
                           .shouldContain("restore: [[[J with class loader: boot")
                           .shouldHaveExitValue(0);
                 });

        // Case 4
        // Create a dynamic archive with 2-, 3- and 4-dimension arrays of java/lang/Integer.
        // The java/lang/Integer class and the 1-dimension array is in the default archive.
        topArchiveName = getNewArchiveName();
        dump2(null, topArchiveName,
              "-Xlog:class+load,cds+dynamic=debug,cds+class=debug",
              "-cp", appJar, mainClass, "integer-array")
              .assertNormalExit(output -> {
                     output.shouldMatch("cds.class.*klasses.*array \\[\\[Ljava.lang.Integer;")
                           .shouldMatch("cds.class.*klasses.*array \\[\\[\\[Ljava.lang.Integer;")
                           .shouldMatch("cds.class.*klasses.*array \\[\\[\\[\\[Ljava.lang.Integer;");
                 });

        // Case 4
        // At runtime, the 4-dimension array of java/lang/Integer should be
        // loaded from the dynamic archive.
        run2(null, topArchiveName, runtimeLogOptions,
             "-cp", appJar, mainClass, "integer-array")
             .assertNormalExit(output -> {
                     output.shouldContain("java.lang.Integer source: shared objects file")
                           .shouldContain("restore: java.lang.Integer with class loader: boot")
                           .shouldContain("restore: [Ljava.lang.Integer; with class loader: boot")
                           .shouldContain("[[Ljava.lang.Integer; source: shared objects file (top)")
                           .shouldContain("restore: [[Ljava.lang.Integer; with class loader: boot")
                           .shouldContain("[[[Ljava.lang.Integer; source: shared objects file (top)")
                           .shouldContain("restore: [[[Ljava.lang.Integer; with class loader: boot")
                           .shouldContain("[[[[Ljava.lang.Integer; source: shared objects file (top)")
                           .shouldContain("restore: [[[[Ljava.lang.Integer; with class loader: boot")
                           .shouldHaveExitValue(0);
                 });
    }
}
