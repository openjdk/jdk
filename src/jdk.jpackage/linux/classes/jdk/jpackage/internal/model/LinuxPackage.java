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

public interface LinuxPackage extends Package {

    String menuGroupName();

    String category();

    String additionalDependencies();

    String release();

    String arch();

    @Override
    default ApplicationLayout packageLayout() {
        if (isInstallDirInUsrTree()) {
            return ApplicationLayout.linuxUsrTreePackageImage(relativeInstallDir(), packageName());
        } else {
            return Package.super.packageLayout();
        }
    }

    @Override
    default String packageFileName() {
        String packageFileNameTemlate;
        switch (asStandardPackageType()) {
            case LINUX_DEB -> {
                packageFileNameTemlate = "%s_%s-%s_%s";
            }
            case LINUX_RPM -> {
                packageFileNameTemlate = "%s-%s-%s.%s";
            }
            default -> {
                throw new UnsupportedOperationException();
            }
        }

        return String.format(packageFileNameTemlate, packageName(), version(), release(), arch());
    }

    default boolean isInstallDirInUsrTree() {
        return !relativeInstallDir().getFileName().equals(Path.of(packageName()));
    }

    class Impl extends Package.Proxy<Package> implements LinuxPackage {

        public Impl(Package target, String menuGroupName, String category,
                String additionalDependencies, String release, String arch) throws ConfigException {
            super(target);
            this.menuGroupName = menuGroupName;
            this.category = category;
            this.release = release;
            this.additionalDependencies = additionalDependencies;
            this.arch = arch;
        }

        @Override
        public String menuGroupName() {
            return menuGroupName;
        }

        @Override
        public String category() {
            return category;
        }

        @Override
        public String additionalDependencies() {
            return additionalDependencies;
        }

        @Override
        public String release() {
            return release;
        }

        @Override
        public String arch() {
            return arch;
        }

        private final String menuGroupName;
        private final String category;
        private final String additionalDependencies;
        private final String release;
        private final String arch;
    }

    class Proxy<T extends LinuxPackage> extends Package.Proxy<T> implements LinuxPackage {

        public Proxy(T target) {
            super(target);
        }

        @Override
        public String menuGroupName() {
            return target.menuGroupName();
        }

        @Override
        public String category() {
            return target.category();
        }

        @Override
        public String additionalDependencies() {
            return target.additionalDependencies();
        }

        @Override
        public String release() {
            return target.release();
        }

        @Override
        public String arch() {
            return target.arch();
        }
    }
}
