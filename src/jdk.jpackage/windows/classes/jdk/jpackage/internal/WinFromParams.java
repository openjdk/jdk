/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.jpackage.internal;

import java.io.IOException;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.WinMsiPackage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.BundlerParamInfo.createStringBundlerParam;
import static jdk.jpackage.internal.FromParams.createApplicationBuilder;
import static jdk.jpackage.internal.FromParams.createApplicationBundlerParam;
import static jdk.jpackage.internal.FromParams.createPackageBuilder;
import static jdk.jpackage.internal.FromParams.createPackageBundlerParam;
import static jdk.jpackage.internal.StandardBundlerParam.MENU_HINT;
import static jdk.jpackage.internal.StandardBundlerParam.RESOURCE_DIR;
import static jdk.jpackage.internal.StandardBundlerParam.SHORTCUT_HINT;
import static jdk.jpackage.internal.WinAppImageBuilder.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.model.StandardPackageType.WIN_MSI;
import jdk.jpackage.internal.model.WinApplication;
import jdk.jpackage.internal.model.WinLauncher;
import static jdk.jpackage.internal.model.WinLauncherMixin.WinShortcut.WIN_SHORTCUT_DESKTOP;
import static jdk.jpackage.internal.model.WinLauncherMixin.WinShortcut.WIN_SHORTCUT_START_MENU;
import jdk.jpackage.internal.model.WinLauncherMixin;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

final class WinFromParams {

    private static WinApplication createWinApplication(
            Map<String, ? super Object> params) throws ConfigException, IOException {
        var launcherFromParams = new LauncherFromParams();
        var app = createApplicationBuilder(params, toFunction(launcherParams -> {
            var launcher = launcherFromParams.create(launcherParams);

            boolean isConsole = CONSOLE_HINT.fetchFrom(launcherParams);

            var shortcuts = Map.of(WIN_SHORTCUT_DESKTOP, List.of(SHORTCUT_HINT,
                WIN_SHORTCUT_HINT), WIN_SHORTCUT_START_MENU, List.of(MENU_HINT,
                        WIN_MENU_HINT)).entrySet().stream().filter(e -> {
                    var shortcutParams = e.getValue();
                    if (launcherParams.containsKey(shortcutParams.get(0).getID())) {
                        // This is an explicit shortcut configuration for an addition launcher
                        return shortcutParams.get(0).fetchFrom(launcherParams);
                    } else {
                        return shortcutParams.get(1).fetchFrom(launcherParams);
                    }
                }).map(Map.Entry::getKey).collect(toSet());

            return WinLauncher.create(launcher, new WinLauncherMixin.Stub(isConsole, shortcuts));
        }), APPLICATION_LAYOUT).create();
        return WinApplication.create(app);
    }

    private static WinMsiPackage createWinMsiPackage(Map<String, ? super Object> params) throws ConfigException, IOException {

        var app = APPLICATION.fetchFrom(params);

        var pkgBuilder = createPackageBuilder(params, app, WIN_MSI);

        final Path serviceInstaller;
        if (!app.isService()) {
            serviceInstaller = null;
        } else {
            serviceInstaller = Optional.ofNullable(RESOURCE_DIR.fetchFrom(params)).map(
                    resourceDir -> {
                        return resourceDir.resolve("service-installer.exe");
                    }).orElse(null);
        }

        return new WinMsiPackageBuilder(pkgBuilder)
                .helpURL(HELP_URL.fetchFrom(params))
                .isSystemWideInstall(MSI_SYSTEM_WIDE.fetchFrom(params))
                .serviceInstaller(serviceInstaller)
                .startMenuGroupName(MENU_GROUP.fetchFrom(params))
                .updateURL(UPDATE_URL.fetchFrom(params))
                .upgradeCode(getUpgradeCode(params))
                .withInstallDirChooser(INSTALLDIR_CHOOSER.fetchFrom(params))
                .withShortcutPrompt(SHORTCUT_PROMPT.fetchFrom(params))
                .create();
    }

    private static UUID getUpgradeCode(Map<String, ? super Object> params) throws ConfigException {
        try {
            return Optional.ofNullable(UPGRADE_UUID.fetchFrom(params)).map(UUID::fromString).orElse(
                    null);
        } catch (IllegalArgumentException ex) {
            throw new ConfigException(ex);
        }
    }

    static final BundlerParamInfo<WinApplication> APPLICATION = createApplicationBundlerParam(
            WinFromParams::createWinApplication);

    static final BundlerParamInfo<WinMsiPackage> MSI_PACKAGE = createPackageBundlerParam(
            WinFromParams::createWinMsiPackage);

    private static final BundlerParamInfo<Boolean> WIN_MENU_HINT = new BundlerParamInfo<>(
            Arguments.CLIOptions.WIN_MENU_HINT.getId(),
            Boolean.class,
            p -> false,
            // valueOf(null) is false,
            // and we actually do want null in some cases
            (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? false : Boolean.valueOf(s));

    private static final BundlerParamInfo<Boolean> WIN_SHORTCUT_HINT = new BundlerParamInfo<>(
            Arguments.CLIOptions.WIN_SHORTCUT_HINT.getId(),
            Boolean.class,
            p -> false,
            // valueOf(null) is false,
            // and we actually do want null in some cases
            (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? false : Boolean.valueOf(s));

    public static final BundlerParamInfo<Boolean> CONSOLE_HINT = new BundlerParamInfo<>(
            Arguments.CLIOptions.WIN_CONSOLE_HINT.getId(),
            Boolean.class,
            params -> false,
            // valueOf(null) is false,
            // and we actually do want null in some cases
            (s, p) -> (s == null
            || "null".equalsIgnoreCase(s)) ? true : Boolean.valueOf(s));

    private static final BundlerParamInfo<Boolean> INSTALLDIR_CHOOSER = new BundlerParamInfo<>(
            Arguments.CLIOptions.WIN_DIR_CHOOSER.getId(),
            Boolean.class,
            params -> false,
            (s, p) -> Boolean.valueOf(s)
    );

    private static final BundlerParamInfo<Boolean> SHORTCUT_PROMPT = new BundlerParamInfo<>(
            Arguments.CLIOptions.WIN_SHORTCUT_PROMPT.getId(),
            Boolean.class,
            params -> false,
            (s, p) -> Boolean.valueOf(s)
    );

    private static final BundlerParamInfo<String> MENU_GROUP = new BundlerParamInfo<>(
            Arguments.CLIOptions.WIN_MENU_GROUP.getId(),
            String.class,
            params -> I18N.getString("param.menu-group.default"),
            (s, p) -> s
    );

    private static final BundlerParamInfo<Boolean> MSI_SYSTEM_WIDE = new BundlerParamInfo<>(
            Arguments.CLIOptions.WIN_PER_USER_INSTALLATION.getId(),
            Boolean.class,
            params -> true, // MSIs default to system wide
            // valueOf(null) is false,
            // and we actually do want null
            (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? null
            : Boolean.valueOf(s)
    );

    private static final BundlerParamInfo<String> HELP_URL = createStringBundlerParam(
            Arguments.CLIOptions.WIN_HELP_URL.getId());

    private static final BundlerParamInfo<String> UPDATE_URL = createStringBundlerParam(
            Arguments.CLIOptions.WIN_UPDATE_URL.getId());

    private static final BundlerParamInfo<String> UPGRADE_UUID = createStringBundlerParam(
            Arguments.CLIOptions.WIN_UPGRADE_UUID.getId());
}
