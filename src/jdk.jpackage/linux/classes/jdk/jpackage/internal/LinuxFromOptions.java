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

import static jdk.jpackage.internal.FromOptions.buildApplicationBuilder;
import static jdk.jpackage.internal.FromOptions.createPackageBuilder;
import static jdk.jpackage.internal.FromOptions.findLauncherShortcut;
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
import jdk.jpackage.internal.model.LinuxApplication;
import jdk.jpackage.internal.model.LinuxDebPackage;
import jdk.jpackage.internal.model.LinuxLauncher;
import jdk.jpackage.internal.model.LinuxLauncherMixin;
import jdk.jpackage.internal.model.LinuxRpmPackage;
import jdk.jpackage.internal.model.StandardPackageType;

final class LinuxFromOptions {

    static LinuxApplication createLinuxApplication(Options optionValues) {

        final var launcherFromOptions = new LauncherFromOptions().faWithDefaultDescription();

        final var app = buildApplicationBuilder().create(optionValues, launcherOptionValues -> {

            final var launcher = launcherFromOptions.create(launcherOptionValues);

            final var shortcut = findLauncherShortcut(LINUX_SHORTCUT_HINT, optionValues, launcherOptionValues);

            return LinuxLauncher.create(launcher, new LinuxLauncherMixin.Stub(shortcut));

        }, APPLICATION_LAYOUT).create();

        return LinuxApplication.create(app);
    }

    static LinuxRpmPackage createLinuxRpmPackage(Options optionValues) {

        final var superPkgBuilder = createLinuxPackageBuilder(optionValues, LINUX_RPM);

        final var pkgBuilder = new LinuxRpmPackageBuilder(superPkgBuilder);

        LINUX_RPM_LICENSE_TYPE.ifPresentIn(optionValues, pkgBuilder::licenseType);

        return pkgBuilder.create();
    }

    static LinuxDebPackage createLinuxDebPackage(Options optionValues) {

        final var superPkgBuilder = createLinuxPackageBuilder(optionValues, LINUX_DEB);

        final var pkgBuilder = new LinuxDebPackageBuilder(superPkgBuilder);

        LINUX_DEB_MAINTAINER_EMAIL.ifPresentIn(optionValues, pkgBuilder::maintainerEmail);

        final var pkg = pkgBuilder.create();

        // Show warning if license file is missing
        if (pkg.licenseFile().isEmpty()) {
            Log.verbose(I18N.getString("message.debs-like-licenses"));
        }

        return pkg;
    }

    private static LinuxPackageBuilder createLinuxPackageBuilder(Options optionValues, StandardPackageType type) {

        final var app = createLinuxApplication(optionValues);

        final var superPkgBuilder = createPackageBuilder(optionValues, app, type);

        final var pkgBuilder = new LinuxPackageBuilder(superPkgBuilder);

        LINUX_PACKAGE_DEPENDENCIES.ifPresentIn(optionValues, pkgBuilder::additionalDependencies);
        LINUX_APP_CATEGORY.ifPresentIn(optionValues, pkgBuilder::category);
        LINUX_MENU_GROUP.ifPresentIn(optionValues, pkgBuilder::menuGroupName);
        LINUX_RELEASE.ifPresentIn(optionValues, pkgBuilder::release);
        LINUX_PACKAGE_NAME.ifPresentIn(optionValues, pkgBuilder::literalName);

        return pkgBuilder;
    }

}
