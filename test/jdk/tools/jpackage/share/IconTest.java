/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.test.AdditionalLauncher.getAdditionalLauncherProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.function.ThrowingBiConsumer;
import jdk.jpackage.internal.util.function.ThrowingConsumer;
import jdk.jpackage.test.AdditionalLauncher;
import jdk.jpackage.test.Annotations.Parameters;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.ConfigurationTarget;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.LauncherIconVerifier;
import jdk.jpackage.test.LinuxHelper;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.TKit;

/*
 * @test
 * @summary jpackage create image and package with custom icons for the main and additional launcher
 * @library /test/jdk/tools/jpackage/helpers
 * @build jdk.jpackage.test.*
 * @compile -Xlint:all -Werror IconTest.java
 * @run main/othervm/timeout=2880 -Xmx512m
 *  jdk.jpackage.test.Main
 *  --jpt-run=IconTest
 */

public class IconTest {

    enum IconType {
        /**
         * Icon not specified.
         */
        DefaultIcon,

        /**
         * Explicit no icon.
         */
        NoIcon,

        /**
         * Custom icon on command line.
         */
        CustomIcon,

        /**
         * Custom icon in resource dir.
         */
        ResourceDirIcon,

        /**
         * Custom icon on command line and in resource dir.
         */
        CustomWithResourceDirIcon
    }

    enum BundleType { AppImage, Package }

    public IconTest(BundleType bundleType, IconType mainLauncherIconType,
            IconType additionalLauncherIconType, String[] extraJPackageArgs) {
        this.appImage = (bundleType == BundleType.AppImage);
        this.extraJPackageArgs = extraJPackageArgs;
        config = new TreeMap<>(Map.of(
                Launcher.Main, mainLauncherIconType,
                Launcher.Additional, additionalLauncherIconType));
    }

    public IconTest(BundleType bundleType, IconType mainLauncherIconType,
            IconType additionalLauncherIconType) {
        this.appImage = (bundleType == BundleType.AppImage);
        this.extraJPackageArgs = new String[0];
        config = new TreeMap<>(Map.of(
                Launcher.Main, mainLauncherIconType,
                Launcher.Additional, additionalLauncherIconType));
    }

    public IconTest(BundleType bundleType, IconType mainLauncherIconType) {
        this.appImage = (bundleType == BundleType.AppImage);
        this.extraJPackageArgs = new String[0];
        config = Map.of(Launcher.Main, mainLauncherIconType);
    }

    @Parameters
    public static Collection<?> data() {
        List<Object[]> data = new ArrayList<>();

        var withLinuxShortcut = Set.of(IconType.DefaultIcon, IconType.NoIcon);

        for (var bundleType : BundleType.values()) {
            if (TKit.isWindows() && bundleType == BundleType.Package) {
                // On Windows icons are embedded in launcher executables in
                // application image. Nothing is changed when app image is
                // packed in msi/exe package bundle, so skip testing of package
                // bundle.
                continue;
            }
            for (var mainLauncherIconType : IconType.values()) {
                if (mainLauncherIconType == IconType.NoIcon) {
                    // `No icon` setting is not applicable for the main launcher.
                    continue;
                }

                if (TKit.isOSX()) {
                    // Custom icons not supported for additional launchers on Mac.
                    data.add(new Object[]{bundleType, mainLauncherIconType});
                    continue;
                }

                for (var additionalLauncherIconType : IconType.values()) {
                    data.add(new Object[]{bundleType, mainLauncherIconType,
                        additionalLauncherIconType});

                    if (TKit.isLinux() && bundleType == BundleType.Package
                            && withLinuxShortcut.contains(mainLauncherIconType)
                            && withLinuxShortcut.contains(
                                    additionalLauncherIconType)) {
                        data.add(new Object[]{bundleType, mainLauncherIconType,
                            additionalLauncherIconType, new String[]{
                            "--linux-shortcut"}});
                    }
                }
            }
        }
        return data;
    }

    @Test
    public void test() throws IOException {

        final ConfigurationTarget target;
        if (appImage) {
            target = new ConfigurationTarget(JPackageCommand.helloAppImage());
        } else {
            target = new ConfigurationTarget(new PackageTest().configureHelloApp());
        }

        initTest(target);

        var installVerifier = createInstallVerifier();
        var bundleVerifier = createBundleVerifier();

        var cmdResult = target.cmd().map(JPackageCommand::executeAndAssertImageCreated);

        target.apply(ThrowingConsumer.toConsumer(installVerifier), test -> {
            test.addInstallVerifier(installVerifier);
        }).apply(cmd -> {
            ThrowingBiConsumer.toBiConsumer(bundleVerifier).accept(cmd, cmdResult.orElseThrow());
        }, test -> {
            test.addBundleVerifier(bundleVerifier);
            test.addBundleDesktopIntegrationVerifier(config.values().stream()
                    .anyMatch(this::isWithDesktopIntegration));
        });

        target.test().ifPresent(v -> {
            v.run(PackageTest.Action.CREATE_AND_UNPACK);
        });
    }

    boolean isWithDesktopIntegration(IconType iconType) {
        boolean withDesktopFile = !Set.of(
                IconType.NoIcon,
                IconType.DefaultIcon).contains(iconType);
        withDesktopFile |= List.of(extraJPackageArgs).contains("--linux-shortcut");
        return withDesktopFile;
    }

    private ThrowingBiConsumer<JPackageCommand, Executor.Result, IOException> createBundleVerifier() {
        return (cmd, result) -> {
            Stream.of(Launcher.Main, Launcher.Additional).filter(config::containsKey).forEach(launcher -> {
                createConsoleOutputVerifier(cmd, launcher).ifPresent(verifier -> {
                    verifier.apply(result.getOutput());
                });
            });
        };
    }

    private Optional<TKit.TextStreamVerifier> createConsoleOutputVerifier(
            JPackageCommand cmd, Launcher launcher) {

        var launcherName = Optional.ofNullable(launcher.launcherName).orElseGet(cmd::name);
        var resourceName = launcherName;
        Optional<Path> customIcon;

        if (launcherName.equals(cmd.name())) {
            customIcon = Optional.ofNullable(cmd.getArgumentValue("--icon")).map(Path::of);
        } else if (config.get(launcher) == IconType.DefaultIcon) {
            resourceName = cmd.name();
            customIcon = Optional.ofNullable(cmd.getArgumentValue("--icon")).map(Path::of);
        } else {
            customIcon = getAdditionalLauncherProperties(cmd, launcherName).findProperty("icon").map(Path::of);
        }

        return createConsoleOutputVerifier(
                getBundleIconType(cmd, launcher),
                launcherName,
                resourceName,
                customIcon);
    }

    private static Optional<TKit.TextStreamVerifier> createConsoleOutputVerifier(
            IconType iconType, String launcherName, String resourceName, Optional<Path> customIcon) {

        Objects.requireNonNull(launcherName);
        Objects.requireNonNull(resourceName);
        Objects.requireNonNull(customIcon);

        CannedFormattedString lookupString;

        switch (iconType) {
            case DefaultIcon:
                lookupString = JPackageStringBundle.MAIN.cannedFormattedString(
                        "message.using-default-resource",
                        "JavaApp" + TKit.ICON_SUFFIX,
                        "[icon]",
                        launcherName + TKit.ICON_SUFFIX);
                break;

            case ResourceDirIcon:
                lookupString = JPackageStringBundle.MAIN.cannedFormattedString(
                        "message.using-custom-resource",
                        "[icon]",
                        resourceName + TKit.ICON_SUFFIX);
                break;

            case CustomIcon:
            case CustomWithResourceDirIcon:
                lookupString = JPackageStringBundle.MAIN.cannedFormattedString(
                        "message.using-custom-resource-from-file",
                        "[icon]",
                        customIcon.orElseThrow());
                break;

            default:
                return Optional.empty();
        }

        return Optional.of(TKit.assertTextStream(lookupString.getValue()));
    }

    private ThrowingConsumer<JPackageCommand, IOException> createInstallVerifier() {
        return cmd -> {
            var verifier = new LauncherIconVerifier();

            var bundleIconType = getBundleIconType(cmd, Launcher.Main);

            switch (bundleIconType) {
                case NoIcon:
                    verifier.setExpectedNoIcon();
                    break;

                case DefaultIcon:
                    verifier.setExpectedDefaultIcon();
                    break;

                case CustomIcon:
                    verifier.setExpectedIcon(Launcher.Main.cmdlineIcon);
                    break;

                case ResourceDirIcon:
                    verifier.setExpectedIcon(Launcher.Main.resourceDirIcon);
                    break;

                case CustomWithResourceDirIcon:
                    verifier.setExpectedIcon(Launcher.Main2.cmdlineIcon);
                    break;
            }

            verifier.applyTo(cmd);

            if (TKit.isLinux() && !cmd.isImagePackageType()) {
                Path desktopFile = LinuxHelper.getDesktopFile(cmd);
                if (isWithDesktopIntegration(bundleIconType)) {
                    TKit.assertFileExists(desktopFile);
                } else {
                    TKit.assertPathExists(desktopFile, false);
                }
            }
        };
    }

    private void initTest(ConfigurationTarget target) {
        config.entrySet().forEach(ThrowingConsumer.toConsumer(entry -> {
            initTest(entry.getKey(), entry.getValue(), target);
        }));

        target.addInitializer(cmd -> {
            cmd.saveConsoleOutput(true);
            cmd.setFakeRuntime();
            cmd.addArguments(extraJPackageArgs);
        });
    }

    private static void initTest(Launcher cfg, IconType iconType,
            ConfigurationTarget target) throws IOException {

        switch (iconType) {
            case DefaultIcon:
                Optional.ofNullable(cfg.launcherName).map(AdditionalLauncher::new)
                        .ifPresent(target::add);
                break;

            case NoIcon:
                Optional.ofNullable(cfg.launcherName).map(AdditionalLauncher::new)
                        .map(AdditionalLauncher::setNoIcon)
                        .ifPresent(target::add);
                break;

            case CustomIcon:
                addCustomIcon(target, cfg.launcherName, cfg.cmdlineIcon);
                break;

            case ResourceDirIcon:
                if (Launcher.PRIMARY.contains(cfg)) {
                    Optional.ofNullable(cfg.launcherName).map(AdditionalLauncher::new)
                            .ifPresent(target::add);
                }
                target.addInitializer(cmd -> {
                    try {
                        addResourceDirIcon(cmd, cfg.launcherName, cfg.resourceDirIcon);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });
                break;

            case CustomWithResourceDirIcon:
                switch (cfg) {
                    case Main:
                        initTest(Launcher.Main2, IconType.CustomIcon, target);
                        initTest(Launcher.Main2, IconType.ResourceDirIcon, target);
                        break;

                    case Additional:
                        initTest(Launcher.Additional2, IconType.CustomIcon, target);
                        initTest(Launcher.Additional2, IconType.ResourceDirIcon, target);
                        break;

                    default:
                        throw new IllegalArgumentException();
                }
                break;
        }
    }

    private IconType getBundleIconType(JPackageCommand cmd, Launcher launcher) {
        return getBundleIconType(cmd, config.get(Launcher.Main), launcher, config.get(launcher));
    }

    /**
     * Returns the expected icon type of the given launcher in the output bundle
     * that the given jpackage command line will output based on the icon type
     * configured for the launcher.
     *
     * @param cmd                  jpackage command line
     * @param mainLauncherIconType the icon type configured for the main launcher
     * @param launcher             the launcher
     * @param iconType             the icon type configured for the specified
     *                             launcher
     * @return the type of of an icon of the given launcher in the output bundle
     */
    private static IconType getBundleIconType(JPackageCommand cmd,
            IconType mainLauncherIconType, Launcher launcher, IconType iconType) {

        Objects.requireNonNull(cmd);
        Objects.requireNonNull(mainLauncherIconType);
        Objects.requireNonNull(launcher);
        Objects.requireNonNull(iconType);

        if (iconType == IconType.DefaultIcon) {
            iconType = mainLauncherIconType;
        }

        if (TKit.isLinux()) {
            var noDefaultIcon = cmd.isImagePackageType() || !cmd.hasArgument("--linux-shortcut");

            if (noDefaultIcon && iconType == IconType.DefaultIcon) {
                iconType = IconType.NoIcon;
            }
        }

        return iconType;
    }

    private static void addResourceDirIcon(JPackageCommand cmd,
            String launcherName, Path iconPath) throws IOException {
        var resourceDir = Optional.ofNullable(cmd.getArgumentValue("--resource-dir")).map(Path::of).orElseGet(() -> {
            return TKit.createTempDirectory("resources");
        });

        cmd.addArguments("--resource-dir", resourceDir);

        String dstIconFileName = Optional.ofNullable(launcherName).orElseGet(cmd::name) + TKit.ICON_SUFFIX;

        TKit.trace(String.format("Resource file: [%s] <- [%s]",
                resourceDir.resolve(dstIconFileName), iconPath));
        Files.copy(iconPath, resourceDir.resolve(dstIconFileName),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static void addCustomIcon(ConfigurationTarget target,
            String launcherName, Path iconPath) {

        if (launcherName != null) {
            var al = new AdditionalLauncher(launcherName).setIcon(iconPath);
            target.apply(al::applyTo, al::applyTo);
        } else {
            target.addInitializer(cmd -> {
                cmd.addArguments("--icon", iconPath);
            });
        }
    }

    private enum Launcher {
        Main(null, ICONS[0], ICONS[1]),
        Main2(null, ICONS[1], ICONS[0]),
        Additional("x", ICONS[2], ICONS[3]),
        Additional2("x", ICONS[3], ICONS[2]);

        Launcher(String name, Path cmdlineIcon, Path resourceDirIcon) {
            this.launcherName = name;
            this.cmdlineIcon = cmdlineIcon;
            this.resourceDirIcon = resourceDirIcon;
        }

        private final String launcherName;
        private final Path cmdlineIcon;
        private final Path resourceDirIcon;

        private static final Set<Launcher> PRIMARY = Set.of(Main, Additional);
    }

    private final boolean appImage;
    private final Map<Launcher, IconType> config;
    private final String[] extraJPackageArgs;

    private static Path iconPath(String name) {
        return TKit.TEST_SRC_ROOT.resolve(Path.of("resources", name
                + TKit.ICON_SUFFIX));
    }

    private static final Path[] ICONS = Stream.of("icon", "icon2", "icon3",
            "icon4")
            .map(IconTest::iconPath)
            .collect(Collectors.toList()).toArray(Path[]::new);
}
