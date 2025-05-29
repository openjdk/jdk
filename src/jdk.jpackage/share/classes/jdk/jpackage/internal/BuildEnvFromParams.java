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

import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.RESOURCE_DIR;
import static jdk.jpackage.internal.StandardBundlerParam.TEMP_ROOT;
import static jdk.jpackage.internal.StandardBundlerParam.VERBOSE;

import java.util.Map;
import jdk.jpackage.internal.model.ConfigException;

final class BuildEnvFromParams {

    static BuildEnv create(Map<String, ? super Object> params) throws ConfigException {

        final var builder = new BuildEnvBuilder(TEMP_ROOT.fetchFrom(params));

        RESOURCE_DIR.copyInto(params, builder::resourceDir);
        VERBOSE.copyInto(params, builder::verbose);

        final var app = FromParams.APPLICATION.findIn(params).orElseThrow();

        final var pkg = FromParams.getCurrentPackage(params);

        if (app.isRuntime()) {
            builder.appImageDir(PREDEFINED_RUNTIME_IMAGE.fetchFrom(params));
        } else if (StandardBundlerParam.hasPredefinedAppImage(params)) {
            builder.appImageDir(StandardBundlerParam.getPredefinedAppImage(params));
        } else if (pkg.isPresent()) {
            builder.appImageDirForPackage();
        } else {
            builder.appImageDirFor(app);
        }

        return builder.create();
    }

    static final BundlerParamInfo<BuildEnv> BUILD_ENV = BundlerParamInfo.createBundlerParam(
            BuildEnv.class, BuildEnvFromParams::create);
}
