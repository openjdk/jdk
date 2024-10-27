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
import java.util.Optional;
import java.util.stream.Stream;
import static jdk.jpackage.internal.Getter.getValueOrDefault;

interface Package {

    interface PackageType {
    }

    enum StandardPackageType implements PackageType {
        WIN_MSI(".msi"),
        WIN_EXE(".exe"),
        LINUX_DEB(".deb"),
        LINUX_RPM(".rpm"),
        MAC_PKG(".pkg"),
        MAC_DMG(".dmg");

        StandardPackageType(String suffix) {
            this.suffix = suffix;
        }

        String suffix() {
            return suffix;
        }

        static StandardPackageType fromCmdLineType(String type) {
            return Stream.of(values()).filter(pt -> {
                return pt.suffix().substring(1).equals(type);
            }).findAny().get();
        }

        private final String suffix;
    }

    Application app();

    PackageType type();

    default StandardPackageType asStandardPackageType() {
        if (type() instanceof StandardPackageType stdType) {
            return stdType;
        } else {
            return null;
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

    String aboutURL();

    Path licenseFile();

    Path predefinedAppImage();

    /**
     * Returns source app image layout.
     */
    default ApplicationLayout appLayout() {
        return app().appLayout();
    }

    /**
     * Returns app image layout inside of the package.
     */
    default ApplicationLayout packageLayout() {
        var layout = appLayout();
        var pathGroup = layout.pathGroup();
        var baseDir = relativeInstallDir();

        for (var key : pathGroup.keys()) {
            pathGroup.setPath(key, baseDir.resolve(pathGroup.getPath(key)));
        }

        return layout;
    }

    /**
     * Returns app image layout of the installed package.
     */
    default ApplicationLayout installedPackageLayout() {
        Path root = relativeInstallDir();
        if (type() instanceof StandardPackageType type) {
            switch (type) {
                case LINUX_DEB, LINUX_RPM, MAC_DMG, MAC_PKG -> {
                    root = Path.of("/").resolve(root);
                }
            }
        }
        return appLayout().resolveAt(root);
    }

    /**
     * Returns package file name.
     */
    default String packageFileName() {
        if (type() instanceof StandardPackageType type) {
            switch (type) {
                case WIN_MSI, WIN_EXE -> {
                    return String.format("%s-%s", packageName(), version());
                }
            }
        }
        throw new UnsupportedOperationException();
    }

    default String packageFileSuffix() {
        if (type() instanceof StandardPackageType type) {
            return type.suffix();
        } else {
            throw new UnsupportedOperationException();
        }
    }

    default String packageFileNameWithSuffix() {
        return packageFileName() + Optional.ofNullable(packageFileSuffix()).orElse("");
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
    default Path relativeInstallDir() {
        var path = Optional.ofNullable(configuredInstallBaseDir()).map(v -> {
            switch (asStandardPackageType()) {
                case LINUX_DEB, LINUX_RPM -> {
                    switch (v.toString()) {
                        case "/usr", "/usr/local" -> {
                            return v;
                        }
                    }
                }
                case WIN_EXE, WIN_MSI -> {
                    return v;
                }
            }
            return v.resolve(packageName());
        }).orElseGet(() -> defaultInstallDir(this));

        switch (asStandardPackageType()) {
            case WIN_EXE, WIN_MSI -> {
                return path;
            }
            default -> {
                return Path.of("/").relativize(path);
            }
        }
    }

    Path configuredInstallBaseDir();

    static record Impl(Application app, PackageType type, String packageName,
            String description, String version, String aboutURL, Path licenseFile,
            Path predefinedAppImage, Path configuredInstallBaseDir) implements Package {

        public Impl {
            description = Optional.ofNullable(description).orElseGet(app::description);
            version = Optional.ofNullable(version).orElseGet(app::version);
            packageName = Optional.ofNullable(packageName).orElseGet(app::name);
        }
    }

    static class Unsupported implements Package {

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
        public String aboutURL() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path licenseFile() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path predefinedAppImage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path configuredInstallBaseDir() {
            throw new UnsupportedOperationException();
        }

    }

    static class Proxy<T extends Package> extends ProxyBase<T> implements Package {

        Proxy(T target) {
            super(target);
        }

        @Override
        public Application app() {
            return target.app();
        }

        @Override
        public PackageType type() {
            return target.type();
        }

        @Override
        public String packageName() {
            return target.packageName();
        }

        @Override
        public String description() {
            return target.description();
        }

        @Override
        public String version() {
            return target.version();
        }

        @Override
        public String aboutURL() {
            return target.aboutURL();
        }

        @Override
        public Path licenseFile() {
            return target.licenseFile();
        }

        @Override
        public Path predefinedAppImage() {
            return target.predefinedAppImage();
        }

        @Override
        public Path configuredInstallBaseDir() {
            return target.configuredInstallBaseDir();
        }
    }

    static Package override(Package base, Package overrides) {
        return new Impl(
                getValueOrDefault(overrides, base, Package::app),
                getValueOrDefault(overrides, base, Package::type),
                getValueOrDefault(overrides, base, Package::packageName),
                getValueOrDefault(overrides, base, Package::description),
                getValueOrDefault(overrides, base, Package::version),
                getValueOrDefault(overrides, base, Package::aboutURL),
                getValueOrDefault(overrides, base, Package::licenseFile),
                getValueOrDefault(overrides, base, Package::predefinedAppImage),
                getValueOrDefault(overrides, base, Package::configuredInstallBaseDir));
    }

    static Path mapInstallDir(Path installDir, PackageType pkgType) throws ConfigException {
        var ex = ConfigException.build().message("error.invalid-install-dir",
                installDir).create();

        if (installDir.getNameCount() == 0) {
            throw ex;
        }

        if (installDir.getFileName().equals(Path.of(""))) {
            // Trailing '/' or '\\'. Strip them away.
            installDir = installDir.getParent();
        }

        if (installDir.toString().isEmpty()) {
            throw ex;
        }

        switch (pkgType) {
            case StandardPackageType.WIN_EXE, StandardPackageType.WIN_MSI -> {
                if (installDir.isAbsolute()) {
                    throw ex;
                }
            }
            default -> {
                if (!installDir.isAbsolute()) {
                    throw ex;
                }
            }
        }

        if (!installDir.normalize().toString().equals(installDir.toString())) {
            // Don't allow '..' or '.' in path components
            throw ex;
        }

        return installDir;
    }

    private static Path defaultInstallDir(Package pkg) {
        switch (pkg.asStandardPackageType()) {
            case WIN_EXE, WIN_MSI -> {
                return Path.of(pkg.app().name());
            }
            case LINUX_DEB, LINUX_RPM -> {
                return Path.of("/opt").resolve(pkg.packageName());
            }
            case MAC_DMG, MAC_PKG -> {
                Path base;
                if (pkg.isRuntimeInstaller()) {
                    base = Path.of("/Library/Java/JavaVirtualMachines");
                } else {
                    base = Path.of("/Applications");
                }
                return base.resolve(pkg.packageName());
            }
            default -> {
                throw new UnsupportedOperationException();
            }
        }
    }
}
