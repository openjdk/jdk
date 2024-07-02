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
import jdk.jpackage.internal.Launcher.Impl;
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE;

final class LauncherFromParams {

    static Launcher create(Map<String, ? super Object> params) {
        var name = APP_NAME.fetchFrom(params);

        LauncherStartupInfo startupInfo = null;
        if (PREDEFINED_APP_IMAGE.fetchFrom(params) == null) {
            startupInfo = LauncherStartupInfo.createFromParams(params);
        }

        var isService = LAUNCHER_AS_SERVICE.fetchFrom(params);
        var description = DESCRIPTION.fetchFrom(params);
        var icon = StandardBundlerParam.ICON.fetchFrom(params);
        var fa = FileAssociation.fetchFrom(params);

        return new Impl(name, startupInfo, fa, isService, description, icon);
    }
}
