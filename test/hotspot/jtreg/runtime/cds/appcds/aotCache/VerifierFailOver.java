/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Sanity test for AOTCache
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build VerifierFailOver_Helper
 * @build VerifierFailOver
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar VerifierFailOverApp VerifierFailOver_Helper
 * @run driver VerifierFailOver
 */

import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class VerifierFailOver {
    public static void main(String... args) throws Exception {
        SimpleCDSAppTester.of("VerifierFailOver")
            .addVmArgs("-Xlog:aot+class=debug")
            .classpath("app.jar")
            .appCommandLine("VerifierFailOverApp")
            .setTrainingChecker((OutputAnalyzer out) -> {
                    out.shouldContain("Skipping VerifierFailOver_Helper: Verified with old verifier");
                })
            .setAssemblyChecker((OutputAnalyzer out) -> {
                    // classes verified with fail-over mode should not be cached.
                    out.shouldMatch("class.* klasses.* VerifierFailOverApp");
                    out.shouldNotMatch("class.* klasses.* VerifierFailOver_Helper");
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
