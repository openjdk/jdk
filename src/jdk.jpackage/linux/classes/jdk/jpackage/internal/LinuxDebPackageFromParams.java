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

import java.util.Map;
import static jdk.jpackage.internal.Package.StandardPackageType.LinuxDeb;
import static jdk.jpackage.internal.PackageFromParams.createBundlerParam;

final class LinuxDebPackageFromParams {

    private static LinuxDebPackage create(Map<String, ? super Object> params) throws ConfigException {
        var pkg = LinuxPackageFromParams.create(params, LinuxDeb);

        var maintainerEmail = MAINTAINER_EMAIL.fetchFrom(params);

        return new LinuxDebPackage.Impl(pkg, maintainerEmail);
    }

    static final StandardBundlerParam<LinuxDebPackage> PACKAGE = createBundlerParam(
            LinuxDebPackageFromParams::create);

    private static final BundlerParamInfo<String> MAINTAINER_EMAIL = new StandardBundlerParam<>(
            Arguments.CLIOptions.LINUX_DEB_MAINTAINER.getId(),
            String.class,
            params -> "Unknown",
            (s, p) -> s);

}
