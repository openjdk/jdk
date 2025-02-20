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
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver AOTFlags
 */

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
        // (1) Training Run
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-cp", appJar, helloClass);

        OutputAnalyzer out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldContain("Hello World");
        out.shouldHaveExitValue(0);

        // (2) Assembly Phase (AOTClassLinking unspecified -> should be enabled by default)
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

        // (3) Production Run with AOTCache
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "-Xlog:cds",
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "prod");
        out.shouldContain("Using AOT-linked classes: true (static archive: has aot-linked classes)");
        out.shouldContain("Opened archive hello.aot.");
        out.shouldContain("Hello World");
        out.shouldHaveExitValue(0);

        // (4) AOTMode=off
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "--show-version",
            "-Xlog:cds",
            "-XX:AOTMode=off",
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "prod");
        out.shouldNotContain(", sharing");
        out.shouldNotContain("Opened archive hello.aot.");
        out.shouldContain("Hello World");
        out.shouldHaveExitValue(0);

        // (5) AOTMode=auto
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "--show-version",
            "-Xlog:cds",
            "-XX:AOTMode=auto",
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "prod");
        out.shouldContain(", sharing");
        out.shouldContain("Opened archive hello.aot.");
        out.shouldContain("Hello World");
        out.shouldHaveExitValue(0);

        // (6) AOTMode=on
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "--show-version",
            "-Xlog:cds",
            "-XX:AOTMode=on",
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "prod");
        out.shouldContain(", sharing");
        out.shouldContain("Opened archive hello.aot.");
        out.shouldContain("Hello World");
        out.shouldHaveExitValue(0);

        // (7) Assembly Phase with -XX:-AOTClassLinking
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

        // (8) Production Run with AOTCache, which was created with -XX:-AOTClassLinking
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTCache=" + aotCacheFile,
            "-Xlog:cds",
            "-cp", appJar, helloClass);
        out = CDSTestUtils.executeAndLog(pb, "prod");
        out.shouldContain("Using AOT-linked classes: false (static archive: no aot-linked classes)");
        out.shouldContain("Opened archive hello.aot.");
        out.shouldContain("Hello World");
        out.shouldHaveExitValue(0);
    }

    static void negativeTests() throws Exception {
        // (1) Mixing old and new options
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

        // (2) Use AOTConfiguration without AOTMode
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("AOTConfiguration can only be used with -XX:AOTMode=record or -XX:AOTMode=create");
        out.shouldNotHaveExitValue(0);

        // (3) Use AOTMode without AOTConfiguration
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

        // (4) Bad AOTMode
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=foo",
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("Unrecognized value foo for AOTMode. Must be one of the following: off, record, create, auto, on");
        out.shouldNotHaveExitValue(0);

        // (5) AOTCache specified with -XX:AOTMode=record
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=" + aotCacheFile,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("AOTCache must not be specified when using -XX:AOTMode=record");
        out.shouldNotHaveExitValue(0);

        // (5) AOTCache not specified with -XX:AOTMode=create
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "neg");
        out.shouldContain("AOTCache must be specified when using -XX:AOTMode=create");
        out.shouldNotHaveExitValue(0);

    }
}
