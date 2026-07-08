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

import static jdk.jpackage.test.PackageType.MAC_DMG;
import static jdk.jpackage.test.PackageType.WINDOWS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import jdk.jpackage.test.AdditionalLauncher;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.ConfigurationTarget;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JavaTool;
import jdk.jpackage.test.LauncherAsServiceVerifier;
import jdk.jpackage.test.LauncherVerifier.Action;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.RunnablePackageTest;
import jdk.jpackage.test.TKit;

/**
 * Launcher as service packaging test. Output of the test should be
 * servicetest*.* and updateservicetest*.* package bundles.
 */

/*
 * @test
 * @summary Launcher as service packaging test
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @key jpackagePlatformPackage
 * @requires (jpackage.test.SQETest != null)
 * @compile -Xlint:all -Werror ServiceTest.java
 * @run main/othervm/timeout=2880 -Xmx512m
 *  jdk.jpackage.test.Main
 *  --jpt-run=ServiceTest.test,ServiceTest.testUpdate
 */

/*
 * @test
 * @summary Launcher as service packaging test
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @key jpackagePlatformPackage
 * @requires (jpackage.test.SQETest == null)
 * @compile -Xlint:all -Werror ServiceTest.java
 * @run main/othervm/timeout=2880 -Xmx512m
 *  jdk.jpackage.test.Main
 *  --jpt-run=ServiceTest
 */

public class ServiceTest {

    public ServiceTest() {
        if (TKit.isWindows()) {
            final String propName = "jpackage.test.ServiceTest.service-installer";
            String serviceInstallerExec = System.getProperty(propName);
            if (serviceInstallerExec == null) {
                if (Stream.of(RunnablePackageTest.Action.CREATE,
                        RunnablePackageTest.Action.INSTALL).allMatch(
                                RunnablePackageTest::hasAction)) {
                    TKit.throwSkippedException(String.format(
                            "%s system property not set", propName));
                } else {
                    // Use cmd.exe as a stub as the output packages will not be
                    // created and installed in the test run
                    serviceInstallerExec = Optional.ofNullable(System.getenv(
                            "COMSPEC")).orElseGet(() -> {
                                return JavaTool.JAVA.getPath().toString();
                            });
                    TKit.trace(
                            String.format("Using [%s] as a service installer",
                                    serviceInstallerExec));
                }
            }

            winServiceInstaller = Path.of(serviceInstallerExec);

        } else {
            winServiceInstaller = null;
        }
    }

    @Test
    public void test() {
        var pkg = createPackageTest().addHelloAppInitializer("com.foo.ServiceTest");
        LauncherAsServiceVerifier.build().setExpectedValue("A1").applyTo(pkg);
        createTestInitializer().applyTo(pkg);
        pkg.run();
    }

    @Test
    public void testUpdate() {
        var testInitializer = createTestInitializer().setUpgradeCode(
                "4050AD4D-D6CC-452A-9CB0-58E5FA8C410F");

        // Package name will be used as package ID on macOS. Keep it the same for
        // both packages to allow update installation.
        final String packageName = "com.bar";

        var pkg = createPackageTest()
                .addHelloAppInitializer(String.join(".", packageName, "Hello"))
                .disablePackageUninstaller();
        testInitializer.applyTo(pkg);

        LauncherAsServiceVerifier.build().setExpectedValue("Default").applyTo(pkg);

        var pkg2 = createPackageTest()
                .addHelloAppInitializer(String.join(".", packageName, "Bye"))
                .addInitializer(cmd -> {
                    cmd.addArguments("--app-version", "2.0");
                });
        testInitializer.applyTo(pkg2);

        var builder = LauncherAsServiceVerifier.build()
                .setLauncherName("foo")
                .setAppOutputFileName("foo-launcher-as-service.txt");

        builder.setExpectedValue("Foo").applyTo(pkg);

        builder.setExpectedValue("Foo2").applyTo(pkg2);

        builder.setExpectedValue("Bar")
                .setLauncherName("bar")
                .setAppOutputFileName("bar-launcher-as-service.txt")
                .applyTo(pkg2);

        new PackageTest.Group(pkg, pkg2).run();
    }

    @Test
    @Parameter(value = {"true", "false"})
    @Parameter(value = {"false", "false"})
    @Parameter(value = {"true", "true"}, ifOS = OperatingSystem.MACOS)
    @Parameter(value = {"false", "true"}, ifOS = OperatingSystem.MACOS)
    public void testAddL(boolean mainLauncherAsService, boolean isMacAppStore) {

        final var uniqueOutputFile = uniqueOutputFile();

        createPackageTest()
                .addHelloAppInitializer("com.buz.AddLaunchersServiceTest")
                .addInitializer(cmd -> {
                    if (isMacAppStore) {
                        cmd.addArgument("--mac-app-store");
                    }
                })
                .mutate(test -> {
                    if (mainLauncherAsService) {
                        LauncherAsServiceVerifier.build()
                                .mutate(uniqueOutputFile).appendAppOutputFileNamePrefix("-")
                                .setExpectedValue("Main").applyTo(test);
                    }
                })
                // Regular launcher. The installer should not automatically execute it.
                .mutate(new AdditionalLauncher("notservice")
                        .withoutVerifyActions(Action.EXECUTE_LAUNCHER)
                        .setProperty("launcher-as-service", Boolean.FALSE)
                        .addJavaOptions("-Djpackage.test.noexit=true")::applyTo)
                // Additional launcher with explicit "launcher-as-service=true" property in the property file.
                .mutate(LauncherAsServiceVerifier.build()
                        .mutate(uniqueOutputFile).appendAppOutputFileNamePrefix("-A1-")
                        .setLauncherName("AL1")
                        .setExpectedValue("AL1")::applyTo)
                .mutate(test -> {
                    if (mainLauncherAsService) {
                        // Additional launcher without "launcher-as-service" property in the property file.
                        // Still, should be installed as a service.
                        LauncherAsServiceVerifier.build()
                                .mutate(uniqueOutputFile).appendAppOutputFileNamePrefix("-A2-")
                                .setLauncherName("AL2")
                                .setExpectedValue("AL2")
                                .setAdditionalLauncherCallback(al -> {
                                    al.removeProperty("launcher-as-service");
                                })
                                .applyTo(test);
                    }
                })
                .mutate(createTestInitializer()::applyTo)
                .run();
        }

    @Test
    @Parameter(value = {"true", "false"})
    @Parameter(value = {"false", "false"})
    @Parameter(value = {"true", "true"}, ifOS = OperatingSystem.MACOS)
    @Parameter(value = {"false", "true"}, ifOS = OperatingSystem.MACOS)
    public void testAddLFromAppImage(boolean mainLauncherAsService, boolean isMacAppStore) {

        var uniqueOutputFile = uniqueOutputFile();

        var appImageCmd = new ConfigurationTarget(JPackageCommand.helloAppImage("com.bar.AddLaunchersFromAppImageServiceTest"));

        if (RunnablePackageTest.hasAction(RunnablePackageTest.Action.INSTALL)) {
            // Ensure launchers are executable because the output bundle will be installed
            // and we want to verify launchers are automatically started by the installer.
            appImageCmd.addInitializer(JPackageCommand::ignoreFakeRuntime);
        }

        if (isMacAppStore) {
            appImageCmd.cmd().orElseThrow().addArgument("--mac-app-store");
        }

        if (mainLauncherAsService) {
            LauncherAsServiceVerifier.build()
                    .mutate(uniqueOutputFile).appendAppOutputFileNamePrefix("-")
                    .setExpectedValue("Main")
                    .applyTo(appImageCmd);
            // Can not use "--launcher-as-service" option with app image packaging.
            appImageCmd.cmd().orElseThrow().removeArgument("--launcher-as-service");
        } else {
            appImageCmd.addInitializer(cmd -> {
                // Configure the main launcher to hang at the end of the execution.
                // The main launcher should not be executed in this test.
                // If it is executed, it indicates it was started as a service,
                // which must fail the test. The launcher's hang-up will be the event failing the test.
                cmd.addArguments("--java-options", "-Djpackage.test.noexit=true");
            });
        }

        // Additional launcher with explicit "launcher-as-service=true" property in the property file.
        LauncherAsServiceVerifier.build()
                .mutate(uniqueOutputFile).appendAppOutputFileNamePrefix("-A1-")
                .setLauncherName("AL1")
                .setExpectedValue("AL1").applyTo(appImageCmd);

        // Regular launcher. The installer should not automatically execute it.
        appImageCmd.add(new AdditionalLauncher("notservice")
                .withoutVerifyActions(Action.EXECUTE_LAUNCHER)
                .addJavaOptions("-Djpackage.test.noexit=true"));

        new PackageTest().excludeTypes(MAC_DMG)
                .addRunOnceInitializer(appImageCmd.cmd().orElseThrow()::execute)
                .usePredefinedAppImage(appImageCmd.cmd().orElseThrow())
                .addInitializer(cmd -> {
                    if (mainLauncherAsService) {
                        cmd.addArgument("--launcher-as-service");
                    }
                })
                .mutate(createTestInitializer()::applyTo)
                .run();
    }

    private final class TestInitializer {

        TestInitializer setUpgradeCode(String v) {
            upgradeCode = v;
            return this;
        }

        void applyTo(PackageTest test) {
            if (winServiceInstaller != null) {
                var resourceDir = TKit.createTempDirectory("resource-dir");
                try {
                    Files.copy(winServiceInstaller, resourceDir.resolve("service-installer.exe"));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }

                test.forTypes(WINDOWS, () -> test.addInitializer(cmd -> {
                    cmd.addArguments("--resource-dir", resourceDir);
                }));
            }

            if (upgradeCode != null) {
                test.forTypes(WINDOWS, () -> test.addInitializer(cmd -> {
                    cmd.addArguments("--win-upgrade-uuid", upgradeCode);
                }));
            }
        }

        private String upgradeCode;
    }

    private TestInitializer createTestInitializer() {
        return new TestInitializer();
    }

    private static PackageTest createPackageTest() {
        var test = new PackageTest()
                .excludeTypes(MAC_DMG) // DMG not supported
                .addInitializer(JPackageCommand::setInputToEmptyDirectory);
        if (RunnablePackageTest.hasAction(RunnablePackageTest.Action.INSTALL)) {
            // Ensure launchers are executable because the output bundle will be installed
            // and we want to verify launchers are automatically started by the installer.
            test.addInitializer(JPackageCommand::ignoreFakeRuntime);
        }
        return test;
    }

    private static Consumer<LauncherAsServiceVerifier.Builder> uniqueOutputFile() {
        var prefix = uniquePrefix();
        return builder -> {
            builder.setAppOutputFileNamePrefixToAppName()
                    .appendAppOutputFileNamePrefix("-")
                    .appendAppOutputFileNamePrefix(prefix);
        };
    }

    private static String uniquePrefix() {
        return HexFormat.of().toHexDigits(System.currentTimeMillis());
    }

    private final Path winServiceInstaller;
}
