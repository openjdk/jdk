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

interface LinuxDebPackage extends LinuxPackage {

    String maintainerEmail();

    default String maintainer() {
        return String.format("%s <%s>", app().vendor(), maintainerEmail());
    }

    default String versionWithRelease() {
        return String.format("%s-%s", version(), release());
    }

    default Path relativeCopyrightFilePath() {
        if (isRuntimeInstaller()) {
            return null;
        } else if (isInstallDirInUsrTree() || Path.of("/").resolve(relativeInstallDir()).startsWith(
                "/usr/")) {
            return Path.of("/usr/share/doc/", packageName(), "copyright");
        } else {
            return relativeInstallDir().resolve("share/doc/copyright");
        }
    }

    static class Impl extends LinuxPackage.Proxy<LinuxPackage> implements LinuxDebPackage {

        public Impl(LinuxPackage target, String maintainerEmail) {
            super(target);
            this.maintainerEmail = maintainerEmail;
        }

        @Override
        public String maintainerEmail() {
            return maintainerEmail;
        }

        private final String maintainerEmail;
    }
}
