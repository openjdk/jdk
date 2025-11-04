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

import static java.util.stream.Collectors.toMap;
import static jdk.jpackage.test.ApplicationLayout.linuxAppImage;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.LinuxHelper;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;


/**
 * Simple Linux specific packaging test. Resulting package should be installed
 * in /usr directory tree.
 */

/*
 * @test
 * @summary jpackage command run installing app in /usr directory tree
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @requires jpackage.test.SQETest == null
 * @requires (os.family == "linux")
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror UsrTreeTest.java
 * @run main/othervm/timeout=720 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=UsrTreeTest
 */
public class UsrTreeTest {

    @Test
    public static void testUsr() {
        test("/usr", true);
    }

    @Test
    public static void testUsrLocal() {
        test("/usr/local", true);
    }

    @Test
    public static void testUsrCustom() {
        test("/usr/foo", false);
    }

    @Test
    public static void testUsrCustom2() {
        test("/usrbuz", false);
    }

    private static void test(String installDir, boolean expectedImageSplit) {
        new PackageTest()
        .forTypes(PackageType.LINUX)
        .configureHelloApp()
        .addInitializer(cmd -> cmd.addArguments("--install-dir", installDir))
        .addBundleDesktopIntegrationVerifier(false)
        .addBundleVerifier(cmd -> {
            final String packageName = LinuxHelper.getPackageName(cmd);
            final Path launcherPath = cmd.appLauncherPath();
            final Path launcherCfgPath = cmd.appLauncherCfgPath(null);
            final Path commonPath = commonPath(launcherPath, launcherCfgPath);

            final boolean actualImageSplit = !commonPath.getFileName().equals(
                    Path.of(packageName));
            TKit.assertTrue(expectedImageSplit == actualImageSplit,
                    String.format(
                            "Check there is%spackage name [%s] in common path [%s] between [%s] and [%s]",
                            expectedImageSplit ? " no " : " ", packageName,
                            commonPath, launcherPath, launcherCfgPath));
        })
        .addInstallVerifier(cmd -> {
            Stream.of(
                    cmd.appLauncherPath(),
                    cmd.appLauncherCfgPath(null),
                    cmd.appLayout().libapplauncher()
            ).map(cmd::pathToPackageFile).map(cmd.appInstallationDirectory()::relativize).forEachOrdered(cmd::assertFileInAppImage);
        })
        .run();
    }

    private static Path commonPath(Path a, Path b) {
        if (a.equals(b)) {
            return a;
        }

        final int minCount = Math.min(a.getNameCount(), b.getNameCount());
        for (int i = minCount; i > 0; i--) {
            Path sp = a.subpath(0, i);
            if (sp.equals(b.subpath(0, i))) {
                return a.getRoot().resolve(sp);
            }
        }

        return a.getRoot();
    }
}
