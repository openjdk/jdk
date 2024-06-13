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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import static jdk.jpackage.internal.Functional.ThrowingSupplier.toSupplier;
import static jdk.jpackage.internal.StandardBundlerParam.ABOUT_URL;
import static jdk.jpackage.internal.StandardBundlerParam.DESCRIPTION;
import static jdk.jpackage.internal.StandardBundlerParam.INSTALLER_NAME;
import static jdk.jpackage.internal.StandardBundlerParam.INSTALL_DIR;
import static jdk.jpackage.internal.StandardBundlerParam.LICENSE_FILE;
import static jdk.jpackage.internal.StandardBundlerParam.VERSION;
import static jdk.jpackage.internal.StandardBundlerParam.getPredefinedAppImage;

interface Package {

    enum PackageType {
        WinMsi(".msi"),
        WinExe(".exe"),
        LinuxDeb(".deb"),
        LinuxRpm(".rpm"),
        MacPkg(".pkg"),
        MacDmg(".dmg");

        PackageType(String suffix) {
            this.suffix = suffix;
        }

        String suffix() {
            return suffix;
        }

        static PackageType fromCmdLineType(String type) {
            return Stream.of(values()).filter(pt -> {
                return pt.suffix().substring(1).equals(type);
            }).findAny().get();
        }

        private final String suffix;
    }

    Application app();

    PackageType type();

    /**
     * Returns platform-specific package name.
     */
    String name();

    String description();

    String version();

    String aboutURL();

    Path licenseFile();

    Path predefinedAppImage();

    default ApplicationLayout appLayout() {
        return app().appLayout();
    }

    default Path installerName() {
        var type = type();
        switch (type) {
            case WinMsi, WinExe -> {
                return Path.of(String.format("%s-%s%s", name(), version(), type.suffix()));
            }
            default -> {
                throw new UnsupportedOperationException();
            }
        }
    }

    default boolean isRuntimeInstaller() {
        return app().mainLauncher() == null;
    }

    /**
     * Returns relative path to the package installation directory. On Windows it should be relative
     * to %ProgramFiles% and relative to the system root ('/') on other platforms.
     */
    Path relativeInstallDir();

    static record Impl(Application app, PackageType type, String name, String description,
            String version, String aboutURL, Path licenseFile, Path predefinedAppImage,
            Path relativeInstallDir) implements Package {

    }

    static class Proxy implements Package {

        Proxy(Package target) {
            this.target = target;
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
        public String name() {
            return target.name();
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

        private final Package target;
    }

    static Package createFromParams(Map<String, ? super Object> params, Application app,
            PackageType type) throws ConfigException {
        var name = Optional.ofNullable(INSTALLER_NAME.fetchFrom(params)).orElseGet(app::name);
        var description = Optional.ofNullable(DESCRIPTION.fetchFrom(params)).orElseGet(app::name);
        var version = Optional.ofNullable(VERSION.fetchFrom(params)).orElseGet(app::version);
        var aboutURL = ABOUT_URL.fetchFrom(params);
        var licenseFile = Optional.ofNullable(LICENSE_FILE.fetchFrom(params)).map(Path::of).orElse(null);
        var predefinedAppImage = getPredefinedAppImage(params);

        var relativeInstallDir = Optional.ofNullable(INSTALL_DIR.fetchFrom(params)).map(Path::of)
                .orElseGet(() -> {
                    switch (type) {
                        case WinExe, WinMsi -> {
                            return app.appImageDirName();
                        }
                        case LinuxDeb, LinuxRpm -> {
                            return Path.of("/opt").resolve(app.appImageDirName());
                        }
                        case MacDmg, MacPkg -> {
                            String root;
                            if (StandardBundlerParam.isRuntimeInstaller(params)) {
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
                });
        if (relativeInstallDir.isAbsolute()) {
            relativeInstallDir = relativeInstallDir.relativize(Path.of("/"));
        }

        return new Impl(app, type, name, description, version, aboutURL, licenseFile,
                predefinedAppImage, relativeInstallDir);
    }

    final static String PARAM_ID = "target.package";

    static final StandardBundlerParam<Package> TARGET_PACKAGE = new StandardBundlerParam<>(
            PARAM_ID, Package.class, params -> {
                return toSupplier(() -> {
                    return Package.createFromParams(params, Application.TARGET_APPLICATION
                            .fetchFrom(params), PackageType.fromCmdLineType(Workshop.PACKAGE_TYPE
                            .fetchFrom(params)));
                }).get();
            }, null);
}
