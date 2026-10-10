/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8365575
 * @summary Sanity test for AOTCache
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build VerifierFailOver_Helper
 * @build VerifierFailOver
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar VerifierFailOverApp VerifierFailOver_Helper
 * @run driver VerifierFailOver
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class VerifierFailOver {

    static final String mainClass = VerifierFailOverApp.class.getName();
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");

    public static void main(String... args) throws Exception {
        test(true);
        test(false);
    }

    static void test(boolean aotClassLinking) throws Exception {
        SimpleCDSAppTester.of("VerifierFailOver")
            .addVmArgs("-Xlog:aot,aot+class=debug")
            .addVmArgs("-XX:" + (aotClassLinking ? "+" : "-") + "AOTClassLinking")
            .classpath("app.jar")
            .appCommandLine("VerifierFailOverApp")
            .setTrainingChecker((OutputAnalyzer out) -> {
                    if (aotClassLinking) {
                        out.shouldMatch("class.* klasses.* VerifierFailOver_Helper");
                    } else {
                        out.shouldMatch("Skipping VerifierFailOver_Helper: Verified with old verifier");
                    }
                })
            .setAssemblyChecker((OutputAnalyzer out) -> {
                    if (aotClassLinking) {
                        // Classes verified with fail-over can be cached if AOTClassLinking is on
                        out.shouldMatch("class.* klasses.* VerifierFailOverApp aot-linked");
                        out.shouldMatch("class.* klasses.* VerifierFailOver_Helper aot-linked");
                    } else {
                        out.shouldMatch("class.* klasses.* VerifierFailOverApp");
                        out.shouldNotMatch("class.* klasses.* VerifierFailOverApp_Helper");
                    }
                })
            .runAOTWorkflow();
    }
}

class VerifierFailOverApp {
    public static void main(String[] args) throws Throwable {
        Class goodClass = Class.forName("VerifierFailOver_Helper");
        Object obj = goodClass.newInstance();
        System.out.println("Successfully loaded: " + obj.getClass().getName());
    }
}
