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
 * @summary substitution of %p/%t in AOTCache/AOTCacheOutput/AOTConfiguration
 * @requires vm.cds
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver FileNameSubstitution
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class FileNameSubstitution {
    static String appJar = ClassFileInstaller.getJarPath("hello.jar");
    static String helloClass = "Hello";

    public static void main(String[] args) throws Exception {
        positiveTests();
        negativeTests();
    }

    static void positiveTests() throws Exception {
        ProcessBuilder pb;
        OutputAnalyzer out;
        String aotCacheFile;
        String aotConfigFile;

        //----------------------------------------------------------------------
        printTestCase("AOTConfiguration (two-command training)");
        removeOutputFiles();
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=test-%p.aotconfig",
            "-Xlog:os",
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldContain("Hello World");
        aotConfigFile = find_pid_substituted_file(out, "test-", ".aotconfig");
        out.shouldContain("AOTConfiguration recorded: " + aotConfigFile);
        out.shouldHaveExitValue(0);

        // "create" with AOTCache
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCache=test-%p.aot",
            "-Xlog:os",
            "-cp", appJar);

        out = CDSTestUtils.executeAndLog(pb, "asm");
        aotCacheFile = find_pid_substituted_file(out, "test-", ".aot");
        out.shouldContain("AOTCache creation is complete: " + aotCacheFile);
        out.shouldHaveExitValue(0);

        // "create" with AOTCacheOutput
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=create",
            "-XX:AOTConfiguration=" + aotConfigFile,
            "-XX:AOTCacheOutput=test-%p.aot",
            "-Xlog:os",
            "-cp", appJar);

        out = CDSTestUtils.executeAndLog(pb, "asm");
        aotCacheFile = find_pid_substituted_file(out, "test-", ".aot");
        out.shouldContain("AOTCache creation is complete: " + aotCacheFile);
        out.shouldHaveExitValue(0);

        //----------------------------------------------------------------------
        printTestCase("AOTConfiguration/AOTCache (single-command training)");
        removeOutputFiles();
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=test-%p.aotconfig",
            "-XX:AOTCacheOutput=test-%p.aot",
            "-Xlog:os",
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldContain("Hello World");
        aotConfigFile = find_pid_substituted_file(out, "test-", ".aotconfig");
        out.shouldContain("AOTConfiguration recorded: " + aotConfigFile);
        aotCacheFile = find_pid_substituted_file(out, "test-", ".aot");
        out.shouldContain("AOTCache creation is complete: " + aotCacheFile);
        out.shouldHaveExitValue(0);


        // The implementation of %t is exactly the same as %p, so just test one case
        //----------------------------------------------------------------------
        printTestCase("AOTConfiguration (two-command training) -- %t");
        removeOutputFiles();
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=test-%t.aotconfig",
            "-Xlog:os",
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldContain("Hello World");
        out.shouldContain("AOTConfiguration recorded: test-20"); // This should work for the nest 70 years or so ...
        out.shouldHaveExitValue(0);
    }

    static void negativeTests() throws Exception {
        ProcessBuilder pb;
        OutputAnalyzer out;

        //----------------------------------------------------------------------
        printTestCase("Cannot use %p twice");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=test-%p%p.aotconfig",
            "-Xlog:os",
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldContain("AOTConfiguration cannot contain more than one %p");
        out.shouldHaveExitValue(1);

        //----------------------------------------------------------------------
        printTestCase("Cannot use %t twice");
        pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-XX:AOTMode=record",
            "-XX:AOTConfiguration=test-%t%t.aotconfig",
            "-Xlog:os",
            "-cp", appJar, helloClass);

        out = CDSTestUtils.executeAndLog(pb, "train");
        out.shouldContain("AOTConfiguration cannot contain more than one %t");
        out.shouldHaveExitValue(1);
    }

    static void removeOutputFiles() {
        removeOutputFiles(".aot");
        removeOutputFiles(".aotconfig");
    }

    static void removeOutputFiles(String suffix) {
        File dir = new File(".");
        for (File f : dir.listFiles()) {
            if (f.getName().endsWith(suffix)) {
                f.delete();
            }
        }
    }

    static String find_pid_substituted_file(OutputAnalyzer out, String prefix, String suffix) {
        String stdout = out.getStdout();
        Pattern pattern = Pattern.compile("Initialized VM with process ID ([0-9]+)");
        Matcher matcher = pattern.matcher(stdout);

        if (!matcher.find()) {
            throw new RuntimeException("Cannot find pid");
        }
        // For single-command training, pid will be from -Xlog of the first process (the training process).
        // %p should not be substituted with the pid of the second process (the assembly process).
        String pid = matcher.group(1);
        String fileName = prefix + "pid" + pid + suffix;
        File file = new File(fileName);
        if (!file.exists()) {
            throw new RuntimeException("Expected file doesn't exist: " + fileName);
        }
        if (!file.isFile()) {
            throw new RuntimeException("Expected to be a file: " + fileName);
        }
        return fileName;
    }

    static int testNum = 0;
    static void printTestCase(String s) {
        System.out.println("vvvvvvv TEST CASE " + testNum + ": " + s + ": starts here vvvvvvv");
        testNum++;
    }
}
