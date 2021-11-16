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
 * @bug 8276184
 * @summary If the caller class is redefined during dump time, the caller class
 *          and its lambda proxy class should not be archived.
 * @requires vm.cds
 * @requires vm.jvmti
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @build sun.hotspot.WhiteBox OldProvider
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run driver RedefineClassHelper
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. RedefineCallerClassTest
 */

import jdk.test.lib.helpers.ClassFileInstaller;

public class RedefineCallerClassTest extends DynamicArchiveTestBase {
    static String mainClass = RedefineCallerClass.class.getName();

    static String providerClass = OldProvider.class.getName();

    static String sharedClasses[] = {
        mainClass,
        "SimpleLambda", // caller class will be redefined in RedefineCallerClass
        providerClass,  // inteface with class file major version < 50
        "jdk/test/lib/compiler/InMemoryJavaCompiler",
        "jdk/test/lib/compiler/InMemoryJavaCompiler$FileManagerWrapper",
        "jdk/test/lib/compiler/InMemoryJavaCompiler$FileManagerWrapper$1",
        "jdk/test/lib/compiler/InMemoryJavaCompiler$MemoryJavaFileObject"
    };

    public static void main(String[] args) throws Exception {
        runTest(RedefineCallerClassTest::test);
    }

    static void test() throws Exception {
        String topArchiveName = getNewArchiveName();
        String appJar = ClassFileInstaller.writeJar("redefine_caller_class.jar", sharedClasses);

        String[] mainArgs = {
            "redefineCaller", // redefine caller class only
            "useOldInf",      // use old interface only
            "both"            // both of the above
        };

        for (String mainArg : mainArgs) {
            String[] options = {
                "-Xlog:class+load,cds",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+AllowArchivingWithJavaAgent",
                "-javaagent:redefineagent.jar",
                "-cp", appJar, mainClass, mainArg
            };

            dump(topArchiveName, options)
                .assertNormalExit(output -> {
                    output.shouldHaveExitValue(0);
                    if (mainArg.equals("both") || mainArg.equals("useOldInf")) {
                        output.shouldContain("Skipping OldProvider: Old class has been linked")
                              .shouldMatch("Skipping.SimpleLambda[$][$]Lambda[$].*0x.*:.*Old.class.has.been.linked");
                    }
                    if (mainArg.equals("both") || mainArg.equals("redefineCaller")) {
                        output.shouldContain("Skipping SimpleLambda: Has been redefined");
                    }
                });

            run(topArchiveName, options)
                .assertNormalExit(output -> {
                    output.shouldHaveExitValue(0)
                          .shouldContain("RedefineCallerClass source: shared objects file (top)")
                          .shouldMatch(".class.load. SimpleLambda[$][$]Lambda[$].*/0x.*source:.*SimpleLambda");
                    if (mainArg.equals("both") || mainArg.equals("useOldInf")) {
                        output.shouldMatch(".class.load. OldProvider.source:.*redefine_caller_class.jar");
                    }
                    if (mainArg.equals("both") || mainArg.equals("redefineCaller")) {
                        output.shouldMatch(".class.load. SimpleLambda.source:.*redefine_caller_class.jar");
                    }
                });
        }
    }
}
