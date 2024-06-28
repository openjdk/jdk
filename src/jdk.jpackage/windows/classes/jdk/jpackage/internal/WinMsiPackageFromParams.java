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

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static jdk.jpackage.internal.Package.StandardPackageType.WinMsi;
import static jdk.jpackage.internal.PackageFromParams.createBundlerParam;
import static jdk.jpackage.internal.StandardBundlerParam.RESOURCE_DIR;
import jdk.jpackage.internal.WinMsiPackage.Impl;

final class WinMsiPackageFromParams {

    private static WinMsiPackage create(Map<String, ? super Object> params) throws ConfigException {
        var pkg = PackageFromParams.create(params, WinApplicationFromParams.APPLICATION, WinMsi);
        var withInstallDirChooser = INSTALLDIR_CHOOSER.fetchFrom(params);
        var withShortcutPrompt = SHORTCUT_PROMPT.fetchFrom(params);
        var helpURL = HELP_URL.fetchFrom(params);
        var updateURL = UPDATE_URL.fetchFrom(params);
        var startMenuGroupName = MENU_GROUP.fetchFrom(params);
        var isSystemWideInstall = MSI_SYSTEM_WIDE.fetchFrom(params);
        var upgradeCode = getUpgradeCode(params);

        final Path serviceInstaller;
        if (!pkg.app().isService()) {
            serviceInstaller = null;
        } else {
            serviceInstaller = Optional.ofNullable(RESOURCE_DIR.fetchFrom(params)).map(
                    resourceDir -> {
                        return resourceDir.resolve("service-installer.exe");
                    }).orElse(null);
        }

        return new Impl(pkg, withInstallDirChooser, withShortcutPrompt, helpURL, updateURL,
                startMenuGroupName, isSystemWideInstall, upgradeCode, serviceInstaller);
    }

    private static UUID getUpgradeCode(Map<String, ? super Object> params) throws ConfigException {
        try {
            return Optional.ofNullable(UPGRADE_UUID.fetchFrom(params)).map(UUID::fromString).orElse(
                    null);
        } catch (IllegalArgumentException ex) {
            throw new ConfigException(ex);
        }
    }

    static final StandardBundlerParam<WinMsiPackage> PACKAGE = createBundlerParam(
            WinMsiPackageFromParams::create);

    private static final BundlerParamInfo<Boolean> INSTALLDIR_CHOOSER = new StandardBundlerParam<>(
            Arguments.CLIOptions.WIN_DIR_CHOOSER.getId(),
            Boolean.class,
            params -> false,
            (s, p) -> Boolean.valueOf(s)
    );

    private static final StandardBundlerParam<Boolean> SHORTCUT_PROMPT = new StandardBundlerParam<>(
            Arguments.CLIOptions.WIN_SHORTCUT_PROMPT.getId(),
            Boolean.class,
            params -> false,
            (s, p) -> Boolean.valueOf(s)
    );

    private static final StandardBundlerParam<String> MENU_GROUP = new StandardBundlerParam<>(
            Arguments.CLIOptions.WIN_MENU_GROUP.getId(),
            String.class,
            params -> I18N.getString("param.menu-group.default"),
            (s, p) -> s
    );

    private static final StandardBundlerParam<Boolean> MSI_SYSTEM_WIDE = new StandardBundlerParam<>(
            Arguments.CLIOptions.WIN_PER_USER_INSTALLATION.getId(),
            Boolean.class,
            params -> true, // MSIs default to system wide
            // valueOf(null) is false,
            // and we actually do want null
            (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? null
            : Boolean.valueOf(s)
    );

    private static final BundlerParamInfo<String> HELP_URL = new StandardBundlerParam<>(
            Arguments.CLIOptions.WIN_HELP_URL.getId(),
            String.class,
            null,
            (s, p) -> s);

    private static final BundlerParamInfo<String> UPDATE_URL = new StandardBundlerParam<>(
            Arguments.CLIOptions.WIN_UPDATE_URL.getId(),
            String.class,
            null,
            (s, p) -> s);

    private static final BundlerParamInfo<String> UPGRADE_UUID = new StandardBundlerParam<>(
            Arguments.CLIOptions.WIN_UPGRADE_UUID.getId(),
            String.class,
            null,
            (s, p) -> s);
}
