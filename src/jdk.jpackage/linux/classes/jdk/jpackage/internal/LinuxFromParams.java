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

import java.io.IOException;
import jdk.jpackage.internal.model.ConfigException;
import java.util.Map;
import java.util.stream.Stream;
import static jdk.jpackage.internal.BundlerParamInfo.createStringBundlerParam;
import static jdk.jpackage.internal.FromParams.createApplicationBuilder;
import static jdk.jpackage.internal.FromParams.createApplicationBundlerParam;
import static jdk.jpackage.internal.FromParams.createPackageBuilder;
import static jdk.jpackage.internal.FromParams.createPackageBundlerParam;
import static jdk.jpackage.internal.LinuxAppImageBuilder.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.StandardBundlerParam.SHORTCUT_HINT;
import jdk.jpackage.internal.model.LinuxApplication;
import jdk.jpackage.internal.model.LinuxLauncher;
import jdk.jpackage.internal.model.LinuxLauncherMixin;
import jdk.jpackage.internal.model.LinuxPackage;
import jdk.jpackage.internal.model.StandardPackageType;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_DEB;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_RPM;
import static jdk.jpackage.internal.util.function.ThrowingFunction.toFunction;

final class LinuxFromParams {

    private static LinuxApplication createLinuxApplication(
            Map<String, ? super Object> params) throws ConfigException, IOException {
        var launcherFromParams = new LauncherFromParams();
        var app = createApplicationBuilder(params, toFunction(launcherParams -> {
            var launcher = launcherFromParams.create(launcherParams);
            var shortcut = Stream.of(SHORTCUT_HINT, LINUX_SHORTCUT_HINT).filter(param -> {
                return launcherParams.containsKey(param.getID());
            }).map(param -> {
                return param.fetchFrom(launcherParams);
            }).findFirst();
            return LinuxLauncher.create(launcher, new LinuxLauncherMixin.Stub(shortcut));
        }), APPLICATION_LAYOUT).create();
        return LinuxApplication.create(app);
    }

    private static LinuxPackageBuilder createLinuxPackageBuilder(
            Map<String, ? super Object> params, StandardPackageType type) throws ConfigException, IOException {

        var app = APPLICATION.fetchFrom(params);

        var pkgBuilder = createPackageBuilder(params, app, type);

        return new LinuxPackageBuilder(pkgBuilder)
                .additionalDependencies(LINUX_PACKAGE_DEPENDENCIES.fetchFrom(params))
                .category(LINUX_CATEGORY.fetchFrom(params))
                .menuGroupName(LINUX_MENU_GROUP.fetchFrom(params))
                .release(RELEASE.fetchFrom(params))
                .directName(LINUX_PACKAGE_NAME.fetchFrom(params));
    }

    private static LinuxPackage createLinuxRpmPackage(
            Map<String, ? super Object> params) throws ConfigException, IOException {
        var pkgBuilder = createLinuxPackageBuilder(params, LINUX_RPM);
        return new LinuxRpmPackageBuilder(pkgBuilder)
                .licenseType(LICENSE_TYPE.fetchFrom(params))
                .create();
    }

    private static LinuxPackage createLinuxDebPackage(
            Map<String, ? super Object> params) throws ConfigException, IOException {
        var pkgBuilder = createLinuxPackageBuilder(params, LINUX_DEB);
        return new LinuxDebPackageBuilder(pkgBuilder)
                .maintainerEmail(MAINTAINER_EMAIL.fetchFrom(params))
                .create();
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
            (s, p) -> (s == null || "null".equalsIgnoreCase(s))
            ? false : Boolean.valueOf(s)
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
