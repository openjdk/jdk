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
import java.text.MessageFormat;
import java.util.Set;
import java.util.regex.Pattern;

interface LinuxPackage extends Package {

    String menuGroupName();

    String category();

    String additionalDependencies();

    String release();

    String arch();

    @Override
    default ApplicationLayout appLayout() {
        if (isInstallDirInUsrTree()) {
            return ApplicationLayout.linuxUsrTreePackageImage(relativeInstallDir(), packageName());
        } else {
            return Package.super.appLayout();
        }
    }

    default boolean isInstallDirInUsrTree() {
        return Set.of(Path.of("usr/local"), Path.of("usr")).contains(relativeInstallDir());
    }

    static class Impl extends Package.Proxy<Package> implements LinuxPackage {

        Impl(Package target, String menuGroupName, String category, String additionalDependencies,
                String release) throws ConfigException {
            this(target, menuGroupName, category, additionalDependencies, release, LinuxPackageArch
                    .getValue(target.asStandardPackageType()));
        }

        Impl(Package target, String menuGroupName, String category,
                String additionalDependencies, String release, String arch) throws ConfigException {
            super(target);
            if (target.type() instanceof StandardPackageType type) {
                packageName = mapPackageName(target.packageName(), type);
            } else {
                packageName = target.packageName();
            }
            this.menuGroupName = menuGroupName;
            this.category = category;
            this.additionalDependencies = additionalDependencies;
            this.release = release;
            this.arch = arch;
        }

        @Override
        public String packageName() {
            return packageName;
        }

        @Override
        public Path packageFileName() {
            String packageFileNameTemlate;
            switch (asStandardPackageType()) {
                case LinuxDeb -> {
                    packageFileNameTemlate = "%s_%s-%s_%s.deb";
                }
                case LinuxRpm -> {
                    packageFileNameTemlate = "%s-%s-%s.%s.rpm";
                }
                default -> {
                    throw new UnsupportedOperationException();
                }
            }

            return Path.of(String.format(packageFileNameTemlate, packageName(), version(),
                    release(), arch()));
        }

        @Override
        public String menuGroupName() {
            return menuGroupName;
        }

        @Override
        public String category() {
            return category;
        }

        @Override
        public String additionalDependencies() {
            return additionalDependencies;
        }

        @Override
        public String release() {
            return release;
        }

        @Override
        public String arch() {
            return arch;
        }

        private final String packageName;

        private final String menuGroupName;
        private final String category;
        private final String additionalDependencies;
        private final String release;
        private final String arch;
    }

    static class Proxy<T extends LinuxPackage> extends Package.Proxy<T> implements LinuxPackage {

        public Proxy(T target) {
            super(target);
        }

        @Override
        public String menuGroupName() {
            return target.menuGroupName();
        }

        @Override
        public String category() {
            return target.category();
        }

        @Override
        public String additionalDependencies() {
            return target.additionalDependencies();
        }

        @Override
        public String release() {
            return target.release();
        }

        @Override
        public String arch() {
            return target.arch();
        }
    }

    private static String mapPackageName(String packageName, StandardPackageType pkgType) throws ConfigException {
        // make sure to lower case and spaces/underscores become dashes
        packageName = packageName.toLowerCase().replaceAll("[ _]", "-");

        switch (pkgType) {
            case LinuxDeb -> {
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
                    throw new ConfigException(MessageFormat.format(I18N.getString(
                            "error.deb-invalid-value-for-package-name"), packageName), I18N
                                    .getString("error.deb-invalid-value-for-package-name.advice"));
                }
            }
            case LinuxRpm -> {
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
                    throw new ConfigException(MessageFormat.format(I18N.getString(
                            "error.rpm-invalid-value-for-package-name"), packageName), I18N
                                    .getString("error.rpm-invalid-value-for-package-name.advice"));
                }
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }

        return packageName;
    }
}
