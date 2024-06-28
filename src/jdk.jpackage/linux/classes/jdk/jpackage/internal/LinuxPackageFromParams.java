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
import jdk.jpackage.internal.LinuxPackage.Impl;
import static jdk.jpackage.internal.LinuxPackage.mapPackageName;
import jdk.jpackage.internal.Package.StandardPackageType;
import static jdk.jpackage.internal.Package.StandardPackageType.LinuxDeb;
import static jdk.jpackage.internal.Package.StandardPackageType.LinuxRpm;

final class LinuxPackageFromParams {

    static LinuxPackage create(Map<String, ? super Object> params, StandardPackageType pkgType) throws ConfigException {
        var pkg = PackageFromParams.create(params, LinuxApplicationFromParams.APPLICATION, pkgType);
        var menuGroupName = LINUX_MENU_GROUP.fetchFrom(params);
        var category = LINUX_CATEGORY.fetchFrom(params);
        var additionalDependencies = LINUX_PACKAGE_DEPENDENCIES.fetchFrom(params);
        var release = RELEASE.fetchFrom(params);

        var packageName = mapPackageName(pkg.packageName(), pkgType);
        var arch = LinuxPackageArch.getValue(pkgType);

        return new Impl(pkg, menuGroupName, category, additionalDependencies, release) {
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
                        release(), arch));
            }
        };
    }

    private static final BundlerParamInfo<String> LINUX_CATEGORY = new StandardBundlerParam<>(
            Arguments.CLIOptions.LINUX_CATEGORY.getId(),
            String.class,
            params -> "misc",
            (s, p) -> s);

    private static final BundlerParamInfo<String> LINUX_PACKAGE_DEPENDENCIES = new StandardBundlerParam<>(
            Arguments.CLIOptions.LINUX_PACKAGE_DEPENDENCIES.getId(),
            String.class,
            params -> null,
            (s, p) -> s);

    private static final BundlerParamInfo<String> LINUX_MENU_GROUP = new StandardBundlerParam<>(
            Arguments.CLIOptions.LINUX_MENU_GROUP.getId(),
            String.class,
            params -> I18N.getString("param.menu-group.default"),
            (s, p) -> s);

    private static final StandardBundlerParam<String> RELEASE = new StandardBundlerParam<>(
            Arguments.CLIOptions.RELEASE.getId(),
            String.class,
            params -> "1",
            (s, p) -> s);
}
