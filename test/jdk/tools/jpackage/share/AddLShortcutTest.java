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

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.test.AdditionalLauncher;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.FileAssociations;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.LauncherShortcut;
import jdk.jpackage.test.LauncherShortcut.InvokeShortcutSpec;
import jdk.jpackage.test.LauncherShortcut.StartupDirectory;
import jdk.jpackage.test.LauncherVerifier.Action;
import jdk.jpackage.test.LinuxHelper;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.RunnablePackageTest;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.WinShortcutVerifier;

/**
 * Test --add-launcher parameter with shortcuts (platform permitting).
 * Output of the test should be AddLShortcutTest*.* installer.
 * The output installer should provide the same functionality as the
 * default installer (see description of the default installer in
 * SimplePackageTest.java) plus install extra application launchers with and
 * without various shortcuts to be tested manually.
 */

/*
 * @test
 * @summary jpackage with --add-launcher
 * @key jpackagePlatformPackage
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @requires (jpackage.test.SQETest != null)
 * @compile -Xlint:all -Werror AddLShortcutTest.java
 * @run main/othervm/timeout=540 -Xmx512m
 *  jdk.jpackage.test.Main
 *  --jpt-run=AddLShortcutTest.test
 */

/*
 * @test
 * @summary jpackage with --add-launcher
 * @key jpackagePlatformPackage
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @requires (jpackage.test.SQETest == null)
 * @compile -Xlint:all -Werror AddLShortcutTest.java
 * @run main/othervm/timeout=1080 -Xmx512m
 *  jdk.jpackage.test.Main
 *  --jpt-run=AddLShortcutTest
 */

public class AddLShortcutTest {

    @Test
    public void test() {
        // Configure several additional launchers with each combination of
        // possible shortcut hints in add-launcher property file.
        // default is true so Foo (no property), and Bar (properties set to "true")
        // will have shortcuts while other launchers with some properties set
        // to "false" will have none.

        PackageTest packageTest = new PackageTest().configureHelloApp();
        packageTest.addInitializer(cmd -> {
            cmd.addArguments("--arguments", "Duke", "--arguments", "is",
                    "--arguments", "the", "--arguments", "King");
            if (TKit.isWindows()) {
                cmd.addArguments("--win-shortcut", "--win-menu");
            } else if (TKit.isLinux()) {
                cmd.addArguments("--linux-shortcut");
            }
        });

        new FileAssociations(
                MethodHandles.lookup().lookupClass().getSimpleName()).applyTo(
                packageTest);

        new AdditionalLauncher("Foo")
                .setDefaultArguments("yep!")
                .setIcon(GOLDEN_ICON)
                .applyTo(packageTest);

        new AdditionalLauncher("Bar")
                .setDefaultArguments("one", "two", "three")
                .setIcon(GOLDEN_ICON)
                .setShortcuts(true, true)
                .applyTo(packageTest);

        new AdditionalLauncher("Launcher3")
                .setDefaultArguments()
                .setIcon(GOLDEN_ICON)
                .setShortcuts(false, false)
                .applyTo(packageTest);

        new AdditionalLauncher("Launcher4")
                .setDefaultArguments()
                .setIcon(GOLDEN_ICON)
                .setShortcuts(true, false)
                .applyTo(packageTest);

        new AdditionalLauncher("Launcher5")
                .setDefaultArguments()
                .setIcon(GOLDEN_ICON)
                .setShortcuts(false, true)
                .applyTo(packageTest);

        packageTest.run();
    }

    @Test(ifNotOS = OperatingSystem.MACOS)
    @ParameterSupplier(ifOS = OperatingSystem.LINUX, value = "testShortcutStartupDirectoryLinux")
    @ParameterSupplier(ifOS = OperatingSystem.WINDOWS, value = "testShortcutStartupDirectoryWindows")
    public void testStartupDirectory(LauncherShortcutStartupDirectoryConfig... cfgs) {

        var test = new PackageTest().addInitializer(cmd -> {
            cmd.setArgumentValue("--name", "AddLShortcutDirTest");
        }).addInitializer(JPackageCommand::setFakeRuntime).addHelloAppInitializer(null);

        test.addInitializer(cfgs[0]::applyToMainLauncher);
        for (var i = 1; i != cfgs.length; ++i) {
            var al = new AdditionalLauncher("launcher-" + i);
            cfgs[i].applyToAdditionalLauncher(al);
            al.withoutVerifyActions(Action.EXECUTE_LAUNCHER).applyTo(test);
        }

        test.run(RunnablePackageTest.Action.CREATE_AND_UNPACK);
    }

    @Test(ifNotOS = OperatingSystem.MACOS)
    @ParameterSupplier(ifOS = OperatingSystem.LINUX, value = "testShortcutStartupDirectoryLinux")
    @ParameterSupplier(ifOS = OperatingSystem.WINDOWS, value = "testShortcutStartupDirectoryWindows")
    public void testStartupDirectory2(LauncherShortcutStartupDirectoryConfig... cfgs) {

        //
        // Launcher shortcuts in the predefined app image.
        //
        // Shortcut configuration for the main launcher is not supported when building an app image.
        // However, shortcut configuration for additional launchers is supported.
        // The test configures shortcuts for additional launchers in the app image building jpackage command
        // and applies shortcut configuration to the main launcher in the native packaging jpackage command.
        //

        Path[] predefinedAppImage = new Path[1];

        new PackageTest().addRunOnceInitializer(() -> {
            var cmd = JPackageCommand.helloAppImage()
                    .setArgumentValue("--name", "foo")
                    .setFakeRuntime();

            for (var i = 1; i != cfgs.length; ++i) {
                var al = new AdditionalLauncher("launcher-" + i);
                cfgs[i].applyToAdditionalLauncher(al);
                al.withoutVerifyActions(Action.EXECUTE_LAUNCHER).applyTo(cmd);
            }

            cmd.execute();

            predefinedAppImage[0] = cmd.outputBundle();
        }).addInitializer(cmd -> {
            cmd.removeArgumentWithValue("--input");
            cmd.setArgumentValue("--name", "AddLShortcutDir2Test");
            cmd.addArguments("--app-image", predefinedAppImage[0]);
            cfgs[0].applyToMainLauncher(cmd);
        }).run(RunnablePackageTest.Action.CREATE_AND_UNPACK);
    }

    public static Collection<Object[]> testShortcutStartupDirectoryLinux() {
        return testShortcutStartupDirectory(LauncherShortcut.LINUX_SHORTCUT);
    }

    public static Collection<Object[]> testShortcutStartupDirectoryWindows() {
        return testShortcutStartupDirectory(LauncherShortcut.WIN_DESKTOP_SHORTCUT, LauncherShortcut.WIN_START_MENU_SHORTCUT);
    }

    @Test(ifNotOS = OperatingSystem.MACOS)
    @Parameter(value = "DEFAULT")
    public void testInvokeShortcuts(StartupDirectory startupDirectory) {

        var testApp = TKit.TEST_SRC_ROOT.resolve("apps/PrintEnv.java");

        var name = "AddLShortcutRunTest";

        var test = new PackageTest().addInitializer(cmd -> {
            cmd.setArgumentValue("--name", name);
        }).addInitializer(cmd -> {
            cmd.addArguments("--arguments", "--print-workdir");
        }).addInitializer(JPackageCommand::ignoreFakeRuntime).addHelloAppInitializer(testApp + "*Hello");

        var shortcutStartupDirectoryVerifier = new ShortcutStartupDirectoryVerifier(name, "a");

        shortcutStartupDirectoryVerifier.applyTo(test, startupDirectory);

        test.addInstallVerifier(cmd -> {
            if (!cmd.isPackageUnpacked("Not invoking launcher shortcuts")) {
                Collection<? extends InvokeShortcutSpec> invokeShortcutSpecs;
                if (TKit.isLinux()) {
                    invokeShortcutSpecs = LinuxHelper.getInvokeShortcutSpecs(cmd);
                } else if (TKit.isWindows()) {
                    invokeShortcutSpecs = WinShortcutVerifier.getInvokeShortcutSpecs(cmd);
                } else {
                    throw new UnsupportedOperationException();
                }
                shortcutStartupDirectoryVerifier.verify(invokeShortcutSpecs);
            }
        });

        test.run();
    }


    private record ShortcutStartupDirectoryVerifier(String packageName, String launcherName) {
        ShortcutStartupDirectoryVerifier {
            Objects.requireNonNull(packageName);
            Objects.requireNonNull(launcherName);
        }

        void applyTo(PackageTest test, StartupDirectory startupDirectory) {
            var al = new AdditionalLauncher(launcherName);
            al.setShortcut(shortcut(), Objects.requireNonNull(startupDirectory));
            al.addJavaOptions(String.format("-Djpackage.test.appOutput=${%s}/%s",
                    outputDirVarName(), expectedOutputFilename()));
            al.withoutVerifyActions(Action.EXECUTE_LAUNCHER).applyTo(test);
        }

        void verify(Collection<? extends InvokeShortcutSpec> invokeShortcutSpecs) throws IOException {

            TKit.trace(String.format("Verify shortcut [%s]", launcherName));

            var expectedOutputFile = Path.of(System.getenv(outputDirVarName())).resolve(expectedOutputFilename());

            TKit.deleteIfExists(expectedOutputFile);

            var invokeShortcutSpec = invokeShortcutSpecs.stream().filter(v -> {
                return launcherName.equals(v.launcherName());
            }).findAny().orElseThrow();

            invokeShortcutSpec.execute();

            // On Linux, "gtk-launch" is used to launch a .desktop file. It is async and there is no
            // way to make it wait for exit of a process it triggers.
            TKit.waitForFileCreated(expectedOutputFile, Duration.ofSeconds(10), Duration.ofSeconds(3));

            TKit.assertFileExists(expectedOutputFile);
            var actualStr = Files.readAllLines(expectedOutputFile).getFirst();

            var outputPrefix = "$CD=";

            TKit.assertTrue(actualStr.startsWith(outputPrefix), "Check output starts with '" + outputPrefix+ "' string");

            invokeShortcutSpec.expectedWorkDirectory().ifPresent(expectedWorkDirectory -> {
                TKit.assertEquals(
                        expectedWorkDirectory,
                        Path.of(actualStr.substring(outputPrefix.length())),
                        String.format("Check work directory of %s of launcher [%s]",
                                invokeShortcutSpec.shortcut().propertyName(),
                                invokeShortcutSpec.launcherName()));
            });
        }

        private String expectedOutputFilename() {
            return String.format("%s-%s.out", packageName, launcherName);
        }

        private String outputDirVarName() {
            if (TKit.isLinux()) {
                return "HOME";
            } else if (TKit.isWindows()) {
                return "LOCALAPPDATA";
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private LauncherShortcut shortcut() {
            if (TKit.isLinux()) {
                return LauncherShortcut.LINUX_SHORTCUT;
            } else if (TKit.isWindows()) {
                return LauncherShortcut.WIN_DESKTOP_SHORTCUT;
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }


    private static Collection<Object[]> testShortcutStartupDirectory(LauncherShortcut... shortcuts) {
        List<List<LauncherShortcutStartupDirectoryConfig>> items = new ArrayList<>();

        for (var shortcut : shortcuts) {
            List<LauncherShortcutStartupDirectoryConfig> mainLauncherVariants = new ArrayList<>();
            for (var valueSetter : StartupDirectoryValueSetter.MAIN_LAUNCHER_VALUES) {
                mainLauncherVariants.add(new LauncherShortcutStartupDirectoryConfig(shortcut, valueSetter));
            }
            mainLauncherVariants.stream().map(List::of).forEach(items::add);
            mainLauncherVariants.add(new LauncherShortcutStartupDirectoryConfig(shortcut));

            List<LauncherShortcutStartupDirectoryConfig> addLauncherVariants = new ArrayList<>();
            addLauncherVariants.add(new LauncherShortcutStartupDirectoryConfig(shortcut));
            for (var valueSetter : StartupDirectoryValueSetter.ADD_LAUNCHER_VALUES) {
                addLauncherVariants.add(new LauncherShortcutStartupDirectoryConfig(shortcut, valueSetter));
            }

            for (var mainLauncherVariant : mainLauncherVariants) {
                for (var addLauncherVariant : addLauncherVariants) {
                    if (mainLauncherVariant.valueSetter().isPresent() || addLauncherVariant.valueSetter().isPresent()) {
                        items.add(List.of(mainLauncherVariant, addLauncherVariant));
                    }
                }
            }
        }

        return items.stream().map(List::toArray).toList();
    }


    private enum StartupDirectoryValueSetter {
        DEFAULT(""),
        TRUE("true"),
        FALSE("false"),
        ;

        StartupDirectoryValueSetter(String value) {
            this.value = Objects.requireNonNull(value);
        }

        void applyToMainLauncher(LauncherShortcut shortcut, JPackageCommand cmd) {
            switch (this) {
                case TRUE, FALSE -> {
                    throw new UnsupportedOperationException();
                }
                case DEFAULT -> {
                    cmd.addArgument(shortcut.optionName());
                }
                default -> {
                    cmd.addArguments(shortcut.optionName(), value);
                }
            }
        }

        void applyToAdditionalLauncher(LauncherShortcut shortcut, AdditionalLauncher addLauncher) {
            addLauncher.setProperty(shortcut.propertyName(), value);
        }

        private final String value;

        static final List<StartupDirectoryValueSetter> MAIN_LAUNCHER_VALUES = List.of(
                StartupDirectoryValueSetter.DEFAULT
        );

        static final List<StartupDirectoryValueSetter> ADD_LAUNCHER_VALUES = List.of(
                StartupDirectoryValueSetter.TRUE,
                StartupDirectoryValueSetter.FALSE
        );
    }


    record LauncherShortcutStartupDirectoryConfig(LauncherShortcut shortcut, Optional<StartupDirectoryValueSetter> valueSetter) {

        LauncherShortcutStartupDirectoryConfig {
            Objects.requireNonNull(shortcut);
            Objects.requireNonNull(valueSetter);
        }

        LauncherShortcutStartupDirectoryConfig(LauncherShortcut shortcut, StartupDirectoryValueSetter valueSetter) {
            this(shortcut, Optional.of(valueSetter));
        }

        LauncherShortcutStartupDirectoryConfig(LauncherShortcut shortcut) {
            this(shortcut, Optional.empty());
        }

        void applyToMainLauncher(JPackageCommand target) {
            valueSetter.ifPresent(valueSetter -> {
                valueSetter.applyToMainLauncher(shortcut, target);
            });
        }

        void applyToAdditionalLauncher(AdditionalLauncher target) {
            valueSetter.ifPresent(valueSetter -> {
                valueSetter.applyToAdditionalLauncher(shortcut, target);
            });
        }

        @Override
        public String toString() {
            return shortcut + "=" + valueSetter.map(Object::toString).orElse("");
        }
    }


    private static final Path GOLDEN_ICON = TKit.TEST_SRC_ROOT.resolve(Path.of(
            "resources", "icon" + TKit.ICON_SUFFIX));
}
