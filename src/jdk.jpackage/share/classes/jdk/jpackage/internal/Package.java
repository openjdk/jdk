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
import java.text.MessageFormat;
import java.util.stream.Stream;

interface Package {

    interface PackageType {
    }

    enum StandardPackageType implements PackageType {
        WinMsi(".msi"),
        WinExe(".exe"),
        LinuxDeb(".deb"),
        LinuxRpm(".rpm"),
        MacPkg(".pkg"),
        MacDmg(".dmg");

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
     */
    String packageName();

    String description();

    String version();

    String aboutURL();

    Path licenseFile();

    Path predefinedAppImage();

    default ApplicationLayout appLayout() {
        return app().appLayout();
    }

    default ApplicationLayout installedAppLayout() {
        Path root = relativeInstallDir();
        if (type() instanceof StandardPackageType type) {
            switch (type) {
                case LinuxDeb, LinuxRpm, MacDmg, MacPkg -> {
                    root = Path.of("/").resolve(root);
                }
            }
        }
        return appLayout().resolveAt(root);
    }

    /**
     * Returns package file name.
     */
    default Path packageFileName() {
        if (type() instanceof StandardPackageType type) {
            switch (type) {
                case WinMsi, WinExe -> {
                    return Path
                            .of(String.format("%s-%s%s", packageName(), version(), type.suffix()));
                }
                default -> {
                    throw new UnsupportedOperationException();
                }
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    default boolean isRuntimeInstaller() {
        return app().isRuntime();
    }

    /**
     * Returns relative path to the package installation directory. On Windows it should be relative
     * to %ProgramFiles% and relative to the system root ('/') on other platforms.
     */
    Path relativeInstallDir();

    static record Impl(Application app, PackageType type, String packageName, String description,
            String version, String aboutURL, Path licenseFile, Path predefinedAppImage,
            Path relativeInstallDir) implements Package {

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
        public Path relativeInstallDir() {
            return target.relativeInstallDir();
        }
    }

    static Path defaultInstallDir(Application app, StandardPackageType type) {
        switch (type) {
            case WinExe, WinMsi -> {
                return app.appImageDirName();
            }
            case LinuxDeb, LinuxRpm -> {
                return Path.of("/opt").resolve(app.appImageDirName());
            }
            case MacDmg, MacPkg -> {
                String root;
                if (app.isRuntime()) {
                    root = "/Library/Java/JavaVirtualMachines";
                } else {
                    root = "/Applications";
                }
                return Path.of(root).resolve(app.appImageDirName());
            }
            default -> {
                throw new IllegalArgumentException();
            }
        }
    }

    static Path mapInstallDir(Path installDir, PackageType pkgType) throws ConfigException {
        var ex = new ConfigException(MessageFormat.format(I18N.getString("error.invalid-install-dir"),
                installDir), null);

        if (installDir.getFileName().equals(Path.of(""))) {
            // Trailing '/' or '\\'. Strip them away.
            installDir = installDir.getParent();
        }

        if (installDir.toString().isEmpty()) {
            throw ex;
        }

        if (pkgType instanceof StandardPackageType stdPkgType) {
            switch (stdPkgType) {
                case WinExe, WinMsi -> {
                    if (installDir.isAbsolute()) {
                        throw ex;
                    }
                }
            }
        } else if (!installDir.isAbsolute()) {
            throw ex;
        }

        if (!installDir.normalize().toString().equals(installDir.toString())) {
            // Don't allow '..' or '.' in path components
            throw ex;
        }

        return installDir;
    }
}
