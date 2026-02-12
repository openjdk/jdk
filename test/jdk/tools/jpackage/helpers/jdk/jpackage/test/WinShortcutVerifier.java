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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static jdk.jpackage.test.LauncherShortcut.WIN_DESKTOP_SHORTCUT;
import static jdk.jpackage.test.LauncherShortcut.WIN_START_MENU_SHORTCUT;
import static jdk.jpackage.test.WindowsHelper.getInstallationSubDirectory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.test.LauncherShortcut.InvokeShortcutSpec;
import jdk.jpackage.test.LauncherShortcut.StartupDirectory;
import jdk.jpackage.test.MsiDatabase.Shortcut;
import jdk.jpackage.test.WindowsHelper.SpecialFolder;


public final class WinShortcutVerifier {

    static void verifyBundleShortcuts(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.WIN_MSI);

        if (Stream.of("--win-menu", "--win-shortcut").noneMatch(cmd::hasArgument) && cmd.addLauncherNames(true).isEmpty()) {
            return;
        }

        var actualShortcuts = WindowsHelper.getMsiShortcuts(cmd).stream().collect(groupingBy(shortcut -> {
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
                    String.format("Check the number of shortcuts of launcher [%s]", name));

            for (int i = 0; i != expectedLauncherShortcuts.size(); i++) {
                TKit.trace(String.format("Verify shortcut #%d of launcher [%s]", i + 1, name));
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

    public static Collection<? extends InvokeShortcutSpec> getInvokeShortcutSpecs(JPackageCommand cmd) {
        return expectShortcuts(cmd).entrySet().stream().map(e -> {
            return e.getValue().stream().map(shortcut -> {
                return convert(cmd, e.getKey(), shortcut);
            });
        }).flatMap(x -> x).toList();
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

        var name = Optional.ofNullable(launcherName).orElseGet(cmd::mainLauncherName);

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

        final List<Shortcut> shortcuts = new ArrayList<>();

        final var winMenu = WIN_START_MENU_SHORTCUT.expectShortcut(cmd, predefinedAppImage, launcherName);
        final var desktop = WIN_DESKTOP_SHORTCUT.expectShortcut(cmd, predefinedAppImage, launcherName);

        final var isUserLocalInstall = WindowsHelper.isUserLocalInstall(cmd);

        final SpecialFolder installRoot;
        if (isUserLocalInstall) {
            installRoot = SpecialFolder.LOCAL_APPLICATION_DATA;
        } else {
            installRoot = SpecialFolder.PROGRAM_FILES;
        }

        final var installDir = Path.of(installRoot.getMsiPropertyName()).resolve(getInstallationSubDirectory(cmd));

        final Function<StartupDirectory, Path> workDir = startupDirectory -> {
            switch (startupDirectory) {
                case DEFAULT -> {
                    return installDir;
                }
                case APP_DIR -> {
                    return ApplicationLayout.windowsAppImage().resolveAt(installDir).appDirectory();
                }
                default -> {
                    throw new IllegalArgumentException();
                }
            }
        };

        if (winMenu.isPresent()) {
            ShortcutType type;
            if (isUserLocalInstall) {
                type = ShortcutType.USER_START_MENU;
            } else {
                type = ShortcutType.COMMON_START_MENU;
            }
            shortcuts.add(createLauncherShortcutSpec(cmd, launcherName, installRoot, winMenu.map(workDir).orElseThrow(), type));
        }

        if (desktop.isPresent()) {
            ShortcutType type;
            if (isUserLocalInstall) {
                type = ShortcutType.USER_DESKTOP;
            } else {
                type = ShortcutType.COMMON_DESKTOP;
            }
            shortcuts.add(createLauncherShortcutSpec(cmd, launcherName, installRoot, desktop.map(workDir).orElseThrow(), type));
        }

        return shortcuts;
    }

    private static Map<String, Collection<Shortcut>> expectShortcuts(JPackageCommand cmd) {

        var predefinedAppImage = Optional.ofNullable(cmd.getArgumentValue("--app-image")).map(Path::of).map(AppImageFile::load);

        return cmd.launcherNames(true).stream().map(launcherName -> {
            return Optional.ofNullable(launcherName).orElseGet(cmd::mainLauncherName);
        }).map(launcherName -> {
            var shortcuts = expectLauncherShortcuts(cmd, predefinedAppImage, launcherName);
            if (shortcuts.isEmpty()) {
                return null;
            } else {
                return Map.entry(launcherName, shortcuts);
            }
        }).filter(Objects::nonNull).collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static InvokeShortcutSpec convert(JPackageCommand cmd, String launcherName, Shortcut shortcut) {
        LauncherShortcut launcherShortcut;
        if (Stream.of(ShortcutType.COMMON_START_MENU, ShortcutType.USER_START_MENU).anyMatch(type -> {
            return shortcut.path().startsWith(Path.of(type.rootFolder().getMsiPropertyName()));
        })) {
            launcherShortcut = WIN_START_MENU_SHORTCUT;
        } else {
            launcherShortcut = WIN_DESKTOP_SHORTCUT;
        }

        var isUserLocalInstall = WindowsHelper.isUserLocalInstall(cmd);
        return new InvokeShortcutSpec.Stub(
                launcherName,
                launcherShortcut,
                Optional.of(resolvePath(shortcut.workDir(), !isUserLocalInstall)),
                List.of("cmd", "/c", "start", "/wait", PathUtils.addSuffix(resolvePath(shortcut.path(), !isUserLocalInstall), ".lnk").toString()));
    }


    private static final Comparator<Shortcut> SHORTCUT_COMPARATOR = Comparator.comparing(Shortcut::target)
            .thenComparing(Comparator.comparing(Shortcut::path))
            .thenComparing(Comparator.comparing(Shortcut::workDir));
}
