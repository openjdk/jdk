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

import static jdk.jpackage.internal.FromOptions.buildApplicationBuilder;
import static jdk.jpackage.internal.FromOptions.createPackageBuilder;
import static jdk.jpackage.internal.LinuxPackagingPipeline.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.cli.StandardOption.LINUX_APP_CATEGORY;
import static jdk.jpackage.internal.cli.StandardOption.LINUX_DEB_MAINTAINER_EMAIL;
import static jdk.jpackage.internal.cli.StandardOption.LINUX_MENU_GROUP;
import static jdk.jpackage.internal.cli.StandardOption.LINUX_PACKAGE_DEPENDENCIES;
import static jdk.jpackage.internal.cli.StandardOption.LINUX_PACKAGE_NAME;
import static jdk.jpackage.internal.cli.StandardOption.LINUX_RELEASE;
import static jdk.jpackage.internal.cli.StandardOption.LINUX_RPM_LICENSE_TYPE;
import static jdk.jpackage.internal.cli.StandardOption.LINUX_SHORTCUT_HINT;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_DEB;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_RPM;

import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LinuxApplication;
import jdk.jpackage.internal.model.LinuxDebPackage;
import jdk.jpackage.internal.model.LinuxLauncher;
import jdk.jpackage.internal.model.LinuxLauncherMixin;
import jdk.jpackage.internal.model.LinuxRpmPackage;
import jdk.jpackage.internal.model.StandardPackageType;

final class LinuxFromOptions {

    static LinuxApplication createLinuxApplication(Options options) {

        final var launcherFromOptions = new LauncherFromOptions().faWithDefaultDescription();

        final var appBuilder = buildApplicationBuilder().create(options, launcherOptions -> {

            final var launcher = launcherFromOptions.create(launcherOptions);

            final var shortcut = LINUX_SHORTCUT_HINT.findIn(launcherOptions);

            return LinuxLauncher.create(launcher, new LinuxLauncherMixin.Stub(shortcut));

        }, (LinuxLauncher linuxLauncher, Launcher launcher) -> {
            return LinuxLauncher.create(launcher, linuxLauncher);
        }, APPLICATION_LAYOUT);

        appBuilder.launchers().map(LinuxPackagingPipeline::normalizeShortcuts).ifPresent(appBuilder::launchers);

        return LinuxApplication.create(appBuilder.create());
    }

    static LinuxRpmPackage createLinuxRpmPackage(Options options, LinuxRpmSystemEnvironment sysEnv) {

        final var superPkgBuilder = createLinuxPackageBuilder(options, sysEnv, LINUX_RPM);

        final var pkgBuilder = new LinuxRpmPackageBuilder(superPkgBuilder);

        LINUX_RPM_LICENSE_TYPE.ifPresentIn(options, pkgBuilder::licenseType);

        return pkgBuilder.create();
    }

    static LinuxDebPackage createLinuxDebPackage(Options options, LinuxDebSystemEnvironment sysEnv) {

        final var superPkgBuilder = createLinuxPackageBuilder(options, sysEnv, LINUX_DEB);

        final var pkgBuilder = new LinuxDebPackageBuilder(superPkgBuilder);

        LINUX_DEB_MAINTAINER_EMAIL.ifPresentIn(options, pkgBuilder::maintainerEmail);

        final var pkg = pkgBuilder.create();

        // Show warning if license file is missing
        if (pkg.licenseFile().isEmpty()) {
            Log.verbose(I18N.getString("message.debs-like-licenses"));
        }

        return pkg;
    }

    private static LinuxPackageBuilder createLinuxPackageBuilder(Options options, LinuxSystemEnvironment sysEnv, StandardPackageType type) {

        final var app = createLinuxApplication(options);

        final var superPkgBuilder = createPackageBuilder(options, app, type);

        final var pkgBuilder = new LinuxPackageBuilder(superPkgBuilder);

        pkgBuilder.arch(sysEnv.packageArch());

        LINUX_PACKAGE_DEPENDENCIES.ifPresentIn(options, pkgBuilder::additionalDependencies);
        LINUX_APP_CATEGORY.ifPresentIn(options, pkgBuilder::category);
        LINUX_MENU_GROUP.ifPresentIn(options, pkgBuilder::menuGroupName);
        LINUX_RELEASE.ifPresentIn(options, pkgBuilder::release);
        LINUX_PACKAGE_NAME.ifPresentIn(options, pkgBuilder::literalName);

        return pkgBuilder;
    }

}
