/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary "AOT" aliases for traditional CDS command-line options
 * @requires vm.cds
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver AOTFlags
 */

import java.io.File;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class AOTFlags {
    static String appJar = ClassFileInstaller.getJarPath("hello.jar");
    static String aotConfigFile = "hello.aotconfig";
    static String aotCacheFile = "hello.aot";
    static String helloClass = "Hello";

    public static void main(String[] args) throws Exception {
        positiveTests();
        negativeTests();
    }

    static void positiveTests() throws Exception {
        //----------------------------------------------------------------------
        printTestCase("Training Run");
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-Xlog:cds=debug",
            "-cp", appJar, helloClass);

        OutputAnalyzer out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldContain("Hello World");
        out.shouldContain("AOTConfiguration recorded: " + aotConfigFile);
        out.shouldHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("Assembly Phase (AOTClassLinking unspecified -> should be enabled by default)");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-Xlog:cds",
            "-cp", appJar);
        out = CDSTestUtils.executeAndLog(pb, "asm");
        out.shouldContain("Dumping shared data to file:");
        out.shouldMatch("cds.*hello[.]aot");
        out.shouldHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("Production Run with AOTCache");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "-Xlog:cds",
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "prod");
        out.shouldContain("Using AOT-linked classes: true (static archive: has aot-linked classes)");
        out.shouldContain("Opened AOT cache hello.aot.");
        out.shouldContain("Hello World");
        out.shouldHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("AOTMode=off");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "--show-version",
            "-Xlog:cds",
            "-XX:AOTMode=off",
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "prod");
        out.shouldNotContain(", sharing");
        out.shouldNotContain("Opened AOT cache hello.aot.");
        out.shouldContain("Hello World");
        out.shouldHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("AOTMode=auto");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "--show-version",
            "-Xlog:cds",
            "-XX:AOTMode=auto",
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "prod");
        out.shouldContain(", sharing");
        out.shouldContain("Opened AOT cache hello.aot.");
        out.shouldContain("Hello World");
        out.shouldHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("AOTMode=on");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "--show-version",
            "-Xlog:cds",
            "-XX:AOTMode=on",
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "prod");
        out.shouldContain(", sharing");
        out.shouldContain("Opened AOT cache hello.aot.");
        out.shouldContain("Hello World");
        out.shouldHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("Assembly Phase with -XX:-AOTClassLinking");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-XX:-AOTClassLinking",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-Xlog:cds",
            "-cp", appJar);
        out = CDSTestUtils.executeAndLog(pb, "asm");
        out.shouldContain("Dumping shared data to file:");
        out.shouldMatch("cds.*hello[.]aot");
        out.shouldHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("Production Run with AOTCache, which was created with -XX:-AOTClassLinking");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "-Xlog:cds",
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "prod");
        out.shouldContain("Using AOT-linked classes: false (static archive: no aot-linked classes)");
        out.shouldContain("Opened AOT cache hello.aot.");
        out.shouldContain("Hello World");
        out.shouldHaveExitValue(0);
    }

    static void negativeTests() throws Exception {
       //----------------------------------------------------------------------
        printTestCase("Mixing old and new options");
        String mixOldNewErrSuffix = " cannot be used at the same time with -Xshare:on, -Xshare:auto, "
            + "-Xshare:off, -Xshare:dump, DumpLoadedClassList, SharedClassListFile, "
            + "or SharedArchiveFile";

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Xshare:off",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-cp", appJar, helloClass);

        OutputAnalyzer out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("Option AOTConfiguration" + mixOldNewErrSuffix);
        out.shouldNotHaveExitValue(0);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:SharedArchiveFile=" + aotCacheFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("Option AOTCache" + mixOldNewErrSuffix);
        out.shouldNotHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("Use AOTConfiguration without AOTMode");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("AOTConfiguration can only be used with -XX:AOTMode=record or -XX:AOTMode=create");
        out.shouldNotHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("Use AOTMode without AOTConfiguration");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("-XX:AOTMode=record cannot be used without setting AOTConfiguration");

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("-XX:AOTMode=create cannot be used without setting AOTConfiguration");
        out.shouldNotHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("Bad AOTMode");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=foo",
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("Unrecognized value foo for AOTMode. Must be one of the following: off, record, create, auto, on");
        out.shouldNotHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("AOTCache specified with -XX:AOTMode=record");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("AOTCache must not be specified when using -XX:AOTMode=record");
        out.shouldNotHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("AOTCache not specified with -XX:AOTMode=create");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("AOTCache must be specified when using -XX:AOTMode=create");
        out.shouldNotHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("No such config file");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=no-such-file",
            "-XX:AOTCache=" + aotCacheFile,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("Must be a valid AOT configuration generated by the current JVM: no-such-file");
        out.shouldNotHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("AOTConfiguration file cannot be used as a CDS archive");

        // first make sure we have a valid aotConfigFile
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldHaveExitValue(0);

        // Cannot use this config file as a AOT cache
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=on",
            "-XX:AOTCache=" + aotConfigFile,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("Not a valid AOT cache (hello.aotconfig)");
        out.shouldNotHaveExitValue(0);

        // Cannot use this config file as a CDS archive
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Xshare:on",
            "-XX:SharedArchiveFile=" + aotConfigFile,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("Not a valid archive (hello.aotconfig)");
        out.shouldNotHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("Classpath mismatch when creating archive");

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-cp", "noSuchJar.jar");

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("class path and/or module path are not compatible with the ones " +
                          "specified when the AOTConfiguration file was recorded");
        out.shouldContain("Unable to use create AOT cache");
        out.shouldHaveExitValue(1);

        //----------------------------------------------------------------------
        printTestCase("Cannot use multiple paths in AOTConfiguration");

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=" + aotConfigFile + File.pathSeparator + "dummy",
            "-cp", "noSuchJar.jar");

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("Option AOTConfiguration must specify a single file name");
        out.shouldHaveExitValue(1);

        //----------------------------------------------------------------------
        printTestCase("Cannot use multiple paths in AOTCache");

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile + File.pathSeparator + "dummy",
            "-cp", "noSuchJar.jar");

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("Option AOTCache must specify a single file name");
        out.shouldHaveExitValue(1);

        //----------------------------------------------------------------------
        printTestCase("Cannot use a dynamic CDS archive for -XX:AOTCache");
        String staticArchive = "static.jsa";
        String dynamicArchive = "dynamic.jsa";

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Xshare:dump",
            "-XX:SharedArchiveFile=" + staticArchive);
        out = CDSTestUtils.executeAndLog(pb, "static");
        out.shouldHaveExitValue(0);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:SharedArchiveFile=" + staticArchive,
            "-XX:ArchiveClassesAtExit=" + dynamicArchive,
            "--version");
        out = CDSTestUtils.executeAndLog(pb, "dynamic");
        out.shouldHaveExitValue(0);

        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Xlog:cds",
            "-XX:AOTMode=on",
            "-XX:AOTCache=" + dynamicArchive,
            "--version");

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("Unable to use AOT cache.");
        out.shouldContain("Not a valid AOT cache (dynamic.jsa)");
        out.shouldHaveExitValue(1);

        //----------------------------------------------------------------------
        testEmptyValue("AOTCache");
        testEmptyValue("AOTConfiguration");
        testEmptyValue("AOTMode");
    }

    static void testEmptyValue(String option) throws Exception {
        printTestCase("Empty values for " + option);
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:" + option + "=", "--version");
        OutputAnalyzer out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("Improperly specified VM option '" + option + "='");
        out.shouldHaveExitValue(1);
    }

    static int testNum = 0;
    static void printTestCase(String s) {
        System.out.println("vvvvvvv TEST CASE " + testNum + ": " + s + " starts here vvvvvvv");
        testNum++;
    }
}
