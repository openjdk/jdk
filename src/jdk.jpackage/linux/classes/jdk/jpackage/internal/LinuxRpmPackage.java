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
import static jdk.jpackage.internal.Functional.ThrowingFunction.toFunction;

interface LinuxRpmPackage extends LinuxPackage {

    String licenseType();

    static class Impl extends LinuxPackage.Proxy implements LinuxRpmPackage {

        public Impl(LinuxPackage target, String licenseType) {
            super(target);
            this.licenseType = licenseType;
        }

        @Override
        public String licenseType() {
            return licenseType;
        }

        private final String licenseType;
    }

    private static LinuxRpmPackage createFromParams(Map<String, ? super Object> params) throws ConfigException {
        var pkg = LinuxPackage.createFromParams(params, StandardPackageType.LinuxRpm);

        var licenseType = Internal.LICENSE_TYPE.fetchFrom(params);

        return new Impl(pkg, licenseType);
    }

    final static class Internal {

        private static final BundlerParamInfo<String> LICENSE_TYPE
                = new StandardBundlerParam<>(
                        Arguments.CLIOptions.LINUX_RPM_LICENSE_TYPE.getId(),
                        String.class,
                        params -> I18N.getString("param.license-type.default"),
                        (s, p) -> s);
    }

    static final StandardBundlerParam<LinuxRpmPackage> TARGET_PACKAGE = new StandardBundlerParam<>(
            Package.PARAM_ID, LinuxRpmPackage.class, params -> {
                return toFunction(LinuxRpmPackage::createFromParams).apply(params);
            }, null);
}
