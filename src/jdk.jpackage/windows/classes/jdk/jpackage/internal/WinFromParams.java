/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.stream.Collectors.toSet;
import static jdk.jpackage.internal.BundlerParamInfo.createBooleanBundlerParam;
import static jdk.jpackage.internal.BundlerParamInfo.createStringBundlerParam;
import static jdk.jpackage.internal.FromParams.createApplicationBuilder;
import static jdk.jpackage.internal.FromParams.createApplicationBundlerParam;
import static jdk.jpackage.internal.FromParams.createPackageBuilder;
import static jdk.jpackage.internal.FromParams.createPackageBundlerParam;
import static jdk.jpackage.internal.StandardBundlerParam.MENU_HINT;
import static jdk.jpackage.internal.StandardBundlerParam.RESOURCE_DIR;
import static jdk.jpackage.internal.StandardBundlerParam.SHORTCUT_HINT;
import static jdk.jpackage.internal.WinPackagingPipeline.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.model.StandardPackageType.WIN_MSI;
import static jdk.jpackage.internal.model.WinLauncherMixin.WinShortcut.WIN_SHORTCUT_DESKTOP;
import static jdk.jpackage.internal.model.WinLauncherMixin.WinShortcut.WIN_SHORTCUT_START_MENU;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.WinApplication;
import jdk.jpackage.internal.model.WinLauncher;
import jdk.jpackage.internal.model.WinLauncherMixin;
import jdk.jpackage.internal.model.WinMsiPackage;

final class WinFromParams {

    private static WinApplication createWinApplication(
            Map<String, ? super Object> params) throws ConfigException, IOException {

        final var launcherFromParams = new LauncherFromParams();

        final var app = createApplicationBuilder(params, toFunction(launcherParams -> {

            final var launcher = launcherFromParams.create(launcherParams);

            final boolean isConsole = CONSOLE_HINT.findIn(launcherParams).orElse(false);

            final var shortcuts = Map.of(WIN_SHORTCUT_DESKTOP, List.of(SHORTCUT_HINT,
                WIN_SHORTCUT_HINT), WIN_SHORTCUT_START_MENU, List.of(MENU_HINT,
                        WIN_MENU_HINT)).entrySet().stream().filter(e -> {

                    final var shortcutParams = e.getValue();

                    return shortcutParams.get(0).findIn(launcherParams).orElseGet(() -> {
                        return shortcutParams.get(1).findIn(launcherParams).orElse(false);
                    });
                }).map(Map.Entry::getKey).collect(toSet());

            return WinLauncher.create(launcher, new WinLauncherMixin.Stub(isConsole, shortcuts));

        }), APPLICATION_LAYOUT).create();

        return WinApplication.create(app);
    }

    private static WinMsiPackage createWinMsiPackage(Map<String, ? super Object> params) throws ConfigException, IOException {

        final var app = APPLICATION.fetchFrom(params);

        final var superPkgBuilder = createPackageBuilder(params, app, WIN_MSI);

        final var pkgBuilder = new WinMsiPackageBuilder(superPkgBuilder);

        HELP_URL.copyInto(params, pkgBuilder::helpURL);
        MSI_SYSTEM_WIDE.copyInto(params, pkgBuilder::isSystemWideInstall);
        MENU_GROUP.copyInto(params, pkgBuilder::startMenuGroupName);
        UPDATE_URL.copyInto(params, pkgBuilder::updateURL);
        INSTALLDIR_CHOOSER.copyInto(params, pkgBuilder::withInstallDirChooser);
        SHORTCUT_PROMPT.copyInto(params, pkgBuilder::withShortcutPrompt);

        if (app.isService()) {
            RESOURCE_DIR.copyInto(params, resourceDir -> {
                pkgBuilder.serviceInstaller(resourceDir.resolve("service-installer.exe"));
            });
        }

        try {
            UPGRADE_UUID.findIn(params).map(UUID::fromString).ifPresent(pkgBuilder::upgradeCode);
        } catch (IllegalArgumentException ex) {
            throw new ConfigException(ex);
        }

        return pkgBuilder.create();
    }

    static final BundlerParamInfo<WinApplication> APPLICATION = createApplicationBundlerParam(
            WinFromParams::createWinApplication);

    static final BundlerParamInfo<WinMsiPackage> MSI_PACKAGE = createPackageBundlerParam(
            WinFromParams::createWinMsiPackage);

    private static final BundlerParamInfo<Boolean> WIN_MENU_HINT = createBooleanBundlerParam(
            Arguments.CLIOptions.WIN_MENU_HINT.getId());

    private static final BundlerParamInfo<Boolean> WIN_SHORTCUT_HINT = createBooleanBundlerParam(
            Arguments.CLIOptions.WIN_SHORTCUT_HINT.getId());

    public static final BundlerParamInfo<Boolean> CONSOLE_HINT = createBooleanBundlerParam(
            Arguments.CLIOptions.WIN_CONSOLE_HINT.getId());

    private static final BundlerParamInfo<Boolean> INSTALLDIR_CHOOSER = new BundlerParamInfo<>(
            Arguments.CLIOptions.WIN_DIR_CHOOSER.getId(),
            Boolean.class,
            null,
            (s, p) -> Boolean.valueOf(s)
    );

    private static final BundlerParamInfo<Boolean> SHORTCUT_PROMPT = new BundlerParamInfo<>(
            Arguments.CLIOptions.WIN_SHORTCUT_PROMPT.getId(),
            Boolean.class,
            null,
            (s, p) -> Boolean.valueOf(s)
    );

    private static final BundlerParamInfo<String> MENU_GROUP = createStringBundlerParam(
            Arguments.CLIOptions.WIN_MENU_GROUP.getId());

    private static final BundlerParamInfo<Boolean> MSI_SYSTEM_WIDE = createBooleanBundlerParam(
            Arguments.CLIOptions.WIN_PER_USER_INSTALLATION.getId());

    private static final BundlerParamInfo<String> HELP_URL = createStringBundlerParam(
            Arguments.CLIOptions.WIN_HELP_URL.getId());

    private static final BundlerParamInfo<String> UPDATE_URL = createStringBundlerParam(
            Arguments.CLIOptions.WIN_UPDATE_URL.getId());

    private static final BundlerParamInfo<String> UPGRADE_UUID = createStringBundlerParam(
            Arguments.CLIOptions.WIN_UPGRADE_UUID.getId());
}
