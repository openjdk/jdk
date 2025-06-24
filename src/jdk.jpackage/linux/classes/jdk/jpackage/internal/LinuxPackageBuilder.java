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

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import jdk.jpackage.internal.model.AppImageLayout;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.LinuxPackage;
import jdk.jpackage.internal.model.LinuxPackageMixin;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.StandardPackageType;

final class LinuxPackageBuilder {

    LinuxPackageBuilder(PackageBuilder pkgBuilder) {
        this.pkgBuilder = Objects.requireNonNull(pkgBuilder);
    }

    LinuxPackage create() throws ConfigException {
        if (literalName != null) {
            pkgBuilder.name(literalName);
        } else {
            // Lower case and turn spaces/underscores into dashes
            pkgBuilder.name(pkgBuilder.create().packageName().toLowerCase().replaceAll("[ _]", "-"));
        }

        final var pkg = pkgBuilder.create();

        final var stdPkgType = pkg.asStandardPackageType();
        if (stdPkgType.isPresent()) {
            validatePackageName(pkg.packageName(), stdPkgType.orElseThrow());
        }

        var reply = create(pkg, pkg.packageLayout());
        if (reply.isInstallDirInUsrTree()) {
            reply = create(pkg, usrTreePackageLayout(pkg.relativeInstallDir(), pkg.packageName()));
        }

        return reply;
    }

    private LinuxPackage create(Package pkg, AppImageLayout pkgLayout) throws ConfigException {
        return LinuxPackage.create(pkg, new LinuxPackageMixin.Stub(
                pkgLayout,
                Optional.ofNullable(menuGroupName).orElseGet(DEFAULTS::menuGroupName),
                Optional.ofNullable(category),
                Optional.ofNullable(additionalDependencies),
                Optional.ofNullable(release),
                pkg.asStandardPackageType().map(LinuxPackageArch::getValue).orElseThrow()));
    }

    LinuxPackageBuilder literalName(String v) {
        literalName = v;
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

    private static void validatePackageName(String packageName,
            StandardPackageType pkgType) throws ConfigException {
        switch (pkgType) {
            case LINUX_DEB -> {
                //
                // Debian rules for package naming are used here
                // https://www.debian.org/doc/debian-policy/ch-controlfields.html#s-f-Source
                //
                // Package names must consist only of lower case letters (a-z),
                // digits (0-9), plus (+) and minus (-) signs, and periods (.).
                // They must be at least two characters long and
                // must start with an alphanumeric character.
                //
                var regexp = Pattern.compile("^[a-z][a-z\\d\\+\\-\\.]+");
                if (!regexp.matcher(packageName).matches()) {
                    throw buildConfigException()
                            .message("error.deb-invalid-value-for-package-name", packageName)
                            .advice("error.deb-invalid-value-for-package-name.advice")
                            .create();
                }
            }
            case LINUX_RPM -> {
                //
                // Fedora rules for package naming are used here
                // https://fedoraproject.org/wiki/Packaging:NamingGuidelines?rd=Packaging/NamingGuidelines
                //
                // all Fedora packages must be named using only the following ASCII
                // characters. These characters are displayed here:
                //
                // abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._+
                //
                var regexp = Pattern.compile("[a-z\\d\\+\\-\\.\\_]+", Pattern.CASE_INSENSITIVE);
                if (!regexp.matcher(packageName).matches()) {
                    throw buildConfigException()
                            .message("error.rpm-invalid-value-for-package-name", packageName)
                            .advice("error.rpm-invalid-value-for-package-name.advice")
                            .create();
                }
            }
            default -> {
            }
        }
    }

    private record Defaults(String menuGroupName) {
    }

    private String literalName;
    private String menuGroupName;
    private String category;
    private String additionalDependencies;
    private String release;

    private final PackageBuilder pkgBuilder;

    private static final Defaults DEFAULTS = new Defaults(I18N.getString(
            "param.menu-group.default"));

}
