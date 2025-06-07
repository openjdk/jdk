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

/**
 * @test
 * @summary Sanity test of combinations of the diagnostic flags [+-]AOTRecordTraining and [+-]AOTReplayTraining
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.flagless
 * @library /test/lib /test/setup_aot /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build AOTProfileFlags JavacBenchApp Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver AOTProfileFlags
 */

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class AOTProfileFlags {
    public static void testDiagnosticFlags() throws Exception {
        printTestCase("Diagnostic Flags");
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j ++) {
                SimpleCDSAppTester.of("AOTProfileFlags" + i + "" + j)
                    .addVmArgs("-XX:+UnlockDiagnosticVMOptions",
                               "-XX:" + (i == 0 ? "-" : "+") + "AOTRecordTraining",
                               "-XX:" + (j == 0 ? "-" : "+") + "AOTReplayTraining")
                    .classpath("app.jar")
                    .appCommandLine("JavacBenchApp", "10")
                    .runAOTWorkflow();
            }
        }
    }
    static void trainAndRun(String testName, String trainingFlags, String productionFlags, String errorPattern) throws Exception {
        printTestCase("Flags mismatch " + testName);

        String appJar = ClassFileInstaller.getJarPath("hello.jar");
        String aotConfigFile = "hello.aotconfig";
        String aotCacheFile = "hello.aot";
        String helloClass = "Hello";

        ProcessBuilder pb;
        OutputAnalyzer out;

        // first make sure we have a valid aotConfigFile with default value of TypeProfileLevel
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:+UnlockExperimentalVMOptions",
            trainingFlags,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldHaveExitValue(0);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-XX:+UnlockExperimentalVMOptions",
            trainingFlags,
            "-cp", appJar);

        out = CDSTestUtils.executeAndLog(pb, "assemble");
        out.shouldHaveExitValue(0);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "-XX:+UnlockExperimentalVMOptions",
            trainingFlags,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "production_success");
        out.shouldNotMatch(errorPattern);
        out.shouldHaveExitValue(0);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "-XX:+UnlockExperimentalVMOptions",
            productionFlags,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "production_failure");
        out.shouldMatch(errorPattern);
        out.shouldHaveExitValue(0);
    }

    public static void testFlagsMismatch() throws Exception {
        String errorPattern = ".*Profile.* setting .* does not equal the current .*Profile.* setting.*";
        trainAndRun("TypeProfileLevel", "-XX:TypeProfileLevel=222", "-XX:TypeProfileLevel=111", errorPattern);
        trainAndRun("TypeProfileArgsLimit", "-XX:TypeProfileArgsLimit=2", "-XX:TypeProfileArgsLimit=3", errorPattern);
        trainAndRun("TypeProfileParamsLimit", "-XX:TypeProfileParmsLimit=2", "-XX:TypeProfileParmsLimit=3", errorPattern);
        trainAndRun("TypeProfileWidth", "-XX:TypeProfileWidth=2", "-XX:TypeProfileWidth=3", errorPattern);
        if (Platform.isDebugBuild()) {
          trainAndRun("ProfileTraps", "-XX:+ProfileTraps", "-XX:-ProfileTraps", errorPattern);
          trainAndRun("TypeProfileCasts", "-XX:+TypeProfileCasts", "-XX:-TypeProfileCasts", errorPattern);
        }
        errorPattern = "SpecTrapLimitExtraEntries setting .* does not equal the current SpecTrapLimitExtraEntries setting";
        trainAndRun("SpecTrapLimitExtraEntries", "-XX:SpecTrapLimitExtraEntries=2", "-XX:SpecTrapLimitExtraEntries=3", errorPattern);
    }

    static int testNum = 0;
    static void printTestCase(String s) {
        System.out.println("vvvvvvv TEST CASE " + testNum + ": " + s + " starts here vvvvvvv");
        testNum++;
    }
    public static void main(String... args) throws Exception {
        testDiagnosticFlags();
        testFlagsMismatch();
    }
}
