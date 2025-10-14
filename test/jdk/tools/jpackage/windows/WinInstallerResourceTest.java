/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.test.WinExecutableIconVerifier.verifyExecutablesHaveSameIcon;

import java.nio.file.Files;
import java.nio.file.Path;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.RunnablePackageTest.Action;
import jdk.jpackage.test.TKit;

/**
 * Test for installer exe from the resource directory.
 */

/*
 * @test
 * @summary jpackage with installer exe from the resource directory
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror WinInstallerResourceTest.java
 * @requires (os.family == "windows")
 * @run main/othervm/timeout=720 -Xmx512m  jdk.jpackage.test.Main
 *  --jpt-run=WinInstallerResourceTest
 */
public class WinInstallerResourceTest {

    @Test
    public void test() {
        createPackageTest("dummy")
        .addInitializer(JPackageCommand::setFakeRuntime)
        .addInitializer(cmd -> {
            // Create exe installer using the default installer exe resource and a custom icon.
            cmd.addArguments("--icon", iconPath("icon"));
        })
        .addBundleVerifier(cmd -> {
            final var exeInstaller = cmd.outputBundle();

            createPackageTest("InstallerResTest")
            .addInitializer(cmd2 -> {
                cmd2.setArgumentValue("--runtime-image", cmd.getArgumentValue("--runtime-image"));
            })
            .addInitializer(cmd2 -> {
                //
                // Create an exe installer using the exe installer created in the first jpackage run.
                //

                // The exe installer created in the first jpackage run has a custom icon.
                // Configure the second jpackage run to use the default icon.
                // This will prevent jpackage from editing icon in the exe installer.
                // Copy the exe installer created in the first jpackage run into the
                // resource directory for the second jpackage run.
                // If jpackage will pick an exe installer resource from the resource directory,
                // the output exe installer should have the same icon as
                // the exe installer produced in the first jpackage run.
                final var resourceDir = TKit.createTempDirectory("resources");
                Files.copy(exeInstaller, resourceDir.resolve("installer.exe"));
                cmd2.addArguments("--resource-dir", resourceDir);
            })
            .addBundleVerifier(cmd2 -> {
                verifyExecutablesHaveSameIcon(exeInstaller, cmd2.outputBundle());
            }).run(Action.CREATE);
        }).run(Action.CREATE);
    }

    private PackageTest createPackageTest(String name) {
        return new PackageTest()
                .ignoreBundleOutputDir()
                .forTypes(PackageType.WIN_EXE)
                .configureHelloApp()
                .addInitializer(cmd -> cmd.setArgumentValue("--name", name));
    }

    private static Path iconPath(String name) {
        return TKit.TEST_SRC_ROOT.resolve(Path.of("resources", name
                + TKit.ICON_SUFFIX));
    }
}
