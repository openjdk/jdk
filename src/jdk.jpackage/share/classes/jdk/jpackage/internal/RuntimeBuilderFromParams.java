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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static jdk.jpackage.internal.StandardBundlerParam.ADD_MODULES;
import static jdk.jpackage.internal.StandardBundlerParam.JLINK_OPTIONS;
import static jdk.jpackage.internal.StandardBundlerParam.LIMIT_MODULES;
import static jdk.jpackage.internal.StandardBundlerParam.MODULE_PATH;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_RUNTIME_IMAGE;

final class RuntimeBuilderFromParams {

    static RuntimeBuilder create(Map<String, ? super Object> params,
            List<LauncherStartupInfo> startupInfos) throws ConfigException {
        List<Path> modulePath = Optional.ofNullable(MODULE_PATH.fetchFrom(params)).orElseGet(
                List::of);

        Path predefinedRuntimeImage = PREDEFINED_RUNTIME_IMAGE.fetchFrom(params);
        if (predefinedRuntimeImage != null) {
            return RuntimeBuilder.createCopyingRuntimeBuilder(predefinedRuntimeImage, modulePath
                    .toArray(Path[]::new));
        } else {
            Set<String> addModules = Optional.ofNullable(ADD_MODULES.fetchFrom(params)).orElseGet(
                    Set::of);
            Set<String> limitModules = Optional.ofNullable(LIMIT_MODULES.fetchFrom(params))
                    .orElseGet(Set::of);
            List<String> options = Optional.ofNullable(JLINK_OPTIONS.fetchFrom(params)).orElseGet(
                    List::of);
            return JLinkRuntimeBuilder.createJLinkRuntimeBuilder(modulePath, addModules,
                    limitModules, options, startupInfos);

        }
    }
}
