/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.lang.invoke.MethodHandles;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.FileAssociations;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.TKit;

/**
 * Test --add-launcher parameter. Output of the test should be
 * additionallauncherstest*.* installer. The output installer should provide the
 * same functionality as the default installer (see description of the default
 * installer in SimplePackageTest.java) plus install three extra application
 * launchers.
 */

/*
 * @test
 * @summary jpackage with --add-launcher
 * @key jpackagePlatformPackage
 * @library ../helpers
 * @build jdk.jpackage.test.*
 * @modules jdk.incubator.jpackage/jdk.incubator.jpackage.internal
 * @compile AdditionalLaunchersTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=AdditionalLaunchersTest
 */

public class AdditionalLaunchersTest {

    @Test
    public void test() {
        // Configure a bunch of additional launchers and also setup
        // file association to make sure it will be linked only to the main
        // launcher.

        PackageTest packageTest = new PackageTest().configureHelloApp();
        packageTest.addInitializer(cmd -> {
            cmd.addArguments("--arguments", "Duke", "--arguments", "is",
                    "--arguments", "the", "--arguments", "King");
        });

        new FileAssociations(
                MethodHandles.lookup().lookupClass().getSimpleName()).applyTo(
                packageTest);

        new AdditionalLauncher("Baz2").setArguments().applyTo(packageTest);
        new AdditionalLauncher("foo").setArguments("yep!").applyTo(packageTest);

        AdditionalLauncher barLauncher = new AdditionalLauncher("Bar").setArguments(
                "one", "two", "three");
        if (TKit.isLinux()) {
            barLauncher.setIcon(TKit.TEST_SRC_ROOT.resolve("apps/dukeplug.png"));
        }
        barLauncher.applyTo(packageTest);

        packageTest.run();
    }

    private static Path replaceFileName(Path path, String newFileName) {
        String fname = path.getFileName().toString();
        int lastDotIndex = fname.lastIndexOf(".");
        if (lastDotIndex != -1) {
            fname = newFileName + fname.substring(lastDotIndex);
        } else {
            fname = newFileName;
        }
        return path.getParent().resolve(fname);
    }

    static class AdditionalLauncher {

        AdditionalLauncher(String name) {
            this.name = name;
        }

        AdditionalLauncher setArguments(String... args) {
            arguments = List.of(args);
            return this;
        }

        AdditionalLauncher setIcon(Path iconPath) {
            icon = iconPath;
            return this;
        }

        void applyTo(PackageTest test) {
            final Path propsFile = TKit.workDir().resolve(name + ".properties");

            test.addInitializer(cmd -> {
                cmd.addArguments("--add-launcher", String.format("%s=%s", name,
                        propsFile));

                Map<String, String> properties = new HashMap<>();
                if (arguments != null) {
                    properties.put("arguments", String.join(" ",
                            arguments.toArray(String[]::new)));
                }

                if (icon != null) {
                    properties.put("icon", icon.toAbsolutePath().toString());
                }

                TKit.createPropertiesFile(propsFile, properties);
            });
            test.addInstallVerifier(cmd -> {
                Path launcherPath = replaceFileName(cmd.appLauncherPath(), name);

                TKit.assertExecutableFileExists(launcherPath);

                if (cmd.isFakeRuntime(String.format(
                        "Not running %s launcher", launcherPath))) {
                    return;
                }
                HelloApp.executeAndVerifyOutput(launcherPath,
                        Optional.ofNullable(arguments).orElse(List.of()).toArray(
                                String[]::new));
            });
            test.addUninstallVerifier(cmd -> {
                Path launcherPath = replaceFileName(cmd.appLauncherPath(), name);

                TKit.assertPathExists(launcherPath, false);
            });
        }

        private List<String> arguments;
        private Path icon;
        private final String name;
    }
}
