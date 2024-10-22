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
import java.util.Map;
import java.util.stream.Stream;
import static jdk.jpackage.internal.ApplicationFromParams.createBundlerParam;
import jdk.jpackage.internal.LinuxApplication.Impl;
import static jdk.jpackage.internal.StandardBundlerParam.SHORTCUT_HINT;

final class LinuxApplicationFromParams {

    private static LinuxApplication create(Map<String, ? super Object> params) throws ConfigException, IOException {
        var app = ApplicationFromParams.create(params, launcherParams -> {
            var launcher = LauncherFromParams.create(launcherParams);

            var shortcut = Stream.of(SHORTCUT_HINT, LINUX_SHORTCUT_HINT).filter(param -> {
                return launcherParams.containsKey(param.getID());
            }).map(param -> {
                return param.fetchFrom(launcherParams);
            }).findFirst();
            return new LinuxLauncher.Impl(launcher, shortcut);
        });
        return new Impl(app);
    }

    static final BundlerParamInfo<LinuxApplication> APPLICATION = createBundlerParam(
            LinuxApplicationFromParams::create);

    private static final BundlerParamInfo<Boolean> LINUX_SHORTCUT_HINT = new BundlerParamInfo<>(
            Arguments.CLIOptions.LINUX_SHORTCUT_HINT.getId(),
            Boolean.class,
            params -> false,
            (s, p) -> (s == null || "null".equalsIgnoreCase(s))
            ? false : Boolean.valueOf(s)
    );
}
