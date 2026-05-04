/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.cli.StandardValidator.IS_LINUX_DEB_PACKAGE_NAME;
import static jdk.jpackage.internal.cli.StandardValidator.IS_LINUX_RPM_PACKAGE_NAME;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.LinuxApplication;
import jdk.jpackage.internal.model.LinuxPackage;
import jdk.jpackage.internal.model.LinuxPackageMixin;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.RuntimeLayout;
import jdk.jpackage.internal.model.StandardPackageType;

final class LinuxPackageBuilder {

    LinuxPackageBuilder(PackageBuilder pkgBuilder) {
        this.pkgBuilder = Objects.requireNonNull(pkgBuilder);
    }

    LinuxPackage create() {
        pkgBuilder.name(Optional.ofNullable(linuxPackageName).orElseGet(() -> {
            // Lower case and turn spaces/underscores into hyphens
            return pkgBuilder.create().packageName().toLowerCase().replaceAll("[ _]", "-");
        }));

        final var tmpPkg = pkgBuilder.create();

        tmpPkg.asStandardPackageType().ifPresent(stdPkgType -> {
            validateDerivedPackageName(tmpPkg.packageName(), stdPkgType);
        });

        final AppImageLayout relativeInstalledLayout;
        if (create(tmpPkg).isInstallDirInUsrTree()) {
            final var usrTreeLayout = usrTreePackageLayout(tmpPkg.relativeInstallDir(), tmpPkg.packageName());
            if (tmpPkg.isRuntimeInstaller()) {
                relativeInstalledLayout = RuntimeLayout.create(usrTreeLayout.runtimeDirectory());
            } else {
                relativeInstalledLayout = usrTreeLayout;
            }
        } else {
            relativeInstalledLayout = tmpPkg.appImageLayout().resolveAt(tmpPkg.relativeInstallDir()).resetRootDirectory();
        }

        final var app = ApplicationBuilder.overrideAppImageLayout(pkgBuilder.app(), relativeInstalledLayout);

        return create(pkgBuilder
                .app(LinuxApplication.create(app))
                .installedPackageLayout(relativeInstalledLayout.resolveAt(Path.of("/")).resetRootDirectory())
                .create());
    }

    private LinuxPackage create(Package pkg) {
        return LinuxPackage.create(pkg, new LinuxPackageMixin.Stub(
                Optional.ofNullable(menuGroupName).orElseGet(DEFAULTS::menuGroupName),
                category(),
                Optional.ofNullable(additionalDependencies),
                release(),
                arch.value()));
    }

    LinuxPackageBuilder linuxPackageName(String v) {
        linuxPackageName = v;
        return this;
    }

    LinuxPackageBuilder menuGroupName(String v) {
        menuGroupName = v;
        return this;
    }

    LinuxPackageBuilder category(String v) {
        category = v;
        return this;
    }

    Optional<String> category() {
        return Optional.ofNullable(category);
    }

    LinuxPackageBuilder additionalDependencies(String v) {
        additionalDependencies = v;
        return this;
    }

    LinuxPackageBuilder release(String v) {
        release = v;
        return this;
    }

    Optional<String> release() {
        return Optional.ofNullable(release);
    }

    LinuxPackageBuilder arch(LinuxPackageArch v) {
        arch = v;
        return this;
    }

    private static LinuxApplicationLayout usrTreePackageLayout(Path prefix, String packageName) {
        final var lib = prefix.resolve(Path.of("lib", packageName));
        return LinuxApplicationLayout.create(
                ApplicationLayout.build()
                        .launchersDirectory(prefix.resolve("bin"))
                        .appDirectory(lib.resolve("app"))
                        .runtimeDirectory(lib.resolve("runtime"))
                        .desktopIntegrationDirectory(lib)
                        .appModsDirectory(lib.resolve("app/mods"))
                        .contentDirectory(lib)
                        .create(),
                lib.resolve("lib/libapplauncher.so"));
    }

    private static void validateDerivedPackageName(String packageName, StandardPackageType pkgType) {
        switch (pkgType) {
            case LINUX_DEB -> {
                if (!IS_LINUX_DEB_PACKAGE_NAME.test(packageName)) {
                    throw new ConfigException(
                            I18N.format("error.invalid-derived-deb-package-name", packageName),
                            I18N.getString("error.invalid-derived-deb-package-name.advice"));
                }
            }
            case LINUX_RPM -> {
                if (!IS_LINUX_RPM_PACKAGE_NAME.test(packageName)) {
                    throw new ConfigException(
                            I18N.format("error.invalid-derived-rpm-package-name", packageName),
                            I18N.getString("error.invalid-derived-rpm-package-name.advice"));
                }
            }
            default -> {
                throw new AssertionError();
            }
        };
    }

    private record Defaults(String menuGroupName) {
    }

    private String linuxPackageName;
    private String menuGroupName;
    private String category;
    private String additionalDependencies;
    private String release;
    private LinuxPackageArch arch;

    private final PackageBuilder pkgBuilder;

    // Should be one of https://specifications.freedesktop.org/menu/latest/category-registry.html#main-category-registry
    // The category is an ID, not a localizable string
    private static final Defaults DEFAULTS = new Defaults("Utility");
}
