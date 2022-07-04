/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @summary test archive lambda invoker species type in dynamic dump
 * @bug 8280767
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive
 * @compile CDSLambdaInvoker.java
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar cds-test.jar CDSLambdaInvoker
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. TestLambdaInvokers
 */

public class TestLambdaInvokers extends DynamicArchiveTestBase {
    private static final String mainClass = "CDSLambdaInvoker";
    private static final String jarFile   = "cds-test.jar";
    private static void doTest(String topArchiveName) throws Exception {
        dump(topArchiveName,
             "-Xlog:cds",
             "-Xlog:cds+dynamic=debug",
             "-cp",
             jarFile,
             mainClass)
             .assertNormalExit(output -> {
                 output.shouldContain("Skip regenerating for shared");
             });
        run(topArchiveName,
             "-Xlog:cds",
             "-Xlog:cds+dynamic=debug",
             "-Xlog:class+load",
             "-cp",
             jarFile,
             mainClass)
             .assertNormalExit(output -> {
                 // java.lang.invoke.BoundMethodHandle$Species_JL is generated from CDSLambdaInvoker
                 output.shouldContain("java.lang.invoke.BoundMethodHandle$Species_JL source: shared objects file (top)");
             });
    }

    static void testWithDefaultBase() throws Exception {
        String topArchiveName = getNewArchiveName("top");
        doTest(topArchiveName);
    }

    public static void main(String[] args) throws Exception {
        runTest(TestLambdaInvokers::testWithDefaultBase);
    }
}
