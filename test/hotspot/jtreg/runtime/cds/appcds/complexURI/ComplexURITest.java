/* Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024 JetBrains s.r.o.
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
 */

/*
 * @test
 * @summary Verifies that CDS works with jar located in directories
 *          with names that need escaping
 * @bug 8339460
 * @requires vm.cds
 * @requires vm.cds.custom.loaders
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile mypackage/Main.java mypackage/Another.java
 * @run main/othervm ComplexURITest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Platform;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class ComplexURITest {
    final static String moduleName = "mymodule";

    public static void main(String[] args) throws Exception {
        System.setProperty("test.noclasspath", "true");
        String jarFile = JarBuilder.build(moduleName, "mypackage/Main", "mypackage/Another");

        Path subDir = Path.of(".", "dir with space");
        Files.createDirectory(subDir);
        Path newJarFilePath = subDir.resolve(moduleName + ".jar");
        Files.move(Path.of(jarFile), newJarFilePath);
        jarFile = newJarFilePath.toString();

        final String listFileName = "test-classlist.txt";
        final String staticArchiveName = "test-static.jsa";
        final String dynamicArchiveName = "test-dynamic.jsa";

        // Verify static archive creation and use
        File fileList = new File(listFileName);
        delete(fileList.toPath());
        File staticArchive = new File(staticArchiveName);
        delete(staticArchive.toPath());

        createClassList(jarFile, listFileName);
        if (!fileList.exists()) {
            throw new RuntimeException("No class list created at " + fileList);
        }

        createArchive(jarFile, listFileName, staticArchiveName);
        if (!staticArchive.exists()) {
            throw new RuntimeException("No shared classes archive created at " + staticArchive);
        }

        useArchive(jarFile, staticArchiveName);

        // Verify dynamic archive creation and use
        File dynamicArchive = new File(dynamicArchiveName);
        delete(dynamicArchive.toPath());

        createDynamicArchive(jarFile, dynamicArchiveName);
        if (!dynamicArchive.exists()) {
            throw new RuntimeException("No dynamic archive created at " + dynamicArchive);
        }

        testDynamicArchive(jarFile, dynamicArchiveName);
    }

    private static void delete(Path path) throws Exception {
        if (Files.exists(path)) {
            if (Platform.isWindows()) {
                Files.setAttribute(path, "dos:readonly", false);
            }
            Files.delete(path);
        }
    }

    private static void createClassList(String jarFile, String list) throws Exception {
        String[] launchArgs  = {
                "-XX:DumpLoadedClassList=" + list,
                "--module-path",
                jarFile,
                "--module",
                moduleName + "/mypackage.Main"};
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(launchArgs);
        OutputAnalyzer output = TestCommon.executeAndLog(pb, "create-list");
        output.shouldHaveExitValue(0);
    }

    private static void createArchive(String jarFile, String list, String archive) throws Exception {
        String[] launchArgs  = {
                "-Xshare:dump",
                "-XX:SharedClassListFile=" + list,
                "-XX:SharedArchiveFile=" + archive,
                "--module-path",
                jarFile,
                "--module",
                moduleName + "/mypackage.Main"};
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(launchArgs);
        OutputAnalyzer output = TestCommon.executeAndLog(pb, "dump-archive");
        output.shouldHaveExitValue(0);
    }

    private static void useArchive(String jarFile, String archive) throws Exception {
        String[] launchArgs  = {
                "-Xshare:on",
                "-XX:SharedArchiveFile=" + archive,
                "--module-path",
                jarFile,
                "--module",
                moduleName + "/mypackage.Main"};
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(launchArgs);
        OutputAnalyzer output = TestCommon.executeAndLog(pb, "use-archive");
        output.shouldHaveExitValue(0);
    }

    private static void createDynamicArchive(String jarFile, String archive) throws Exception {
        String[] launchArgs  = {
                "-XX:ArchiveClassesAtExit=" + archive,
                "--module-path",
                jarFile,
                "--module",
                moduleName + "/mypackage.Main"};
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(launchArgs);
        OutputAnalyzer output = TestCommon.executeAndLog(pb, "dynamic-archive");
        output.shouldHaveExitValue(0);
    }

    private static void testDynamicArchive(String jarFile, String archive) throws Exception {
        String[] launchArgs  = {
                "-XX:SharedArchiveFile=" + archive,
                "-XX:+PrintSharedArchiveAndExit",
                "--module-path",
                jarFile,
                "--module",
                moduleName + "/mypackage.Main"};
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(launchArgs);
        OutputAnalyzer output = TestCommon.executeAndLog(pb, "dynamic-archive");
        output.shouldHaveExitValue(0);
        output.shouldContain("archive is valid");
        output.shouldContain(": mypackage.Main app_loader");
        output.shouldContain(": mypackage.Another unregistered_loader");
    }
}
