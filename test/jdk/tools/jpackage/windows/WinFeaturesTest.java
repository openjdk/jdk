/*
 * Copyright (c) 2022, Red Hat Inc. and/or its affiliates. All rights reserved.
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

import jdk.jpackage.internal.ApplicationLayout;
import jdk.jpackage.test.FileAssociations;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static jdk.jpackage.test.WindowsHelper.filesFeatureInstalled;

/*
 * @test
 * @summary check that features can be installed separately
 * @library ../helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @requires (os.family == "windows")
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @compile WinFeaturesTest.java
 * @run main/othervm/timeout=540 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=WinFeaturesTest
 */

public class WinFeaturesTest {
    @Test
    @Parameter("DefaultFeature_Files")
    @Parameter("DefaultFeature_FileAssociations")
    @Parameter("DefaultFeature_Shortcuts")
    public static void test(String feature) {
        PackageTest packageTest = new PackageTest()
                .forTypes(PackageType.WINDOWS)
                .configureHelloApp()
                .addInitializer(cmd -> {
                    cmd.addArgument("--win-menu");
                    cmd.addArgument("--win-shortcut");
                    cmd.addInstallArguments(String.format("ADDLOCAL=%s", feature));
                })
                .addInstallVerifier(cmd -> {
                    ApplicationLayout appLayout = cmd.appLayout();
                    String launcherName = String.format("%s.exe", WinFeaturesTest.class.getName());
                    Path launcherPath = appLayout.launchersDirectory().resolve(launcherName);
                    if (filesFeatureInstalled(cmd)) {
                        TKit.assertFileExists(launcherPath);
                    } else if (!cmd.isPackageUnpacked()) { // ADDLOCAL is not supported with "msiexec /a"
                        TKit.assertFalse(Files.exists(launcherPath), launcherPath.toString());
                    }
                });

        Path icon = TKit.TEST_SRC_ROOT.resolve(Path.of("resources", "icon"
                + TKit.ICON_SUFFIX));
        icon = TKit.createRelativePathCopy(icon);
        new FileAssociations("jptest1")
                .setFilename("fa1")
                .setIcon(icon)
                .applyTo(packageTest);

        packageTest.run();
    }
}
