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
import static jdk.jpackage.internal.StandardBundlerParam.APP_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.StandardBundlerParam.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.StandardBundlerParam.RELEASE;
import static jdk.jpackage.internal.StandardBundlerParam.VERSION;

interface Launcher {

    String name();

    default Path executableName() {
        return Path.of(name());
    }

    LauncherStartupInfo startupInfo();

    List<FileAssociation> fileAssociations();

    boolean isService();

    String version();

    String release();

    String description();

    /**
     * Null for the default or resource directory icon, empty path for no icon, other value for
     * custom icon.
     */
    Path icon();

    static record Impl(String name, LauncherStartupInfo startupInfo,
            List<FileAssociation> fileAssociations, boolean isService, String version,
            String release, String description, Path icon) implements Launcher {

    }

    static class Proxy implements Launcher {

        Proxy(Launcher target) {
            this.target = target;
        }

        @Override
        public String name() {
            return target.name();
        }

        @Override
        public LauncherStartupInfo startupInfo() {
            return target.startupInfo();
        }

        @Override
        public List<FileAssociation> fileAssociations() {
            return target.fileAssociations();
        }

        @Override
        public boolean isService() {
            return target.isService();
        }

        @Override
        public String version() {
            return target.version();
        }

        @Override
        public String release() {
            return target.release();
        }

        @Override
        public String description() {
            return target.description();
        }

        @Override
        public Path icon() {
            return target.icon();
        }

        private final Launcher target;
    }

    static Launcher createFromParams(Map<String, ? super Object> params) {
        var name = APP_NAME.fetchFrom(params);

        LauncherStartupInfo startupInfo = null;
        if (PREDEFINED_APP_IMAGE.fetchFrom(params) == null) {
            startupInfo = LauncherStartupInfo.createFromParams(params);
        }

        var isService = LAUNCHER_AS_SERVICE.fetchFrom(params);
        var version = VERSION.fetchFrom(params);
        var release = RELEASE.fetchFrom(params);
        var description = DESCRIPTION.fetchFrom(params);
        var icon = StandardBundlerParam.ICON.fetchFrom(params);
        var fa = FileAssociation.fetchFrom(params);

        return new Impl(name, startupInfo, fa, isService, version, release, description, icon);
    }
}
