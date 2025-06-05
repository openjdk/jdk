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

import static jdk.jpackage.internal.I18N.buildConfigException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.MsiVersion;
import jdk.jpackage.internal.model.WinMsiPackage;
import jdk.jpackage.internal.model.WinMsiPackageMixin;

final class WinMsiPackageBuilder {

    WinMsiPackageBuilder(PackageBuilder pkgBuilder) {
        this.pkgBuilder = Objects.requireNonNull(pkgBuilder);
    }

    WinMsiPackage create() throws ConfigException {
        var pkg = pkgBuilder.create();

        try {
            MsiVersion.of(pkg.version());
        } catch (IllegalArgumentException ex) {
            throw buildConfigException(ex)
                    .advice("error.version-string-wrong-format.advice")
                    .create();
        }

        if (pkg.app().isService() && (serviceInstaller == null || !Files.exists(serviceInstaller))) {
            throw buildConfigException()
                    .message("error.missing-service-installer")
                    .advice("error.missing-service-installer.advice")
                    .create();
        }

        return WinMsiPackage.create(pkg, new WinMsiPackageMixin.Stub(
                MsiVersion.of(pkg.version()),
                withInstallDirChooser,
                withShortcutPrompt,
                Optional.ofNullable(helpURL),
                Optional.ofNullable(updateURL),
                Optional.ofNullable(startMenuGroupName).orElseGet(DEFAULTS::startMenuGroupName),
                isSystemWideInstall,
                Optional.ofNullable(upgradeCode).orElseGet(() -> upgradeCode(pkg.app())),
                productCode(pkg.app(), pkg.version()),
                Optional.ofNullable(serviceInstaller)));
    }

    private static UUID upgradeCode(Application app) {
        return createNameUUID("UpgradeCode", app.vendor(), app.name());
    }

    private static UUID productCode(Application app, String pkgVersion) {
        return createNameUUID("ProductCode", app.vendor(), app.name(), pkgVersion);
    }

    WinMsiPackageBuilder withInstallDirChooser(boolean v) {
        withInstallDirChooser = v;
        return this;
    }

    WinMsiPackageBuilder withShortcutPrompt(boolean v) {
        withShortcutPrompt = v;
        return this;
    }

    WinMsiPackageBuilder helpURL(String v) {
        helpURL = v;
        return this;
    }

    WinMsiPackageBuilder updateURL(String v) {
        updateURL = v;
        return this;
    }

    WinMsiPackageBuilder startMenuGroupName(String v) {
        startMenuGroupName = v;
        return this;
    }

    WinMsiPackageBuilder isSystemWideInstall(boolean v) {
        isSystemWideInstall = v;
        return this;
    }

    WinMsiPackageBuilder serviceInstaller(Path v) {
        serviceInstaller = v;
        return this;
    }

    WinMsiPackageBuilder upgradeCode(UUID v) {
        upgradeCode = v;
        return this;
    }

    private static UUID createNameUUID(String... components) {
        String key = String.join("/", components);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    record Defaults(String startMenuGroupName) {}

    private boolean withInstallDirChooser;
    private boolean withShortcutPrompt;
    private String helpURL;
    private String updateURL;
    private String startMenuGroupName;
    private boolean isSystemWideInstall = true;
    private Path serviceInstaller;
    private UUID upgradeCode;

    private final PackageBuilder pkgBuilder;

    private static final Defaults DEFAULTS = new Defaults(I18N.getString("param.menu-group.default"));
}
