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
import java.util.Optional;
import jdk.jpackage.internal.Functional.ThrowingFunction;
import static jdk.jpackage.internal.Functional.ThrowingSupplier.toSupplier;
import jdk.jpackage.internal.Package.Impl;
import jdk.jpackage.internal.Package.PackageType;
import jdk.jpackage.internal.Package.StandardPackageType;
import static jdk.jpackage.internal.Package.defaultInstallDir;
import static jdk.jpackage.internal.Package.mapInstallDir;
import static jdk.jpackage.internal.StandardBundlerParam.ABOUT_URL;
import static jdk.jpackage.internal.StandardBundlerParam.DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.INSTALLER_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.INSTALL_DIR;
import static jdk.jpackage.internal.StandardBundlerParam.LICENSE_FILE;
import static jdk.jpackage.internal.StandardBundlerParam.VERSION;
import static jdk.jpackage.internal.StandardBundlerParam.getPredefinedAppImage;

final class PackageFromParams {

    static Package create(Map<String, ? super Object> params,
            StandardBundlerParam<? extends Application> appParam, PackageType pkgType) throws ConfigException {
        var app = appParam.fetchFrom(params);
        var packageName = Optional.ofNullable(INSTALLER_NAME.fetchFrom(params)).orElseGet(app::name);
        var description = Optional.ofNullable(DESCRIPTION.fetchFrom(params)).orElseGet(app::name);
        var version = Optional.ofNullable(VERSION.fetchFrom(params)).orElseGet(app::version);
        var aboutURL = ABOUT_URL.fetchFrom(params);
        var licenseFile = Optional.ofNullable(LICENSE_FILE.fetchFrom(params)).map(Path::of).orElse(null);
        var predefinedAppImage = getPredefinedAppImage(params);

        var relativeInstallDir = Optional.ofNullable(INSTALL_DIR.fetchFrom(params)).map(v -> {
            return toSupplier(() -> mapInstallDir(Path.of(v), pkgType)).get();
        }).orElseGet(() -> {
            if (pkgType instanceof StandardPackageType stdPkgType) {
                return defaultInstallDir(app, stdPkgType);
            } else {
                return app.appImageDirName();
            }
        });
        if (relativeInstallDir.isAbsolute()) {
            relativeInstallDir = relativeInstallDir.relativize(Path.of("/"));
        }

        return new Impl(app, pkgType, packageName, description, version, aboutURL, licenseFile,
                predefinedAppImage, relativeInstallDir);
    }

    static <T extends Package> StandardBundlerParam<T> createBundlerParam(
            ThrowingFunction<Map<String, ? super Object>, T> valueFunc) {
        return StandardBundlerParam.createBundlerParam("target.package", valueFunc);
    }

    static final StandardBundlerParam<Package> PACKAGE = createBundlerParam(params -> {
        return PackageFromParams.create(params, ApplicationFromParams.APPLICATION,
                StandardPackageType.fromCmdLineType(WorkshopFromParams.PACKAGE_TYPE
                        .fetchFrom(params)));
    });
}
