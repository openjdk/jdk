/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.test.WindowsHelper.getInstallationSubDirectory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.test.MsiDatabase.Shortcut;
import jdk.jpackage.test.WindowsHelper.SpecialFolder;


final class WinShortcutVerifier {

    static void verifyBundleShortcuts(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.WIN_MSI);

        if (Stream.of("--win-menu", "--win-shortcut").noneMatch(cmd::hasArgument) && cmd.addLauncherNames().isEmpty()) {
            return;
        }

        var actualShortcuts = WindowsHelper.getMsiShortcuts(cmd).stream().collect(Collectors.groupingBy(shortcut -> {
            return PathUtils.replaceSuffix(shortcut.target().getFileName(), "").toString();
        }));

        var expectedShortcuts = expectShortcuts(cmd);

        var launcherNames = expectedShortcuts.keySet().stream().sorted().toList();

        TKit.assertStringListEquals(
                launcherNames,
                actualShortcuts.keySet().stream().sorted().toList(),
                "Check the list of launchers with shortcuts");

        Function<Collection<Shortcut>, List<Shortcut>> sorter = shortcuts -> {
            return shortcuts.stream().sorted(SHORTCUT_COMPARATOR).toList();
        };

        for (var name : launcherNames) {
            var actualLauncherShortcuts = sorter.apply(actualShortcuts.get(name));
            var expectedLauncherShortcuts = sorter.apply(expectedShortcuts.get(name));

            TKit.assertEquals(expectedLauncherShortcuts.size(), actualLauncherShortcuts.size(),
                    String.format("Check the number of shortcuts of [%s] launcher", name));

            for (int i = 0; i != expectedLauncherShortcuts.size(); i++) {
                TKit.trace(String.format("Verify shortcut #%d of [%s] launcher", i + 1, name));
                actualLauncherShortcuts.get(i).assertEquals(expectedLauncherShortcuts.get(i));
                TKit.trace("Done");
            }
        }
    }

    static void verifyDeployedShortcuts(JPackageCommand cmd, boolean installed) {
        cmd.verifyIsOfType(PackageType.WINDOWS);

        verifyDeployedShortcutsInternal(cmd, installed);
        var copyCmd = cmd.createMutableCopy();
        if (copyCmd.hasArgument("--win-per-user-install")) {
            copyCmd.removeArgument("--win-per-user-install");
        } else {
            copyCmd.addArgument("--win-per-user-install");
        }
        verifyDeployedShortcutsInternal(copyCmd, false);
    }

    private static void verifyDeployedShortcutsInternal(JPackageCommand cmd, boolean installed) {

        var expectedShortcuts = expectShortcuts(cmd).values().stream().flatMap(Collection::stream).toList();

        var isUserLocalInstall = WindowsHelper.isUserLocalInstall(cmd);

        expectedShortcuts.stream().map(Shortcut::path).sorted().map(path -> {
            return resolvePath(path, !isUserLocalInstall);
        }).map(path -> {
            return PathUtils.addSuffix(path, ".lnk");
        }).forEach(path -> {
            if (installed) {
                TKit.assertFileExists(path);
            } else {
                TKit.assertPathExists(path, false);
            }
        });

        if (!installed) {
            expectedShortcuts.stream().map(Shortcut::path).filter(path -> {
                return Stream.of(ShortcutType.COMMON_START_MENU, ShortcutType.USER_START_MENU).anyMatch(type -> {
                    return path.startsWith(Path.of(type.rootFolder().getMsiPropertyName()));
                });
            }).map(Path::getParent).distinct().map(unresolvedShortcutDir -> {
                return resolvePath(unresolvedShortcutDir, !isUserLocalInstall);
            }).forEach(shortcutDir -> {
                if (Files.isDirectory(shortcutDir)) {
                    TKit.assertDirectoryNotEmpty(shortcutDir);
                } else {
                    TKit.assertPathExists(shortcutDir, false);
                }
            });
        }
    }

    private enum ShortcutType {
        COMMON_START_MENU(SpecialFolder.COMMON_START_MENU_PROGRAMS),
        USER_START_MENU(SpecialFolder.USER_START_MENU_PROGRAMS),
        COMMON_DESKTOP(SpecialFolder.COMMON_DESKTOP),
        USER_DESKTOP(SpecialFolder.USER_DESKTOP),
        ;

        ShortcutType(SpecialFolder rootFolder) {
            this.rootFolder = Objects.requireNonNull(rootFolder);
        }

        SpecialFolder rootFolder() {
            return rootFolder;
        }

        private final SpecialFolder rootFolder;
    }

    private static Path resolvePath(Path path, boolean allUsers) {
        var root = path.getName(0);
        var resolvedRoot = SpecialFolder.findMsiProperty(root.toString(), allUsers).orElseThrow().getPath();
        return resolvedRoot.resolve(root.relativize(path));
    }

    private static Shortcut createLauncherShortcutSpec(JPackageCommand cmd, String launcherName,
            SpecialFolder installRoot, Path workDir, ShortcutType type) {

        var name = Optional.ofNullable(launcherName).orElseGet(cmd::name);

        var appLayout = ApplicationLayout.windowsAppImage().resolveAt(
                Path.of(installRoot.getMsiPropertyName()).resolve(getInstallationSubDirectory(cmd)));

        Path path;
        switch (type) {
            case COMMON_START_MENU, USER_START_MENU -> {
                path = Path.of(cmd.getArgumentValue("--win-menu-group", () -> "Unknown"), name);
            }
            default -> {
                path = Path.of(name);
            }
        }

        return new Shortcut(
                Path.of(type.rootFolder().getMsiPropertyName()).resolve(path),
                appLayout.launchersDirectory().resolve(name + ".exe"),
                workDir);
    }

    private static Collection<Shortcut> expectLauncherShortcuts(JPackageCommand cmd,
            Optional<AppImageFile> predefinedAppImage, String launcherName) {
        Objects.requireNonNull(cmd);
        Objects.requireNonNull(predefinedAppImage);

        List<MsiDatabase.Shortcut> shortcuts = new ArrayList<>();

        var name = Optional.ofNullable(launcherName).orElseGet(cmd::name);

        boolean isWinMenu;
        boolean isDesktop;
        if (name.equals(cmd.name())) {
            isWinMenu = cmd.hasArgument("--win-menu");
            isDesktop = cmd.hasArgument("--win-shortcut");
        } else {
            var props = predefinedAppImage.map(v -> {
                return v.launchers().get(name);
            }).map(appImageFileLauncherProps -> {
                Map<String, String> convProps = new HashMap<>();
                for (var e : Map.of("menu", "win-menu", "shortcut", "win-shortcut").entrySet()) {
                    Optional.ofNullable(appImageFileLauncherProps.get(e.getKey())).ifPresent(v -> {
                        convProps.put(e.getValue(), v);
                    });
                }
                return new AdditionalLauncher.PropertyFile(convProps);
            }).orElseGet(() -> {
                return AdditionalLauncher.getAdditionalLauncherProperties(cmd, launcherName);
            });
            isWinMenu = props.findBooleanProperty("win-menu").orElseGet(() -> cmd.hasArgument("--win-menu"));
            isDesktop = props.findBooleanProperty("win-shortcut").orElseGet(() -> cmd.hasArgument("--win-shortcut"));
        }

        var isUserLocalInstall = WindowsHelper.isUserLocalInstall(cmd);

        SpecialFolder installRoot;
        if (isUserLocalInstall) {
            installRoot = SpecialFolder.LOCAL_APPLICATION_DATA;
        } else {
            installRoot = SpecialFolder.PROGRAM_FILES;
        }

        var workDir = Path.of(installRoot.getMsiPropertyName()).resolve(getInstallationSubDirectory(cmd));

        if (isWinMenu) {
            ShortcutType type;
            if (isUserLocalInstall) {
                type = ShortcutType.USER_START_MENU;
            } else {
                type = ShortcutType.COMMON_START_MENU;
            }
            shortcuts.add(createLauncherShortcutSpec(cmd, launcherName, installRoot, workDir, type));
        }

        if (isDesktop) {
            ShortcutType type;
            if (isUserLocalInstall) {
                type = ShortcutType.USER_DESKTOP;
            } else {
                type = ShortcutType.COMMON_DESKTOP;
            }
            shortcuts.add(createLauncherShortcutSpec(cmd, launcherName, installRoot, workDir, type));
        }

        return shortcuts;
    }

    private static Map<String, Collection<Shortcut>> expectShortcuts(JPackageCommand cmd) {
        Map<String, Collection<Shortcut>> expectedShortcuts = new HashMap<>();

        var predefinedAppImage = Optional.ofNullable(cmd.getArgumentValue("--app-image")).map(Path::of).map(AppImageFile::load);

        predefinedAppImage.map(v -> {
            return v.launchers().keySet().stream();
        }).orElseGet(() -> {
            return Stream.concat(Stream.of(cmd.name()), cmd.addLauncherNames().stream());
        }).forEach(launcherName -> {
            var shortcuts = expectLauncherShortcuts(cmd, predefinedAppImage, launcherName);
            if (!shortcuts.isEmpty()) {
                expectedShortcuts.put(launcherName, shortcuts);
            }
        });

        return expectedShortcuts;
    }

        addShortcuts.accept(cmd.name());
        predefinedAppImage.map(v -> {
            return (Collection<String>)v.addLaunchers().keySet();
        }).orElseGet(cmd::addLauncherNames).forEach(addShortcuts);

        return expectedShortcuts;
    }

    private static final Comparator<Shortcut> SHORTCUT_COMPARATOR = Comparator.comparing(Shortcut::target)
            .thenComparing(Comparator.comparing(Shortcut::path))
            .thenComparing(Comparator.comparing(Shortcut::workDir));
}
