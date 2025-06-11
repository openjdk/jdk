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

import static jdk.jpackage.internal.BundlerParamInfo.createStringBundlerParam;
import static jdk.jpackage.internal.FromParams.createApplicationBuilder;
import static jdk.jpackage.internal.FromParams.createApplicationBundlerParam;
import static jdk.jpackage.internal.FromParams.createPackageBuilder;
import static jdk.jpackage.internal.FromParams.createPackageBundlerParam;
import static jdk.jpackage.internal.LinuxPackagingPipeline.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.StandardBundlerParam.SHORTCUT_HINT;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_DEB;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_RPM;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.LinuxApplication;
import jdk.jpackage.internal.model.LinuxLauncher;
import jdk.jpackage.internal.model.LinuxLauncherMixin;
import jdk.jpackage.internal.model.LinuxPackage;
import jdk.jpackage.internal.model.StandardPackageType;

final class LinuxFromParams {

    private static LinuxApplication createLinuxApplication(
            Map<String, ? super Object> params) throws ConfigException, IOException {
        final var launcherFromParams = new LauncherFromParams();
        final var app = createApplicationBuilder(params, toFunction(launcherParams -> {
            final var launcher = launcherFromParams.create(launcherParams);
            final var shortcut = Stream.of(SHORTCUT_HINT, LINUX_SHORTCUT_HINT).map(param -> {
                return param.findIn(launcherParams);
            }).filter(Optional::isPresent).map(Optional::get).findFirst();
            return LinuxLauncher.create(launcher, new LinuxLauncherMixin.Stub(shortcut));
        }), APPLICATION_LAYOUT).create();
        return LinuxApplication.create(app);
    }

    private static LinuxPackageBuilder createLinuxPackageBuilder(
            Map<String, ? super Object> params, StandardPackageType type) throws ConfigException, IOException {

        final var app = APPLICATION.fetchFrom(params);

        final var superPkgBuilder = createPackageBuilder(params, app, type);

        final var pkgBuilder = new LinuxPackageBuilder(superPkgBuilder);

        LINUX_PACKAGE_DEPENDENCIES.copyInto(params, pkgBuilder::additionalDependencies);
        LINUX_CATEGORY.copyInto(params, pkgBuilder::category);
        LINUX_MENU_GROUP.copyInto(params, pkgBuilder::menuGroupName);
        RELEASE.copyInto(params, pkgBuilder::release);
        LINUX_PACKAGE_NAME.copyInto(params, pkgBuilder::literalName);

        return pkgBuilder;
    }

    private static LinuxPackage createLinuxRpmPackage(
            Map<String, ? super Object> params) throws ConfigException, IOException {

        final var superPkgBuilder = createLinuxPackageBuilder(params, LINUX_RPM);

        final var pkgBuilder = new LinuxRpmPackageBuilder(superPkgBuilder);

        LICENSE_TYPE.copyInto(params, pkgBuilder::licenseType);

        return pkgBuilder.create();
    }

    private static LinuxPackage createLinuxDebPackage(
            Map<String, ? super Object> params) throws ConfigException, IOException {

        final var superPkgBuilder = createLinuxPackageBuilder(params, LINUX_DEB);

        final var pkgBuilder = new LinuxDebPackageBuilder(superPkgBuilder);

        MAINTAINER_EMAIL.copyInto(params, pkgBuilder::maintainerEmail);

        return pkgBuilder.create();
    }

    static final BundlerParamInfo<LinuxApplication> APPLICATION = createApplicationBundlerParam(
            LinuxFromParams::createLinuxApplication);

    static final BundlerParamInfo<LinuxPackage> RPM_PACKAGE = createPackageBundlerParam(
            LinuxFromParams::createLinuxRpmPackage);

    static final BundlerParamInfo<LinuxPackage> DEB_PACKAGE = createPackageBundlerParam(
            LinuxFromParams::createLinuxDebPackage);

    private static final BundlerParamInfo<Boolean> LINUX_SHORTCUT_HINT = new BundlerParamInfo<>(
            Arguments.CLIOptions.LINUX_SHORTCUT_HINT.getId(),
            Boolean.class,
            params -> false,
            (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ? false : Boolean.valueOf(s)
    );

    private static final BundlerParamInfo<String> LINUX_CATEGORY = createStringBundlerParam(
            Arguments.CLIOptions.LINUX_CATEGORY.getId());

    private static final BundlerParamInfo<String> LINUX_PACKAGE_DEPENDENCIES = createStringBundlerParam(
            Arguments.CLIOptions.LINUX_PACKAGE_DEPENDENCIES.getId());

    private static final BundlerParamInfo<String> LINUX_MENU_GROUP = createStringBundlerParam(
            Arguments.CLIOptions.LINUX_MENU_GROUP.getId());

    private static final BundlerParamInfo<String> RELEASE = createStringBundlerParam(
            Arguments.CLIOptions.RELEASE.getId());

    private static final BundlerParamInfo<String> LINUX_PACKAGE_NAME = createStringBundlerParam(
            Arguments.CLIOptions.LINUX_BUNDLE_NAME.getId());

    private static final BundlerParamInfo<String> LICENSE_TYPE = createStringBundlerParam(
            Arguments.CLIOptions.LINUX_RPM_LICENSE_TYPE.getId());

    private static final BundlerParamInfo<String> MAINTAINER_EMAIL = createStringBundlerParam(
            Arguments.CLIOptions.LINUX_DEB_MAINTAINER.getId());
}
