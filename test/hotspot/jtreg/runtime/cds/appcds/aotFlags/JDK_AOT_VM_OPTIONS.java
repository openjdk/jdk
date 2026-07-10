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
 * @summary JDK_AOT_VM_OPTIONS environment variable
 * @requires vm.cds
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver JDK_AOT_VM_OPTIONS
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class JDK_AOT_VM_OPTIONS {
    static String appJar = ClassFileInstaller.getJarPath("hello.jar");
    static String aotConfigFile = "hello.aotconfig";
    static String aotCacheFile = "hello.aot";
    static String helloClass = "Hello";

    public static void main(String[] args) throws Exception {
        ProcessBuilder pb;
        OutputAnalyzer out;

        //----------------------------------------------------------------------
        printTestCase("JDK_AOT_VM_OPTIONS (single-command training)");

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTCacheOutput=" + aotCacheFile,
            "-Xlog:aot=debug",
            "-cp", appJar, helloClass);
        // The "-Xshare:off" below should be treated as part of a property value and not
        // a VM option by itself
        pb.environment().put("JDK_AOT_VM_OPTIONS", "-Dsome.option='foo -Xshare:off ' -Xmx512m -XX:-AOTClassLinking");
        out = CDSTestUtils.executeAndLog(pb, "ontstep-train");
        out.shouldContain("Hello World");
        out.shouldContain("AOTCache creation is complete: hello.aot");
        out.shouldContain("Picked up JDK_AOT_VM_OPTIONS: -Dsome.option='foo -Xshare:off '");
        checkAOTClassLinkingDisabled(out);

        //----------------------------------------------------------------------
        printTestCase("JDK_AOT_VM_OPTIONS (two-command training)");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-Xlog:aot=debug",
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldContain("Hello World");
        out.shouldContain("AOTConfiguration recorded: " + aotConfigFile);
        out.shouldHaveExitValue(0);

        //----------------------------------------------------------------------
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-Xlog:aot",
            "-cp", appJar);
        pb.environment().put("JDK_AOT_VM_OPTIONS", "-XX:-AOTClassLinking");
        out = CDSTestUtils.executeAndLog(pb, "asm");
        out.shouldContain("Picked up JDK_AOT_VM_OPTIONS:");
        checkAOTClassLinkingDisabled(out);

        //----------------------------------------------------------------------
        printTestCase("JDK_AOT_VM_OPTIONS (with AOTMode specified in -XX:VMOptionsFile)");
        String optionsFile = "opts.txt";
        Files.writeString(Path.of(optionsFile), "-XX:AOTMode=create");

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:VMOptionsFile=" + optionsFile,
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-Xlog:aot",
            "-cp", appJar);
        pb.environment().put("JDK_AOT_VM_OPTIONS", "-XX:-AOTClassLinking");
        out = CDSTestUtils.executeAndLog(pb, "asm");
        out.shouldContain("Picked up JDK_AOT_VM_OPTIONS:");
        checkAOTClassLinkingDisabled(out);

        //----------------------------------------------------------------------
        printTestCase("Using -XX:VMOptionsFile inside JDK_AOT_VM_OPTIONS)");
        Files.writeString(Path.of(optionsFile), "-XX:-AOTClassLinking");

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-Xlog:aot",
            "-cp", appJar);
        pb.environment().put("JDK_AOT_VM_OPTIONS", "-XX:VMOptionsFile="  + optionsFile);
        out = CDSTestUtils.executeAndLog(pb, "asm");
        out.shouldContain("Picked up JDK_AOT_VM_OPTIONS:");
        checkAOTClassLinkingDisabled(out);
    }

    static void checkAOTClassLinkingDisabled(OutputAnalyzer out) {
        out.shouldMatch("aot-linked =[ ]+0,"); // -XX:-AOTClassLinking should take effect
        out.shouldNotMatch("aot-linked =[ ]+[1-9]");
        out.shouldHaveExitValue(0);
    }

    static int testNum = 0;
    static void printTestCase(String s) {
        System.out.println("vvvvvvv TEST CASE " + testNum + ": " + s + ": starts here vvvvvvv");
        testNum++;
    }
}
