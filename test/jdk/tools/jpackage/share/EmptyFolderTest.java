/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;

/**
 * Tests generation of packages and app image with input folder containing empty folders.
 */

/*
 * @test
 * @summary jpackage for package with input containing empty folders
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @build EmptyFolderTest
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=EmptyFolderTest.testPackage
 */

/*
 * @test
 * @summary jpackage for app image with input containing empty folders
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @build EmptyFolderTest
 * @run main/othervm -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=EmptyFolderTest.testAppImage
 */

public class EmptyFolderTest {

    @Test
    public static void testPackage() {
        new PackageTest()
                .configureHelloApp()
                .addInitializer(EmptyFolderTest::createDirTree)
                .addInitializer(cmd -> {
                    cmd.setArgumentValue("--name", "EmptyFolderPackageTest");
                })
                .addInstallVerifier(EmptyFolderTest::validateDirTree)
                .run();
    }

    @Test
    public static void testAppImage() throws IOException {
        var cmd = JPackageCommand.helloAppImage();

        // Add more files into input folder
        createDirTree(cmd);

        // Create app image
        cmd.executeAndAssertHelloAppImageCreated();

        // Verify directory structure
        validateDirTree(cmd);
    }

    private static void createDirTree(JPackageCommand cmd) throws IOException {
        var baseDir = cmd.inputDir();
        for (var path : DIR_STRUCT) {
            path = baseDir.resolve(path);
            if (isFile(path)) {
                Files.createDirectories(path.getParent());
                Files.write(path, new byte[0]);
            } else {
                Files.createDirectories(path);
            }
        }
    }

    private static void validateDirTree(JPackageCommand cmd) {
        // When MSI package is unpacked and not installed, empty directories are not created.
        final boolean emptyDirSupported = !(PackageType.WINDOWS.contains(cmd.packageType()) && cmd.isPackageUnpacked());
        validateDirTree(cmd, emptyDirSupported);
    }

    private static void validateDirTree(JPackageCommand cmd, boolean emptyDirSupported) {
        var outputBaseDir = cmd.appLayout().appDirectory();
        var inputBaseDir = cmd.inputDir();
        for (var path : DIR_STRUCT) {
            Path outputPath = outputBaseDir.resolve(path);
            if (isFile(outputPath)) {
                TKit.assertFileExists(outputPath);
            } else if (emptyDirSupported) {
                TKit.assertDirectoryExists(outputPath);
            } else if (inputBaseDir.resolve(path).toFile().list().length == 0) {
                TKit.assertPathExists(outputPath, false);
            } else {
                TKit.assertDirectoryNotEmpty(outputPath);
            }
        }
    }

    private static boolean isFile(Path path) {
        return path.getFileName().toString().endsWith(".txt");
    }

    // Note: To specify file use ".txt" extension.
    private static final Path [] DIR_STRUCT = {
        Path.of("folder-empty"),
        Path.of("folder-not-empty"),
        Path.of("folder-not-empty", "folder-empty"),
        Path.of("folder-not-empty", "another-folder-empty"),
        Path.of("folder-not-empty", "folder-non-empty2", "file.txt")
    };
}
