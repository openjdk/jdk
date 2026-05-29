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

/**
 * @test
 * @summary Sanity test of the diagnostic flag -XX:+VerifyAOTCode
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.flagless
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @comment Both C1 and C2 JIT compilers are required because the test verifies
 *          all AOT code tiers generation (except tier 3).
 * @library /test/lib /test/setup_aot
 * @build TestVerifyAOTCode JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver/timeout=480 TestVerifyAOTCode
 */

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestVerifyAOTCode {

    public static void main(String... args) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("app.jar");
        String aotConfigFile = "app.aotconfig";
        String aotCacheFile = "app.aot";
        String appClass = "JavacBenchApp";
        String appArgs = "10";

        ProcessBuilder pb;
        OutputAnalyzer out;

        // first make sure we have a valid aotConfigFile
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Xlog:aot",
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-cp", appJar, appClass, appArgs);

        out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldHaveExitValue(0);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Xlog:aot+codecache+exit",
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-cp", appJar);

        out = CDSTestUtils.executeAndLog(pb, "assemble");
        out.shouldMatch("aot,codecache,exit.*\\s+Tier 1:.*");
        out.shouldMatch("aot,codecache,exit.*\\s+Tier 2:.*");
        out.shouldMatch("aot,codecache,exit.*\\s+Tier 3:.*");
        out.shouldMatch("aot,codecache,exit.*\\s+Tier 4:.*");
        out.shouldMatch("aot,codecache,exit.*\\s+Tier 5:.*");
        out.shouldHaveExitValue(0);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Xlog:aot+codecache+init",
            "-Xlog:aot+codecache+nmethod",
            "-XX:AOTCache=" + aotCacheFile,
            "-XX:+CITime",
            "-cp", appJar, appClass, appArgs);

        out = CDSTestUtils.executeAndLog(pb, "production_default");
        out.shouldMatch("Loaded nmethod from AOT Code Cache");
        out.shouldMatch("  AOT Code T1.*");
        out.shouldMatch("  AOT Code T2.*");
        out.shouldMatch("  AOT Code T4.*");
        out.shouldMatch("  AOT Code T5.*");
        // Tier 3 AOT code generation is not implemented
        out.shouldNotMatch("\\s+AOT Code T3.*");
        out.shouldHaveExitValue(0);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Xlog:aot+codecache+init",
            "-Xlog:aot+codecache+nmethod",
            "-XX:AOTCache=" + aotCacheFile,
            "-XX:+CITime",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+VerifyAOTCode",
            "-cp", appJar, appClass, appArgs);

        out = CDSTestUtils.executeAndLog(pb, "production_VerifyAOTCode");
        out.shouldMatch("Verified nmethod from AOT Code Cache");
        out.shouldNotMatch("Loaded nmethod from AOT Code Cache");
        out.shouldMatch("  AOT Code T1.*");
        out.shouldMatch("  AOT Code T2.*");
        out.shouldMatch("  AOT Code T4.*");
        out.shouldMatch("  AOT Code T5.*");
        out.shouldHaveExitValue(0);
    }
}
