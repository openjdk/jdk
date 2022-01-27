/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @summary test dynamic dump meanwhile output loaded class list
 * @bug 8279009 8275084
 * @requires vm.cds
 * @requires vm.cds.custom.loaders
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/Hello.java ClassSpecializerTestApp.java ClassListWithCustomClassNoSource.java
 * @run main/othervm TestDumpClassListSource
 */

/* Test two senarios:
 *   1. ClassSpecializerTestApp.java:
 *      Test case for bug 8275084, make sure the filtering of source class to
 *      dumped class list.
 *   2. ClassListWithCustomClassNoSource: test custom class loader
 *      2.1 class loaded without source.
 *      2.2 class loaded with ProtectionDomain set as same as main class.
 *      2.3 class loaded by custom loader from shared space.
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.cds.CDSTestUtils;

public class TestDumpClassListSource {
    private static final boolean EXPECT_MATCH = true;
    private static final boolean EXPECT_NOMATCH  = !EXPECT_MATCH;

    private static void checkMatch(String file, String regexp, boolean expectMatch, String exceptionMessage) throws Exception {
        String listData = new String(Files.readAllBytes(Paths.get(file)));
        Pattern pattern = Pattern.compile(regexp, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(listData);
        boolean found   = matcher.find();
        if (expectMatch) {
            if (!found) {
                throw new RuntimeException(exceptionMessage);
            }
        } else {
            if (found) {
                throw new RuntimeException(exceptionMessage);
            }
        }
    }

    static final String mainInvokeClass = "ClassSpecializerTestApp";
    static final String mainCutomClass  = "ClassListWithCustomClassNoSource";
    static final String sourceTarget    = "_ClassSpecializer_generateConcreteSpeciesCode";

    private static void checkFileExistence(String type, File file) throws Exception {
        if (!file.exists()) {
            throw new RuntimeException(type + " file " + file.getName() + " should be created");
        }
    }

    public static void main(String[] args) throws Exception {
        String listFileName = "test-classlist.list";
        String archiveName  = "test-dynamic.jsa";
        String jarFile = JarBuilder.build("test-hello", "ClassSpecializerTestApp", "ClassListWithCustomClassNoSource",
                                          "ClassListWithCustomClassNoSource$CL", "Hello");
        // 1. Invoke lambda
        File fileList = new File(listFileName);
        if (fileList.exists()) {
            fileList.delete();
        }
        File fileArchive = new File(archiveName);
        if (fileArchive.exists()) {
            fileArchive.delete();
        }
        String[] launchArgs  = {
                "-Xshare:auto",
                "-XX:DumpLoadedClassList=" + listFileName,
                "-XX:ArchiveClassesAtExit=" + archiveName,
                "-Xlog:cds",
                "-Xlog:cds+lambda",
                "-cp",
                jarFile,
                mainInvokeClass};
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(launchArgs);
        OutputAnalyzer output = TestCommon.executeAndLog(pb, "invoke-class");

        checkFileExistence("Archive", fileArchive);
        checkFileExistence("ClassList", fileList);

        output.shouldHaveExitValue(0);
        checkMatch(listFileName, sourceTarget, EXPECT_NOMATCH, "Failed to filter " + sourceTarget + " in class list file");

        fileArchive.delete();
        fileList.delete();

        // 2. Custom loaded class
        //    2.1 test in memory class generation without source
        launchArgs  = new String[] {
                "-Xshare:auto",
                "-XX:DumpLoadedClassList=" + listFileName,
                "-XX:ArchiveClassesAtExit=" + archiveName,
                "-Xlog:cds",
                "-Xlog:cds+lambda",
                "-Xlog:class+path=info",
                 "-cp",
                jarFile,
                mainCutomClass,
                "1"};
        pb = ProcessTools.createJavaProcessBuilder(launchArgs);
        output = TestCommon.executeAndLog(pb, "custom-nosource");

        checkFileExistence("Archive", fileArchive);
        checkFileExistence("ClassList", fileList);

        output.shouldHaveExitValue(0);
        checkMatch(listFileName, sourceTarget, EXPECT_NOMATCH, "Failed to filter " + sourceTarget + " in class list file");
        checkMatch(listFileName, "Hello", EXPECT_NOMATCH, "Hello should not be logged in class list file");

        fileArchive.delete();
        fileList.delete();

        //    2.2 test in memory class with ProtectionDomain as main class.
        //    "Hello" will be printed in list file and its source set as main class.
        launchArgs  = new String[] {
                "-Xshare:auto",
                "-XX:DumpLoadedClassList=" + listFileName,
                "-XX:ArchiveClassesAtExit=" + archiveName,
                "-Xlog:cds",
                "-Xlog:cds+lambda",
                "-Xlog:class+path=info",
                 "-cp",
                jarFile,
                mainCutomClass,
                "2"};
        pb = ProcessTools.createJavaProcessBuilder(launchArgs);
        output = TestCommon.executeAndLog(pb, "custom-nosource");

        checkFileExistence("Archive", fileArchive);
        checkFileExistence("ClassList", fileList);

        output.shouldHaveExitValue(0);
        checkMatch(listFileName, sourceTarget, EXPECT_NOMATCH, "Failed to filter " + sourceTarget + " in class list file");
        checkMatch(listFileName, "Hello", EXPECT_MATCH, "Hello should be logged in class list file");

        fileArchive.delete();
        fileList.delete();

        //    2.3 class loaded by custom loader from shared space.
        //      2.3.1 dump class list
        launchArgs = new String[] {
                "-XX:DumpLoadedClassList=" + listFileName,
                 "-cp",
                jarFile,
                mainCutomClass,
                "3"};
        pb = ProcessTools.createJavaProcessBuilder(launchArgs);
        output = TestCommon.executeAndLog(pb, "custom-dump-classlist");

        checkFileExistence("ClassList", fileList);

        checkMatch(listFileName, "Hello id: [0-9]+ super: [0-9]+ source: .*/test-hello.jar", EXPECT_MATCH,
                   "Class Hello should be printed in classlist");
        //      2.3.2 dump shared archive based on listFileName
        String archive = "test-hello.jsa";
        File archiveFile = new File(archive);
        if (archiveFile.exists()) {
            archiveFile.delete();
        }
        launchArgs = new String[] {
                "-Xshare:dump",
                "-XX:SharedClassListFile=" + listFileName,
                "-XX:SharedArchiveFile=" + archive,
                 "-cp",
                jarFile,
                mainCutomClass,
                "3"};
        pb = ProcessTools.createJavaProcessBuilder(launchArgs);
        output = TestCommon.executeAndLog(pb, "custom-dump");

        checkFileExistence("Archive", archiveFile);

        //       2.3.3 run with the shared archive and -XX:DumpLoadedClassList
        //             Hello should not be printed out in class list file.
        String classList = "new-test-list.list";
        File newFile = new File(classList);
        if (newFile.exists()) {
            newFile.delete();
        }
        launchArgs = new String[] {
                "-Xshare:on",
                "-XX:SharedArchiveFile=" + archive,
                "-XX:DumpLoadedClassList=" + classList,
                 "-cp",
                jarFile,
                mainCutomClass,
                "3"};
        pb = ProcessTools.createJavaProcessBuilder(launchArgs);
        output = TestCommon.executeAndLog(pb, "custom-share");

        checkFileExistence("ClassList", newFile);
        checkMatch(classList, "Hello id: ?", EXPECT_NOMATCH, "Failed to filter custom loaded class Hello from class list");
    }
}
