/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.Annotations.Parameters;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import static java.util.stream.Collectors.joining;
import java.util.stream.Stream;
import jdk.jpackage.internal.IOUtils;
import jdk.jpackage.test.Functional.ThrowingFunction;
import jdk.jpackage.test.JPackageCommand;


/**
 * Tests generation of packages with additional content in app image.
 */

/*
 * @test
 * @summary jpackage with --app-content option
 * @library ../helpers
 * @library /test/lib
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @build AppContentTest
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @modules java.base/jdk.internal.util
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AppContentTest
 */
public class AppContentTest {

    private static final String TEST_JAVA = "apps/PrintEnv.java";
    private static final String TEST_DUKE = "apps/dukeplug.png";
    private static final String TEST_DIR = "apps";
    private static final String TEST_BAD = "non-existant";

    // On OSX `--app-content` paths will be copied into the "Contents" folder
    // of the output app image.
    // "codesign" imposes restrictions on the directory structure of "Contents" folder.
    // In particular, random files should be placed in "Contents/Resources" folder
    // otherwise "codesign" will fail to sign.
    // Need to prepare arguments for `--app-content` accordingly.
    private final static boolean copyInResources = TKit.isOSX();

    private final List<String> testPathArgs;

    @Parameters
    public static Collection data() {
        return List.of(new String[][]{
            {TEST_JAVA, TEST_DUKE}, // include two files in two options
            {TEST_JAVA, TEST_BAD},  // try to include non-existant content
            {TEST_JAVA + "," + TEST_DUKE, TEST_DIR}, // two files in one option,
                                            // and a dir tree in another option.
        });
    }

    public AppContentTest(String... testPathArgs) {
        this.testPathArgs = List.of(testPathArgs);
    }

    @Test
    public void test() throws Exception {
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
                Path baseDir = getAppContentRoot(cmd);
                for (String arg : testPathArgs) {
                    List<String> paths = Arrays.asList(arg.split(","));
                    for (String p : paths) {
                        Path name = Path.of(p).getFileName();
                        TKit.assertPathExists(baseDir.resolve(name), true);
                    }
                }

            })
            .setExpectedExitCode(expectedJPackageExitCode)
            .run();
    }

    private static Path getAppContentRoot(JPackageCommand cmd) {
        Path contentDir = cmd.appLayout().contentDirectory();
        if (copyInResources) {
            return contentDir.resolve("Resources");
        } else {
            return contentDir;
        }
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
            var appContentArg = TKit.createTempDirectory("app-content").resolve("Resources");
            var srcPath = TKit.TEST_SRC_ROOT.resolve(appContentPath);
            var dstPath = appContentArg.resolve(srcPath.getFileName());
            Files.createDirectories(dstPath.getParent());
            IOUtils.copyRecursive(srcPath, dstPath);
            return appContentArg;
        }

        private static List<Path> initAppContentPaths(List<Path> appContentPaths) {
            if (copyInResources) {
                return appContentPaths.stream().map(appContentPath -> {
                    if (appContentPath.endsWith(TEST_BAD)) {
                        return appContentPath;
                    } else {
                        return ThrowingFunction.toFunction(
                                AppContentInitializer::copyAppContentPath).apply(
                                        appContentPath);
                    }
                }).toList();
            } else {
                return appContentPaths.stream().map(TKit.TEST_SRC_ROOT::resolve).toList();
            }
        }

        private List<String> jpackageArgs;
        private final List<List<Path>> appContentPathGroups;
    }
}
