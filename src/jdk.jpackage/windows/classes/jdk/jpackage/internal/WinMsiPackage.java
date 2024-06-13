
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static jdk.jpackage.internal.Functional.ThrowingFunction.toFunction;
import static jdk.jpackage.internal.StandardBundlerParam.RESOURCE_DIR;

interface WinMsiPackage extends Package {

    boolean withInstallDirChooser();

    boolean withShortcutPrompt();

    String helpURL();

    String updateURL();

    String startMenuGroupName();

    boolean isSystemWideInstall();

    default UUID upgradeCode() {
        return createNameUUID("UpgradeCode", app().vendor(), app().name());
    }

    default UUID productCode() {
        return createNameUUID("ProductCode", app().vendor(), app().name(), version());
    }

    Path serviceInstaller();

    static class Impl extends Package.Proxy implements WinMsiPackage {

        Impl(Package pkg, boolean withInstallDirChooser, boolean withShortcutPrompt, String helpURL,
                String updateURL, String startMenuGroupName, boolean isSystemWideInstall,
                UUID upgradeCode, Path serviceInstaller) {
            super(pkg);
            this.withInstallDirChooser = withInstallDirChooser;
            this.withShortcutPrompt = withShortcutPrompt;
            this.helpURL = helpURL;
            this.updateURL = updateURL;
            this.startMenuGroupName = startMenuGroupName;
            this.isSystemWideInstall = isSystemWideInstall;
            this.upgradeCode = upgradeCode;
            this.serviceInstaller = serviceInstaller;
        }

        @Override
        public boolean withInstallDirChooser() {
            return withInstallDirChooser;
        }

        @Override
        public boolean withShortcutPrompt() {
            return withShortcutPrompt;
        }

        @Override
        public String helpURL() {
            return helpURL;
        }

        @Override
        public String updateURL() {
            return updateURL;
        }

        @Override
        public String startMenuGroupName() {
            return startMenuGroupName;
        }

        @Override
        public boolean isSystemWideInstall() {
            return isSystemWideInstall;
        }

        @Override
        public UUID upgradeCode() {
            if (upgradeCode == null) {
                return WinMsiPackage.super.upgradeCode();
            } else {
                return upgradeCode;
            }
        }

        @Override
        public Path serviceInstaller() {
            return serviceInstaller;
        }

        private final boolean withInstallDirChooser;
        private final boolean withShortcutPrompt;
        private final String helpURL;
        private final String updateURL;
        private final String startMenuGroupName;
        private final boolean isSystemWideInstall;
        private final UUID upgradeCode;
        private final Path serviceInstaller;
    }

    private static WinMsiPackage createFromParams(Map<String, ? super Object> params) throws ConfigException {
        var pkg = Package.createFromParams(params, WinApplication.createFromParams(params),
                PackageType.WinMsi);
        var withInstallDirChooser = Internal.INSTALLDIR_CHOOSER.fetchFrom(params);
        var withShortcutPrompt = Internal.SHORTCUT_PROMPT.fetchFrom(params);
        var helpURL = Internal.HELP_URL.fetchFrom(params);
        var updateURL = Internal.UPDATE_URL.fetchFrom(params);
        var startMenuGroupName = Internal.MENU_GROUP.fetchFrom(params);
        var isSystemWideInstall = Internal.MSI_SYSTEM_WIDE.fetchFrom(params);
        var upgradeCode = getUpgradeCode(params);

        try {
            MsiVersion.of(pkg.version());
        } catch (IllegalArgumentException ex) {
            throw new ConfigException(ex.getMessage(), I18N.getString(
                    "error.version-string-wrong-format.advice"), ex);
        }

        Path serviceInstaller = null;
        if (!pkg.app().isService()) {
            serviceInstaller = null;
        } else {
            Path resourceDir = RESOURCE_DIR.fetchFrom(params);
            if (resourceDir != null) {
                serviceInstaller = resourceDir.resolve("service-installer.exe");
                if (!Files.exists(serviceInstaller)) {
                    throw new ConfigException(I18N.getString(
                            "error.missing-service-installer"), I18N.getString(
                                    "error.missing-service-installer.advice"));
                }
            }
        }

        return new Impl(pkg, withInstallDirChooser, withShortcutPrompt, helpURL, updateURL,
                startMenuGroupName, isSystemWideInstall, upgradeCode, serviceInstaller);
    }

    private static UUID getUpgradeCode(Map<String, ? super Object> params) throws ConfigException {
        try {
            return Optional.ofNullable(Internal.UPGRADE_UUID.fetchFrom(params))
                    .map(UUID::fromString).orElse(
                    null);
        } catch (IllegalArgumentException ex) {
            throw new ConfigException(ex);
        }
    }

    private static UUID createNameUUID(String... components) {
        String key = String.join("/", components);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    final static class Internal {

        private static final BundlerParamInfo<Boolean> INSTALLDIR_CHOOSER
                = new StandardBundlerParam<>(
                        Arguments.CLIOptions.WIN_DIR_CHOOSER.getId(),
                        Boolean.class,
                        params -> false,
                        (s, p) -> Boolean.valueOf(s)
                );

        private static final StandardBundlerParam<Boolean> SHORTCUT_PROMPT
                = new StandardBundlerParam<>(
                        Arguments.CLIOptions.WIN_SHORTCUT_PROMPT.getId(),
                        Boolean.class,
                        params -> false,
                        (s, p) -> Boolean.valueOf(s)
                );

        private static final StandardBundlerParam<String> MENU_GROUP
                = new StandardBundlerParam<>(
                        Arguments.CLIOptions.WIN_MENU_GROUP.getId(),
                        String.class,
                        params -> I18N.getString("param.menu-group.default"),
                        (s, p) -> s
                );

        private static final StandardBundlerParam<Boolean> MSI_SYSTEM_WIDE
                = new StandardBundlerParam<>(
                        Arguments.CLIOptions.WIN_PER_USER_INSTALLATION.getId(),
                        Boolean.class,
                        params -> true, // MSIs default to system wide
                        // valueOf(null) is false,
                        // and we actually do want null
                        (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? null
                        : Boolean.valueOf(s)
                );

        private static final BundlerParamInfo<String> HELP_URL
                = new StandardBundlerParam<>(
                        Arguments.CLIOptions.WIN_HELP_URL.getId(),
                        String.class,
                        null,
                        (s, p) -> s);

        private static final BundlerParamInfo<String> UPDATE_URL
                = new StandardBundlerParam<>(
                        Arguments.CLIOptions.WIN_UPDATE_URL.getId(),
                        String.class,
                        null,
                        (s, p) -> s);

        private static final BundlerParamInfo<String> UPGRADE_UUID
                = new StandardBundlerParam<>(
                        Arguments.CLIOptions.WIN_UPGRADE_UUID.getId(),
                        String.class,
                        null,
                        (s, p) -> s);
    }

    static final StandardBundlerParam<WinMsiPackage> TARGET_PACKAGE = new StandardBundlerParam<>(
            Package.PARAM_ID, WinMsiPackage.class, params -> {
                return toFunction(WinMsiPackage::createFromParams).apply(params);
            }, null);
}
