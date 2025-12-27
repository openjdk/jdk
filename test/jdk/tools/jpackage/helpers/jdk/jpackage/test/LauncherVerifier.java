/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.test;

import static java.util.stream.Collectors.toMap;
import static jdk.jpackage.test.AdditionalLauncher.NO_ICON;
import static jdk.jpackage.test.AdditionalLauncher.getAdditionalLauncherProperties;
import static jdk.jpackage.test.LauncherShortcut.LINUX_SHORTCUT;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
import jdk.jpackage.internal.resources.ResourceLocator;
import jdk.jpackage.internal.util.PListReader;
import jdk.jpackage.internal.util.function.ThrowingBiConsumer;
import jdk.jpackage.internal.util.function.ThrowingSupplier;
import jdk.jpackage.test.LauncherShortcut.StartupDirectory;
import org.xml.sax.SAXException;

public final class LauncherVerifier {

    LauncherVerifier(JPackageCommand cmd) {
        name = cmd.name();
        javaOptions = Optional.empty();
        arguments = Optional.empty();
        icon = Optional.empty();
        properties = Optional.empty();
    }

    LauncherVerifier(String name,
            Optional<List<String>> javaOptions,
            Optional<List<String>> arguments,
            Optional<Path> icon,
            Map<String, String> properties) {
        this.name = Objects.requireNonNull(name);
        this.javaOptions = javaOptions.map(List::copyOf);
        this.arguments = arguments.map(List::copyOf);
        this.icon = icon;
        this.properties = Optional.of(new PropertyFile(properties));
    }

    static void executeMainLauncherAndVerifyOutput(JPackageCommand cmd) {
        new LauncherVerifier(cmd).verify(cmd, Action.EXECUTE_LAUNCHER);
    }

    static String launcherDescription(JPackageCommand cmd, String launcherName) {
        return launcherDescription(cmd, launcherName, (theCmd, theLauncherName) -> {
            return getAdditionalLauncherProperties(theCmd, theLauncherName);
        });
    }


    public enum Action {
        VERIFY_ICON(LauncherVerifier::verifyIcon),
        VERIFY_DESCRIPTION(LauncherVerifier::verifyDescription),
        VERIFY_INSTALLED((verifier, cmd) -> {
            verifier.verifyInstalled(cmd, true);
        }),
        VERIFY_UNINSTALLED((verifier, cmd) -> {
            verifier.verifyInstalled(cmd, false);
        }),
        VERIFY_APP_IMAGE_FILE((verifier, cmd) -> {
            if (cmd.isImagePackageType()) {
                verifier.verifyInAppImageFile(cmd);
            }
        }),
        VERIFY_MAC_ENTITLEMENTS((verifier, cmd) -> {
            if (TKit.isOSX() && MacHelper.appImageSigned(cmd)) {
                verifier.verifyMacEntitlements(cmd);
            }
        }),
        EXECUTE_LAUNCHER(LauncherVerifier::executeLauncher),
        ;

        Action(ThrowingBiConsumer<LauncherVerifier, JPackageCommand, ? extends Exception> action) {
            this.action = ThrowingBiConsumer.toBiConsumer(action);
        }

        private void apply(LauncherVerifier verifier, JPackageCommand cmd) {
            action.accept(verifier, cmd);
        }

        private final BiConsumer<LauncherVerifier, JPackageCommand> action;

        static final List<Action> VERIFY_APP_IMAGE = List.of(
                VERIFY_ICON, VERIFY_DESCRIPTION, VERIFY_INSTALLED, VERIFY_APP_IMAGE_FILE, VERIFY_MAC_ENTITLEMENTS
        );

        static final List<Action> VERIFY_DEFAULTS = Stream.concat(
                VERIFY_APP_IMAGE.stream(), Stream.of(EXECUTE_LAUNCHER)
        ).toList();
    }


    void verify(JPackageCommand cmd, Action... actions) {
        verify(cmd, List.of(actions));
    }

    void verify(JPackageCommand cmd, Iterable<Action> actions) {
        Objects.requireNonNull(cmd);
        for (var a : actions) {
            a.apply(this, cmd);
        }
    }

    private boolean isMainLauncher() {
        return properties.isEmpty();
    }

    private Optional<String> findProperty(String key) {
        return properties.flatMap(v -> {
            return v.findProperty(key);
        });
    }

    private String getDescription(JPackageCommand cmd) {
        return launcherDescription(cmd, name, (theCmd, theLauncherName) -> {
            return properties.orElseThrow();
        });
    }

    private List<String> getArguments(JPackageCommand cmd) {
        return getStringArrayProperty(cmd, "--arguments", arguments);
    }

    private List<String> getJavaOptions(JPackageCommand cmd) {
        return getStringArrayProperty(cmd, "--java-options", javaOptions);
    }

    private List<String> getStringArrayProperty(JPackageCommand cmd, String optionName, Optional<List<String>> items) {
        Objects.requireNonNull(cmd);
        Objects.requireNonNull(optionName);
        Objects.requireNonNull(items);
        if (isMainLauncher()) {
            return List.of(cmd.getAllArgumentValues(optionName));
        } else {
            return items.orElseGet(() -> {
                return List.of(cmd.getAllArgumentValues(optionName));
            });
        }
    }

    private boolean explicitlyNoShortcut(LauncherShortcut shortcut) {
        var explicit = findProperty(shortcut.propertyName());
        if (explicit.isPresent()) {
            return explicit.flatMap(StartupDirectory::parse).isEmpty();
        } else {
            return false;
        }
    }

    private static boolean explicitShortcutForMainLauncher(JPackageCommand cmd, LauncherShortcut shortcut) {
        return cmd.hasArgument(shortcut.optionName());
    }

    private void verifyIcon(JPackageCommand cmd) throws IOException {
        initIconVerifier(cmd).applyTo(cmd);
    }

    private LauncherIconVerifier initIconVerifier(JPackageCommand cmd) {
        var verifier = new LauncherIconVerifier().setLauncherName(name);

        var mainLauncherIcon = Optional.ofNullable(cmd.getArgumentValue("--icon")).map(Path::of).or(() -> {
            return iconInResourceDir(cmd, cmd.name());
        });

        if (TKit.isOSX()) {
            // There should be no icon files on Mac for additional launchers,
            // and always an icon file for the main launcher.
            if (isMainLauncher()) {
                mainLauncherIcon.ifPresentOrElse(verifier::setExpectedIcon, verifier::setExpectedDefaultIcon);
            }
            return verifier;
        }

        if (isMainLauncher()) {
            mainLauncherIcon.ifPresentOrElse(verifier::setExpectedIcon, verifier::setExpectedDefaultIcon);
        } else {
            icon.ifPresentOrElse(icon -> {
                if (!NO_ICON.equals(icon)) {
                    verifier.setExpectedIcon(icon);
                }
            }, () -> {
                // No "icon" property in the property file.
                iconInResourceDir(cmd, name).ifPresentOrElse(verifier::setExpectedIcon, () -> {
                    // No icon for this additional launcher in the resource directory.
                    mainLauncherIcon.ifPresentOrElse(verifier::setExpectedIcon, verifier::setExpectedDefaultIcon);
                });
            });
        }

        if (TKit.isLinux()) {
            // On Linux, a launcher may have an icon only if it has a corresponding .desktop file.
            // In case of "app-image" packaging there are no .desktop files, but jpackage will add icon files
            // in the app image anyways so that in two-step packaging jpackage can pick the icons for .desktop files.
            // jpackage should not add the default icon to the app image in case of "app-image" packaging.
            if (cmd.isImagePackageType()) {
                // This is "app-image" packaging. Let's see if, in two-step packaging,
                // jpackage creates a .desktop file for this launcher.
                if (!withLinuxDesktopFile(cmd.createMutableCopy().setPackageType(PackageType.LINUX_RPM))) {
                    // No .desktop file in the "future" package for this launcher,
                    // then don't expect an icon in the app image produced by the `cmd`.
                    verifier.setExpectedNoIcon();
                } else if (verifier.expectDefaultIcon()) {
                    // A .desktop file in the "future" package for this launcher,
                    // but it will use the default icon.
                    // Don't expect an icon in the app image produced by the `cmd`.
                    verifier.setExpectedNoIcon();
                }
            } else if (!withLinuxDesktopFile(cmd)) {
                verifier.setExpectedNoIcon();
            }
        }

        return verifier;
    }

    private static boolean withLinuxMainLauncherDesktopFile(JPackageCommand cmd) {
        if (!TKit.isLinux() || cmd.isImagePackageType()) {
            return false;
        }

        return explicitShortcutForMainLauncher(cmd, LINUX_SHORTCUT)
                || cmd.hasArgument("--icon")
                || cmd.hasArgument("--file-associations")
                || iconInResourceDir(cmd, cmd.name()).isPresent();
    }

    private boolean withLinuxDesktopFile(JPackageCommand cmd) {
        if (!TKit.isLinux() || cmd.isImagePackageType()) {
            return false;
        }

        if (isMainLauncher()) {
            return withLinuxMainLauncherDesktopFile(cmd);
        } else if (explicitlyNoShortcut(LINUX_SHORTCUT) || icon.map(icon -> {
            return icon.equals(NO_ICON);
        }).orElse(false)) {
            return false;
        } else if (iconInResourceDir(cmd, name).isPresent() || icon.map(icon -> {
            return !icon.equals(NO_ICON);
        }).orElse(false)) {
            return true;
        } else if (findProperty(LINUX_SHORTCUT.propertyName()).flatMap(StartupDirectory::parse).isPresent()) {
            return true;
        } else {
            return withLinuxMainLauncherDesktopFile(cmd.createMutableCopy().removeArgument("--file-associations"));
        }
    }

    private void verifyDescription(JPackageCommand cmd) throws IOException {
        if (TKit.isWindows() && !cmd.hasArgument("--app-image")) {
            // On Windows, check the description if the predefined app image is not configured.
            // The description and the icon are encoded in the launcher executable, which should be
            // copied verbatim from the predefined app image into the output bundle.
            // This check is done in the JPackageCommand class, so there is no need to duplicate it here.
            String expectedDescription = getDescription(cmd);
            Path launcherPath = cmd.appLauncherPath(name);
            String actualDescription =
                    WindowsHelper.getExecutableDescription(launcherPath);
            TKit.assertEquals(expectedDescription, actualDescription,
                    String.format("Check file description of [%s]", launcherPath));
        } else if (TKit.isLinux() && !cmd.isImagePackageType()) {
            String expectedDescription = getDescription(cmd);
            Path desktopFile = LinuxHelper.getDesktopFile(cmd, name);
            if (Files.exists(desktopFile)) {
                TKit.assertTextStream("Comment=" + expectedDescription)
                        .label(String.format("[%s] file", desktopFile))
                        .predicate(String::equals)
                        .apply(Files.readAllLines(desktopFile));
            }
        }
    }

    private void verifyInstalled(JPackageCommand cmd, boolean installed) throws IOException {
        var launcherPath = cmd.appLauncherPath(name);
        var launcherCfgFilePath = cmd.appLauncherCfgPath(name);
        if (installed) {
            TKit.assertExecutableFileExists(launcherPath);
            TKit.assertFileExists(launcherCfgFilePath);
        } else {
            TKit.assertPathExists(launcherPath, false);
            TKit.assertPathExists(launcherCfgFilePath, false);
        }

        if (TKit.isLinux() && !cmd.isImagePackageType()) {
            final var packageDesktopFile = LinuxHelper.getDesktopFile(cmd, name);
            final var withLinuxDesktopFile = withLinuxDesktopFile(cmd) && installed;
            if (withLinuxDesktopFile) {
                TKit.assertFileExists(packageDesktopFile);
            } else {
                TKit.assertPathExists(packageDesktopFile, false);
            }
        }

        if (installed) {
            initIconVerifier(cmd).verifyFileInAppImageOnly(true).applyTo(cmd);
        }
    }

    private void verifyInAppImageFile(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.IMAGE);
        if (!isMainLauncher()) {
            Stream<LauncherShortcut> shortcuts;
            if (TKit.isWindows()) {
                shortcuts = Stream.of(LauncherShortcut.WIN_DESKTOP_SHORTCUT, LauncherShortcut.WIN_START_MENU_SHORTCUT);
            } else if (TKit.isLinux()) {
                shortcuts = Stream.of(LauncherShortcut.LINUX_SHORTCUT);
            } else {
                shortcuts = Stream.of();
            }

            var aif = AppImageFile.load(cmd.outputBundle());
            var aifFileName = AppImageFile.getPathInAppImage(Path.of("")).getFileName();

            var aifProps = Objects.requireNonNull(aif.addLaunchers().get(name));

            shortcuts.forEach(shortcut -> {
                var recordedShortcut = aifProps.get(shortcut.appImageFilePropertyName());
                properties.flatMap(props -> {
                    return props.findProperty(shortcut.propertyName());
                }).ifPresentOrElse(expectedShortcut -> {
                    TKit.assertNotNull(recordedShortcut, String.format(
                            "Check shortcut [%s] of launcher [%s] is recorded in %s file",
                            shortcut, name, aifFileName));
                    TKit.assertEquals(
                            StartupDirectory.parse(expectedShortcut),
                            StartupDirectory.parse(recordedShortcut),
                            String.format("Check the value of shortcut [%s] of launcher [%s] recorded in %s file",
                                    shortcut, name, aifFileName));
                }, () -> {
                    TKit.assertNull(recordedShortcut, String.format(
                            "Check shortcut [%s] of launcher [%s] is NOT recorded in %s file",
                            shortcut, name, aifFileName));
                });
            });
        }
    }

    private void verifyMacEntitlements(JPackageCommand cmd) throws ParserConfigurationException, SAXException, IOException {
        Path launcherPath = cmd.appLauncherPath(name);
        var entitlements = MacSignVerify.findEntitlements(launcherPath);

        TKit.assertTrue(entitlements.isPresent(), String.format("Check [%s] launcher is signed with entitlements", name));

        var customFile = Optional.ofNullable(cmd.getArgumentValue("--mac-entitlements")).map(Path::of);
        if (customFile.isEmpty()) {
            // Try from the resource dir.
            var resourceDirFile = Optional.ofNullable(cmd.getArgumentValue("--resource-dir")).map(Path::of).map(resourceDir -> {
                return resourceDir.resolve(cmd.name() + ".entitlements");
            }).filter(Files::exists);
            if (resourceDirFile.isPresent()) {
                customFile = resourceDirFile;
            }
        }

        Map<String, Object> expected;
        if (customFile.isPresent()) {
            expected = new PListReader(Files.readAllBytes(customFile.orElseThrow())).toMap(true);
        } else if (cmd.hasArgument("--mac-app-store")) {
            expected = DefaultEntitlements.APP_STORE;
        } else {
            expected = DefaultEntitlements.STANDARD;
        }

        TKit.assertEquals(expected, entitlements.orElseThrow().toMap(true), String.format("Check [%s] launcher is signed with expected entitlements", name));
    }

    private void executeLauncher(JPackageCommand cmd) throws IOException {
        Path launcherPath = cmd.appLauncherPath(name);

        if (!cmd.canRunLauncher(String.format("Not running [%s] launcher", launcherPath))) {
            return;
        }

        var appVerifier = HelloApp.assertApp(launcherPath)
                .addDefaultArguments(getArguments(cmd))
                .addJavaOptions(getJavaOptions(cmd).stream().map(str -> {
                    return resolveVariables(cmd, str);
                }).toList());

        appVerifier.executeAndVerifyOutput();
    }

    private static String resolveVariables(JPackageCommand cmd, String str) {
        var map = Stream.of(JPackageCommand.Macro.values()).collect(toMap(x -> {
            return String.format("$%s", x.name());
        }, cmd::macroValue));
        for (var e : map.entrySet()) {
            str = str.replaceAll(Pattern.quote(e.getKey()),
                    Matcher.quoteReplacement(e.getValue().toString()));
        }
        return str;
    }

    private static Optional<Path> iconInResourceDir(JPackageCommand cmd, String launcherName) {
        Objects.requireNonNull(launcherName);
        return Optional.ofNullable(cmd.getArgumentValue("--resource-dir")).map(Path::of).map(resourceDir -> {
            Path icon = resourceDir.resolve(launcherName + TKit.ICON_SUFFIX);
            if (Files.exists(icon)) {
                return icon;
            } else {
                return null;
            }
        });
    }

    private static String launcherDescription(
            JPackageCommand cmd,
            String launcherName,
            BiFunction<JPackageCommand, String, PropertyFile> addLauncherPropertyFileGetter) {

        return PropertyFinder.findLauncherProperty(cmd, launcherName,
                PropertyFinder.cmdlineOptionWithValue("--description"),
                PropertyFinder.launcherPropertyFile("description"),
                PropertyFinder.appImageFileLauncher(cmd, launcherName, "description")
        ).orElseGet(() -> {
            if (cmd.isMainLauncher(launcherName)) {
                return cmd.mainLauncherName();
            } else {
                return launcherDescription(cmd, null, addLauncherPropertyFileGetter);
            }
        });
    }


    private static final class DefaultEntitlements {
        private static Map<String, Object> loadFromResources(String resourceName) {
            return ThrowingSupplier.toSupplier(() -> {
                var bytes = ResourceLocator.class.getResourceAsStream(resourceName).readAllBytes();
                return new PListReader(bytes).toMap(true);
            }).get();
        }

        static final Map<String, Object> STANDARD = loadFromResources("entitlements.plist");
        static final Map<String, Object> APP_STORE = loadFromResources("sandbox.plist");
    }


    private final String name;
    private final Optional<List<String>> javaOptions;
    private final Optional<List<String>> arguments;
    private final Optional<Path> icon;
    private final Optional<PropertyFile> properties;
}
