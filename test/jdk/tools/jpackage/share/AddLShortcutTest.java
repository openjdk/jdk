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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.test.AdditionalLauncher;
import jdk.jpackage.test.Annotations.Parameter;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.FileAssociations;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JavaAppDesc;
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
 * @requires (os.family != "mac")
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
 * @requires (os.family != "mac")
 * @requires (jpackage.test.SQETest == null)
 * @compile -Xlint:all -Werror AddLShortcutTest.java
 * @run main/othervm/timeout=1080 -Xmx512m
 *  jdk.jpackage.test.Main
 *  --jpt-run=AddLShortcutTest
 */

public class AddLShortcutTest {

    @Test(ifNotOS = OperatingSystem.MACOS)
    public void test() {
        // Configure several additional launchers with each combination of
        // possible shortcut hints in add-launcher property file.
        // default is true so Foo (no property), and Bar (properties set to "true")
        // will have shortcuts while other launchers with some properties set
        // to "false" will have none.

        final var packageName = MethodHandles.lookup().lookupClass().getSimpleName();

        PackageTest packageTest = new PackageTest().configureHelloApp();
        packageTest.addInitializer(cmd -> {
            cmd.addArguments("--arguments", "Duke", "--arguments", "is",
                    "--arguments", "the", "--arguments", "King");
            if (TKit.isWindows()) {
                cmd.addArguments("--win-shortcut", "--win-menu");
            } else if (TKit.isLinux()) {
                cmd.addArguments("--linux-shortcut");
            }

            cmd.setArgumentValue("--name", packageName);

            var addLauncherApp = TKit.TEST_SRC_ROOT.resolve("apps/PrintEnv.java");
            HelloApp.createBundle(JavaAppDesc.parse(addLauncherApp + "*another.jar:Welcome"), cmd.inputDir());
        });

        if (RunnablePackageTest.hasAction(RunnablePackageTest.Action.INSTALL)) {
            // Ensure launchers are executable because the output bundle will be installed
            // and launchers will be attempted to be executed through their shortcuts.
            packageTest.addInitializer(JPackageCommand::ignoreFakeRuntime);
        }

        new FileAssociations(packageName).applyTo(packageTest);

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

        var launcher5 = new AdditionalLauncher("Launcher5")
                .setDefaultArguments("--print-workdir")
                .setIcon(GOLDEN_ICON)
                .setShortcut(LauncherShortcut.LINUX_SHORTCUT, StartupDirectory.APP_DIR)
                .setShortcut(LauncherShortcut.WIN_DESKTOP_SHORTCUT, StartupDirectory.APP_DIR)
                .setShortcut(LauncherShortcut.WIN_START_MENU_SHORTCUT, null)
                .setProperty("main-jar", "another.jar")
                .setProperty("main-class", "Welcome");

        new ShortcutStartupDirectoryVerifier(packageName).add(launcher5).applyTo(packageTest);

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

        var appImageCmd = JPackageCommand.helloAppImage()
                .setArgumentValue("--name", "foo")
                .setFakeRuntime();

        for (var i = 1; i != cfgs.length; ++i) {
            var al = new AdditionalLauncher("launcher-" + i);
            cfgs[i].applyToAdditionalLauncher(al);
            al.withoutVerifyActions(Action.EXECUTE_LAUNCHER).applyTo(appImageCmd);
        }

        new PackageTest()
        .addRunOnceInitializer(appImageCmd::execute)
        .usePredefinedAppImage(appImageCmd)
        .addInitializer(cmd -> {
            cfgs[0].applyToMainLauncher(cmd);
            cmd.setArgumentValue("--name", "AddLShortcutDir2Test");
        }).run(RunnablePackageTest.Action.CREATE_AND_UNPACK);
    }

    @Test(ifNotOS = OperatingSystem.MACOS)
    @Parameter(value = "DEFAULT")
    @Parameter(value = "APP_DIR")
    public void testLastArg(StartupDirectory startupDirectory) {
        final List<String> shortcutArgs = new ArrayList<>();
        if (TKit.isLinux()) {
            shortcutArgs.add("--linux-shortcut");
        } else if (TKit.isWindows()) {
            shortcutArgs.add("--win-shortcut");
        } else {
            TKit.assertUnexpected("Unsupported platform");
        }

        if (startupDirectory == StartupDirectory.APP_DIR) {
            shortcutArgs.add(startupDirectory.asStringValue());
        }

        var appImageCmd = JPackageCommand.helloAppImage()
                .setArgumentValue("--name", "foo")
                .setFakeRuntime();

        new PackageTest()
        .addRunOnceInitializer(appImageCmd::execute)
        .usePredefinedAppImage(appImageCmd)
        .addInitializer(cmd -> {
            cmd.setArgumentValue("--name", "AddLShortcutDir3Test");
            cmd.ignoreDefaultVerbose(true);
        }).addInitializer(cmd -> {
            cmd.addArguments(shortcutArgs);
        }).addBundleVerifier(cmd -> {
            TKit.assertEquals(shortcutArgs.getLast(), cmd.getAllArguments().getLast(),
                    "Check the last argument of jpackage command line");
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
    @Parameter(value = "APP_DIR")
    public void testInvokeShortcuts(StartupDirectory startupDirectory) {

        var testApp = TKit.TEST_SRC_ROOT.resolve("apps/PrintEnv.java");

        var name = "AddLShortcutRunTest";

        var test = new PackageTest().addInitializer(cmd -> {
            cmd.setArgumentValue("--name", name);
        }).addInitializer(cmd -> {
            cmd.addArguments("--arguments", "--print-workdir");
        }).addInitializer(JPackageCommand::ignoreFakeRuntime).addHelloAppInitializer(testApp + "*Hello");

        new ShortcutStartupDirectoryVerifier(name).add("a", startupDirectory).applyTo(test);

        test.run();
    }


    private static final class ShortcutStartupDirectoryVerifier {

        ShortcutStartupDirectoryVerifier(String packageName) {
            this.packageName = Objects.requireNonNull(packageName);
        }

        void applyTo(PackageTest test) {
            verifiers.values().forEach(verifier -> {
                verifier.applyTo(test);
            });
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

                    var invokeShortcutSpecsMap = invokeShortcutSpecs.stream().collect(Collectors.groupingBy(InvokeShortcutSpec::launcherName));

                    for (var e : verifiers.entrySet()) {
                        e.getValue().verify(invokeShortcutSpecsMap.get(e.getKey()));
                    }
                }
            });
        }

        ShortcutStartupDirectoryVerifier add(String launcherName, StartupDirectory startupDirectory) {
            return add(new AdditionalLauncher(launcherName)
                    .setShortcut(shortcut(), Objects.requireNonNull(Objects.requireNonNull(startupDirectory))));
        }

        ShortcutStartupDirectoryVerifier add(AdditionalLauncher addLauncher) {
            var launcherVerifier = new LauncherVerifier(addLauncher);
            verifiers.put(launcherVerifier.launcherName(), launcherVerifier);
            return this;
        }


        private final class LauncherVerifier {

            private LauncherVerifier(AdditionalLauncher addLauncher) {
                this.addLauncher = Objects.requireNonNull(addLauncher);
            }

            private String launcherName() {
                return addLauncher.name();
            }

            private void applyTo(PackageTest test) {
                addLauncher.addJavaOptions(String.format("-Djpackage.test.appOutput=${%s}/%s",
                        outputDirVarName(), expectedOutputFilename()));
                addLauncher.withoutVerifyActions(Action.EXECUTE_LAUNCHER).applyTo(test);
            }

            private void verify(Collection<? extends InvokeShortcutSpec> invokeShortcutSpecs) throws IOException {
                Objects.requireNonNull(invokeShortcutSpecs);
                if (invokeShortcutSpecs.isEmpty()) {
                    throw new IllegalArgumentException();
                }

                TKit.trace(String.format("Verify shortcut [%s]", launcherName()));

                var expectedOutputFile = Path.of(System.getenv(outputDirVarName())).resolve(expectedOutputFilename());

                TKit.deleteIfExists(expectedOutputFile);

                var invokeShortcutSpec = invokeShortcutSpecs.stream().filter(v -> {
                    return launcherName().equals(v.launcherName());
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
                return String.format("%s-%s.out", packageName, launcherName());
            }

            private final AdditionalLauncher addLauncher;
        }


        private static String outputDirVarName() {
            if (TKit.isLinux()) {
                return "HOME";
            } else if (TKit.isWindows()) {
                return "LOCALAPPDATA";
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private static LauncherShortcut shortcut() {
            if (TKit.isLinux()) {
                return LauncherShortcut.LINUX_SHORTCUT;
            } else if (TKit.isWindows()) {
                return LauncherShortcut.WIN_DESKTOP_SHORTCUT;
            } else {
                throw new UnsupportedOperationException();
            }
        }

        private final String packageName;
        // Keep the order
        private final Map<String, LauncherVerifier> verifiers = new LinkedHashMap<>();
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
