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
 * @summary Test archived value classes
 * @bug 8389233
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.debug
 * @library /test/jdk/lib/testlibrary /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @build AOTValueClassTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar archived_value_class.jar AOTValueClassApp
 * @run driver AOTValueClassTest
 * @run driver AOTValueClassTest INIT_TEST_CLASS
 */

import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTValueClassTest {
    public static void main(String[] args) throws Exception {
        final String mainClass = "AOTValueClassApp";
        final String appJar = ClassFileInstaller.getJarPath("archived_value_class.jar");
        boolean initTestClass = false;

        if (args.length > 0) {
            if (args[0].equals("INIT_TEST_CLASS")) {
                initTestClass = true;
            } else {
                throw new RuntimeException("Unexpected argument");
            }
        }

        SimpleCDSAppTester tester = SimpleCDSAppTester.of("AOTValueClassTest");
        if (initTestClass) {
            tester.addVmArgs("-XX:AOTInitTestClass=" + mainClass);
        }
        tester.addVmArgs("-Xlog:cds,aot,aot+class=debug", "--enable-preview")
              .appCommandLine(mainClass)
              .classpath(appJar)
              .setAssemblyChecker((OutputAnalyzer out) -> {
                  out.shouldContain("AOTValueClassApp aot-linked");
              });
        tester.runAOTWorkflow();
    }
}

value class AOTValueClassApp {
    public static void main(String[] args) {
        System.out.println("Hello value class");
    }
}
