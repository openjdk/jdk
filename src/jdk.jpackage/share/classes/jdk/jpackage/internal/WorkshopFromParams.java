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
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.Workshop.Impl;
import jdk.jpackage.internal.Workshop.Proxy;

final class WorkshopFromParams {

    static Workshop create(Map<String, ? super Object> params) throws ConfigException {
        var root = StandardBundlerParam.TEMP_ROOT.fetchFrom(params);
        var resourceDir = StandardBundlerParam.RESOURCE_DIR.fetchFrom(params);

        var defaultWorkshop = new Impl(root, resourceDir);

        Path appImageDir;
        if (StandardBundlerParam.isRuntimeInstaller(params)) {
            appImageDir = StandardBundlerParam.PREDEFINED_RUNTIME_IMAGE.fetchFrom(params);
        } else if (StandardBundlerParam.hasPredefinedAppImage(params)) {
            appImageDir = StandardBundlerParam.getPredefinedAppImage(params);
        } else {
            Path dir;
            if (PACKAGE_TYPE.fetchFrom(params).equals("app-image") || OperatingSystem.isWindows()) {
                dir = ApplicationFromParams.APPLICATION.fetchFrom(params).appImageDirName();
            } else {
                dir = PackageFromParams.PACKAGE.fetchFrom(params).relativeInstallDir();
            }

            appImageDir = defaultWorkshop.buildRoot().resolve("image").resolve(dir);
        }

        return new Proxy(defaultWorkshop) {
            @Override
            public Path appImageDir() {
                return appImageDir;
            }
        };
    }

    static final StandardBundlerParam<Workshop> WORKSHOP = StandardBundlerParam.createBundlerParam(
            "workshop", WorkshopFromParams::create);

    static final StandardBundlerParam<String> PACKAGE_TYPE = new StandardBundlerParam<>(
            Arguments.CLIOptions.PACKAGE_TYPE.getId(), String.class, null, null);
}
