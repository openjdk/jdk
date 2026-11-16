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
 * @summary Automatic modules can read all unnamed modules, and vice versa, even if AOT cache is used.
 * @bug 8393235
 * @modules java.logging
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @run driver AutomaticModuleTest AOT
 */

import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.test.lib.cds.CDSModulePackager;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AutomaticModuleTest {
    static final Path SRC = Paths.get(System.getProperty("test.src")).resolve("modules");

    public static void main(String... args) throws Exception {
        CDSModulePackager modulePackager = new CDSModulePackager(SRC);
        String modulePath = modulePackager.getOutputDir().toString();
        modulePackager.createAutomaticModularJar("com.auto");
        modulePackager.createModularJar("com.test");

        test(modulePath);
    }

    static void test(String modulePath) throws Exception {
        SimpleCDSAppTester tester =  SimpleCDSAppTester.of("AutomaticModuleTest")
            .modulepath(modulePath)
            .addVmArgs("--add-modules", "java.logging,com.test")
            .addVmArgs("--add-reads", "com.test=java.logging")
            .addVmArgs("--add-reads", "com.test=com.auto")
            .appCommandLine("-m", "com.auto/com.auto.ComAuto")
            .setTrainingChecker(out -> check(out))
            .setProductionChecker(out -> check(out))
            .runAOTWorkflow();
    }

    static void check(OutputAnalyzer out) {
        out.shouldContain("OUT: com.auto.isNamed() = true");
        out.shouldContain("OUT: com.auto.getName() = com.auto");
        out.shouldContain("OUT: com.auto.canRead(java.logging) = true");
        out.shouldContain("OUT: com.test.canRead(java.logging) = true");
        out.shouldContain("OUT: unnamed.canRead(java.logging) = true");
        out.shouldContain("OUT: com.auto.canRead(unnamed) = true");
        out.shouldContain("OUT: unnamed.canRead(com.auto) = true");
        out.shouldContain("OUT: com.auto.canRead(com.test) = true");
        out.shouldContain("OUT: com.test.canRead(com.auto) = true");
    }
}
