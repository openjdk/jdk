/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageType;
import static jdk.jpackage.test.RunnablePackageTest.Action.CREATE;
import jdk.jpackage.test.TKit;

/**
 * Test that --icon also changes icon of exe installer.
 */

/*
 * @test
 * @summary jpackage with --icon parameter for exe installer
 * @library ../helpers
 * @key jpackagePlatformPackage
 * @build jdk.jpackage.test.*
 * @build WinInstallerIconTest
 * @requires (os.family == "windows")
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm/timeout=360 -Xmx512m  jdk.jpackage.test.Main
 *  --jpt-run=WinInstallerIconTest
 */

public class WinInstallerIconTest {

    @Test
    public void test() {
        Path customIcon = iconPath("icon");

        // Create installer with the default icon
        var size1 = createInstaller(null, "WithDefaultIcon");

        // Create installer with custom icon.
        var size2 = createInstaller(customIcon, "WithCustomIcon");

        // Create another installer with custom icon.
        var size3 = createInstaller(customIcon, null);

        if (Stream.of(size1, size2, size3).allMatch(Optional::<Long>isEmpty)) {
            TKit.trace(
                    "Not verifying sizes of installers because they were not created");
            return;
        }

        TKit.assertTrue(size2.get() < size1.get(), "Check installer 2 built with custom icon " +
                "is smaller than Installer 1 built with default icon");

        TKit.assertTrue(size3.get() < size1.get(), "Check installer 3 built with custom icon " +
                "is smaller than Installer 1 built with default icon");

    }

    private Optional<Long> createInstaller(Path icon, String nameSuffix) {

        PackageTest test = new PackageTest()
                .forTypes(PackageType.WIN_EXE)
                .addInitializer(JPackageCommand::setFakeRuntime)
                .configureHelloApp();
        if (icon != null) {
            test.addInitializer(cmd -> cmd.addArguments("--icon", icon));
        }

        if (nameSuffix != null) {
            test.addInitializer(cmd -> {
                String name = cmd.name() + nameSuffix;
                cmd.setArgumentValue("--name", name);
                // Create installer bundle in the test work directory, ignore
                // value of jpackage.test.output system property.
                cmd.setDefaultInputOutput();
            });
        }

        Long installerExeByteCount[] = new Long[1];

        test.addBundleVerifier(cmd -> {
            Path installerExePath = cmd.outputBundle();
            installerExeByteCount[0] = installerExePath.toFile().length();
            TKit.trace(String.format("Size of [%s] is %d bytes",
                    installerExePath, installerExeByteCount[0]));
        });

        test.run(CREATE);

        return Optional.ofNullable(installerExeByteCount[0]);
    }

    private static Path iconPath(String name) {
        return TKit.TEST_SRC_ROOT.resolve(Path.of("resources", name
                + TKit.ICON_SUFFIX));
    }
}
