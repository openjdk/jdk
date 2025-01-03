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
import java.util.Optional;

public interface Package {

    Application app();

    PackageType type();

    default Optional<StandardPackageType> asStandardPackageType() {
        if (type() instanceof StandardPackageType stdType) {
            return Optional.of(stdType);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns platform-specific package name.
     *
     * The value should be valid file system name as it will be used to create
     * files/directories in the file system.
     */
    String packageName();

    String description();

    String version();

    Optional<String> aboutURL();

    Optional<Path> licenseFile();

    Optional<Path> predefinedAppImage();

    /**
     * Returns source app image layout.
     */
    default AppImageLayout appImageLayout() {
        return app().imageLayout();
    }

    default Optional<ApplicationLayout> asApplicationLayout() {
        return app().asApplicationLayout();
    }

    /**
     * Returns app image layout inside of the package.
     */
    default AppImageLayout packageLayout() {
        return appImageLayout().resolveAt(relativeInstallDir());
    }

    default Optional<ApplicationLayout> asPackageApplicationLayout() {
        if (packageLayout() instanceof ApplicationLayout layout) {
            return Optional.of(layout);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns app image layout of the installed package.
     */
    default Optional<AppImageLayout> installedPackageLayout() {
        return asStandardPackageType().map(stdType -> {
            switch (stdType) {
                case LINUX_DEB, LINUX_RPM, MAC_DMG, MAC_PKG -> {
                    return packageLayout().resolveAt(Path.of("/"));
                }
                case WIN_EXE, WIN_MSI -> {
                    return packageLayout();
                }
                default -> {
                    throw new UnsupportedOperationException();
                }
            }
        });
    }

    default Optional<ApplicationLayout> asInstalledPackageApplicationLayout() {
        return installedPackageLayout().map(layout -> {
            if (layout instanceof ApplicationLayout appLayout) {
                return appLayout;
            } else {
                return (ApplicationLayout)null;
            }
        });
    }

    /**
     * Returns package file name.
     */
    default String packageFileName() {
        return String.format("%s-%s", packageName(), version());
    }

    default Optional<String> packageFileSuffix() {
        return asStandardPackageType().map(StandardPackageType::suffix);
    }

    default String packageFileNameWithSuffix() {
        return packageFileName() + packageFileSuffix().orElse("");
    }

    default boolean isRuntimeInstaller() {
        return app().isRuntime();
    }

    /**
     * Returns relative path to the package installation directory.
     *
     * On Windows it should be relative to %ProgramFiles% and relative
     * to the system root ('/') on other platforms.
     */
    Path relativeInstallDir();

    record Stub(Application app, PackageType type, String packageName,
            String description, String version, Optional<String> aboutURL,
            Optional<Path> licenseFile, Optional<Path> predefinedAppImage,
            Path relativeInstallDir) implements Package {
    }

    class Unsupported implements Package {

        @Override
        public Application app() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PackageType type() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String packageName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String description() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String version() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> aboutURL() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Path> licenseFile() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Path> predefinedAppImage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path relativeInstallDir() {
            throw new UnsupportedOperationException();
        }
    }
}
