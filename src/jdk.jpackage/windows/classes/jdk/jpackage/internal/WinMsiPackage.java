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
import java.util.Optional;
import java.util.UUID;

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

    static class Impl extends Package.Proxy<Package> implements WinMsiPackage {

        Impl(Package pkg, boolean withInstallDirChooser, boolean withShortcutPrompt, String helpURL,
                String updateURL, String startMenuGroupName, boolean isSystemWideInstall,
                UUID upgradeCode, Path serviceInstaller) throws ConfigException {
            super(pkg);

            try {
                MsiVersion.of(pkg.version());
            } catch (IllegalArgumentException ex) {
                throw new ConfigException(ex.getMessage(), I18N.getString(
                        "error.version-string-wrong-format.advice"), ex);
            }

            if (pkg.app().isService() && serviceInstaller == null || !Files.exists(serviceInstaller)) {
                throw new ConfigException(I18N.getString("error.missing-service-installer"), I18N
                        .getString("error.missing-service-installer.advice"));
            }

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
            return Optional.ofNullable(upgradeCode).orElseGet(WinMsiPackage.super::upgradeCode);
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

    private static UUID createNameUUID(String... components) {
        String key = String.join("/", components);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
