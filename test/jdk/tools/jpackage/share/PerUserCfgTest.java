/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.test.AdditionalLauncher;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.LinuxHelper;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.TKit;

/**
 * Test per-user configuration of app launchers created by jpackage.
 */

/*
 * @test
 * @summary pre-user configuration of app launchers
 * @library /test/jdk/tools/jpackage/helpers
 * @key jpackagePlatformPackage
 * @requires jpackage.test.SQETest == null
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror PerUserCfgTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=PerUserCfgTest
 */
public class PerUserCfgTest {

    @Test
    public static void test() throws IOException {
        // Create a number of .cfg files with different startup args
        JPackageCommand cfgCmd = JPackageCommand.helloAppImage().setFakeRuntime()
                .setArgumentValue("--dest", TKit.createTempDirectory("cfg-files").toString());

        addLauncher(cfgCmd, "a");
        addLauncher(cfgCmd, "b");

        cfgCmd.execute();

        new PackageTest().configureHelloApp().addInstallVerifier(cmd -> {
            if (cmd.isPackageUnpacked("Not running per-user configuration tests")) {
                return;
            }

            Path launcherPath = cmd.appLauncherPath();
            if (!cmd.canRunLauncher(String.format(
                    "Not running %s launcher and per-user configuration tests",
                    launcherPath))) {
                return;
            }

            final PackageType type = cmd.packageType();
            if (PackageType.MAC.contains(type)) {
                withConfigFile(cmd, cfgCmd.appLauncherCfgPath("a"),
                        getUserHomeDir().resolve("Library/Application Support").resolve(
                                cmd.name()), theCmd -> {
                    runMainLauncher(cmd, "a");
                });
            } else if (PackageType.LINUX.contains(type)) {
                final String pkgName = LinuxHelper.getPackageName(cmd);
                final Path homeDir = getUserHomeDir();

                withConfigFile(cmd, cfgCmd.appLauncherCfgPath("a"),
                        homeDir.resolve(".local").resolve(pkgName), theCmd -> {
                    runMainLauncher(cmd, "a");
                });

                withConfigFile(cmd, cfgCmd.appLauncherCfgPath("b"),
                        homeDir.resolve("." + pkgName), theCmd -> {
                    runMainLauncher(cmd, "b");
                });

                withConfigFile(cmd, cfgCmd.appLauncherCfgPath("b"),
                        homeDir.resolve("." + pkgName), theCmd -> {
                    runMainLauncher(cmd, "b");

                    withConfigFile(cmd, cfgCmd.appLauncherCfgPath("a"),
                            homeDir.resolve(".local").resolve(pkgName),
                            theCmd2 -> {
                                runMainLauncher(cmd, "a");
                            });
                });
            } else if (PackageType.WINDOWS.contains(type)) {
                final Path appData = getDirFromEnvVariable("APPDATA");
                final Path localAppData = getDirFromEnvVariable("LOCALAPPDATA");

                if (appData == null || localAppData == null) {
                    TKit.trace(String.format(
                            "Not running per-user configuration tests because some of the environment varibles are not set. "
                                    + "Run jtreg with -e:APPDATA,LOCALAPPDATA option to fix the problem"));
                } else {
                    withConfigFile(cmd, cfgCmd.appLauncherCfgPath("a"),
                            appData.resolve(cmd.name()), theCmd -> {
                        runMainLauncher(cmd, "a");
                    });

                    withConfigFile(cmd, cfgCmd.appLauncherCfgPath("b"),
                            localAppData.resolve(cmd.name()), theCmd -> {
                        runMainLauncher(cmd, "b");
                    });

                    withConfigFile(cmd, cfgCmd.appLauncherCfgPath("b"),
                            appData.resolve(cmd.name()), theCmd -> {
                        runMainLauncher(cmd, "b");

                        withConfigFile(cmd, cfgCmd.appLauncherCfgPath("a"),
                                localAppData.resolve(cmd.name()),
                                theCmd2 -> {
                                    runMainLauncher(cmd, "a");
                                });
                    });
                }
            }
            runMainLauncher(cmd);
        }).run();
    }

    private static void addLauncher(JPackageCommand cmd, String name) {
        new AdditionalLauncher(name) {
            @Override
            protected void verify(JPackageCommand cmd) {}
        }.setDefaultArguments(name).applyTo(cmd);
    }

    private static Path getUserHomeDir() {
        return getDirFromEnvVariable("HOME");
    }

    private static Path getDirFromEnvVariable(String envVariableName) {
        return Optional.ofNullable(System.getenv(envVariableName)).map(Path::of).orElse(
                null);
    }

    private static void withConfigFile(JPackageCommand cmd, Path srcCfgFile,
            Path outputCfgFileDir, ThrowingConsumer<JPackageCommand> action) throws
            Throwable {
        Path targetCfgFile = outputCfgFileDir.resolve(cmd.appLauncherCfgPath(
                null).getFileName());
        TKit.assertPathExists(targetCfgFile, false);
        try (var dirCleaner = TKit.createDirectories(targetCfgFile.getParent())) {
            // Suppress "warning: [try] auto-closeable resource dirCleaner is never referenced"
            Objects.requireNonNull(dirCleaner);
            Files.copy(srcCfgFile, targetCfgFile);
            try {
                TKit.traceFileContents(targetCfgFile, "cfg file");
                action.accept(cmd);
            } finally {
                Files.deleteIfExists(targetCfgFile);
            }
        }
    }

    private static void runMainLauncher(JPackageCommand cmd,
            String... expectedArgs) {

        HelloApp.assertApp(cmd.appLauncherPath()).addDefaultArguments(List.of(
                expectedArgs)).executeAndVerifyOutput();
    }
}
