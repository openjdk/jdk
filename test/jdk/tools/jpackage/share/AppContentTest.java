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

import static jdk.internal.util.OperatingSystem.LINUX;
import static jdk.internal.util.OperatingSystem.MACOS;
import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.function.ThrowingFunction;
import jdk.jpackage.test.JPackageCommand;


/**
 * Tests generation of packages with additional content in app image.
 */

/*
 * @test
 * @summary jpackage with --app-content option
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @build AppContentTest
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AppContentTest
 */
public class AppContentTest {

    private static final String TEST_JAVA = "apps/PrintEnv.java";
    private static final String TEST_DUKE = "apps/dukeplug.png";
    private static final String TEST_DUKE_LINK = "dukeplugLink.txt";
    private static final String TEST_DIR = "apps";
    private static final String TEST_BAD = "non-existant";

    // On OSX `--app-content` paths will be copied into the "Contents" folder
    // of the output app image.
    // "codesign" imposes restrictions on the directory structure of "Contents" folder.
    // In particular, random files should be placed in "Contents/Resources" folder
    // otherwise "codesign" will fail to sign.
    // Need to prepare arguments for `--app-content` accordingly.
    private static final boolean copyInResources = TKit.isOSX();

    private static final String RESOURCES_DIR = "Resources";
    private static final String LINKS_DIR = "Links";

    @Test
    // include two files in two options
    @Parameter({TEST_JAVA, TEST_DUKE})
    // try to include non-existant content
    @Parameter({TEST_JAVA, TEST_BAD})
     // two files in one option and a dir tree in another option.
    @Parameter({TEST_JAVA + "," + TEST_DUKE, TEST_DIR})
     // include one file and one link to the file
    @Parameter(value = {TEST_JAVA, TEST_DUKE_LINK}, ifOS = {MACOS,LINUX})
    public void test(String... args) throws Exception {
        final List<String> testPathArgs = List.of(args);
        final int expectedJPackageExitCode;
        if (testPathArgs.contains(TEST_BAD)) {
            expectedJPackageExitCode = 1;
        } else {
            expectedJPackageExitCode = 0;
        }

        var appContentInitializer = new AppContentInitializer(testPathArgs);

        new PackageTest().configureHelloApp()
            .addRunOnceInitializer(appContentInitializer::initAppContent)
            .addInitializer(appContentInitializer::applyTo)
            .addInstallVerifier(cmd -> {
                for (String arg : testPathArgs) {
                    List<String> paths = Arrays.asList(arg.split(","));
                    for (String p : paths) {
                        Path name = Path.of(p).getFileName();
                        if (isSymlinkPath(name)) {
                            TKit.assertSymbolicLinkExists(getAppContentRoot(cmd)
                                .resolve(LINKS_DIR).resolve(name));
                        } else {
                            TKit.assertPathExists(getAppContentRoot(cmd)
                                .resolve(name), true);
                        }
                    }
                }
            })
            .setExpectedExitCode(expectedJPackageExitCode)
            .run();
    }

    private static Path getAppContentRoot(JPackageCommand cmd) {
        Path contentDir = cmd.appLayout().contentDirectory();
        if (copyInResources) {
            return contentDir.resolve(RESOURCES_DIR);
        } else {
            return contentDir;
        }
    }

    private static boolean isSymlinkPath(Path v) {
        return v.getFileName().toString().contains("Link");
    }

    private static final class AppContentInitializer {
        AppContentInitializer(List<String> appContentArgs) {
            appContentPathGroups = appContentArgs.stream().map(arg -> {
                return Stream.of(arg.split(",")).map(Path::of).toList();
            }).toList();
        }

        void initAppContent() {
            jpackageArgs = appContentPathGroups.stream()
                    .map(AppContentInitializer::initAppContentPaths)
                    .<String>mapMulti((appContentPaths, consumer) -> {
                        consumer.accept("--app-content");
                        consumer.accept(
                        appContentPaths.stream().map(Path::toString).collect(
                                joining(",")));
                    }).toList();
        }

        void applyTo(JPackageCommand cmd) {
            cmd.addArguments(jpackageArgs);
        }

        private static Path copyAppContentPath(Path appContentPath) throws IOException {
            var appContentArg = TKit.createTempDirectory("app-content").resolve(RESOURCES_DIR);
            var srcPath = TKit.TEST_SRC_ROOT.resolve(appContentPath);
            var dstPath = appContentArg.resolve(srcPath.getFileName());
            Files.createDirectories(dstPath.getParent());
            FileUtils.copyRecursive(srcPath, dstPath);
            return appContentArg;
        }

        private static Path createAppContentLink(Path appContentPath) throws IOException {
            var appContentArg = TKit.createTempDirectory("app-content");
            Path dstPath;
            if (copyInResources) {
                appContentArg = appContentArg.resolve(RESOURCES_DIR);
                dstPath = appContentArg.resolve(LINKS_DIR)
                                       .resolve(appContentPath.getFileName());
            } else {
                appContentArg = appContentArg.resolve(LINKS_DIR);
                dstPath = appContentArg.resolve(appContentPath.getFileName());
            }

            Files.createDirectories(dstPath.getParent());

            // Create target file for a link
            String tagetName = dstPath.getFileName().toString().replace("Link", "");
            Path targetPath = dstPath.getParent().resolve(tagetName);
            Files.write(targetPath, "foo".getBytes());
            // Create link
            Files.createSymbolicLink(dstPath, targetPath.getFileName());

            return appContentArg;
        }

        private static List<Path> initAppContentPaths(List<Path> appContentPaths) {
            return appContentPaths.stream().map(appContentPath -> {
                if (appContentPath.endsWith(TEST_BAD)) {
                    return appContentPath;
                } else if (isSymlinkPath(appContentPath)) {
                    return ThrowingFunction.toFunction(
                            AppContentInitializer::createAppContentLink).apply(
                                    appContentPath);
                } else if (copyInResources) {
                    return ThrowingFunction.toFunction(
                            AppContentInitializer::copyAppContentPath).apply(
                                    appContentPath);
                } else {
                    return TKit.TEST_SRC_ROOT.resolve(appContentPath);
                }
            }).toList();
        }

        private List<String> jpackageArgs;
        private final List<List<Path>> appContentPathGroups;
    }
}
