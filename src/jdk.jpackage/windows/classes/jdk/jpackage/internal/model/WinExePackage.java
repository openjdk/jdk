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
package jdk.jpackage.internal.model;

import java.nio.file.Path;
import static jdk.jpackage.internal.model.StandardPackageType.WIN_EXE;

public interface WinExePackage extends Package {

    WinMsiPackage msiPackage();

    Path icon();

    final class Stub extends Package.Proxy<Package> implements WinExePackage {

        public Stub(WinMsiPackage msiPackage, Path icon) throws ConfigException {
            super(createExePackage(msiPackage));
            this.msiPackage = msiPackage;
            this.icon = icon;
        }

        @Override
        public WinMsiPackage msiPackage() {
            return msiPackage;
        }

        @Override
        public Path icon() {
            return icon;
        }

        private static Package createExePackage(Package pkg) {
            return new Package.Stub(
                    pkg.app(),
                    WIN_EXE,
                    pkg.packageName(),
                    pkg.description(),
                    pkg.version(),
                    pkg.aboutURL(),
                    pkg.licenseFile(),
                    pkg.predefinedAppImage(),
                    pkg.relativeInstallDir());
        }

        private final WinMsiPackage msiPackage;
        private final Path icon;
    }
}
