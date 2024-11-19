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

import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.Application;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import static jdk.jpackage.internal.I18N.buildConfigException;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.Package.Stub;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.model.StandardPackageType;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_DEB;
import static jdk.jpackage.internal.model.StandardPackageType.LINUX_RPM;
import static jdk.jpackage.internal.model.StandardPackageType.MAC_DMG;
import static jdk.jpackage.internal.model.StandardPackageType.MAC_PKG;
import static jdk.jpackage.internal.model.StandardPackageType.WIN_EXE;
import static jdk.jpackage.internal.model.StandardPackageType.WIN_MSI;

final class PackageBuilder {

    PackageBuilder(Application app, PackageType type) {
        this.app = Objects.requireNonNull(app);
        this.type = Objects.requireNonNull(type);
    }

    Package create() throws ConfigException {
        final var effectiveName = Optional.ofNullable(name).orElseGet(app::name);

        Path relativeInstallDir;
        if (installDir != null) {
            var normalizedInstallDir = mapInstallDir(installDir, type);
            if (type instanceof StandardPackageType stdType) {
                boolean addPackageName = true;
                switch (stdType) {
                    case LINUX_DEB, LINUX_RPM -> {
                        switch (normalizedInstallDir.toString()) {
                            case "/usr", "/usr/local" -> {
                                addPackageName = false;
                            }
                        }
                    }
                    case WIN_EXE, WIN_MSI -> {
                        addPackageName = false;
                    }
                    case MAC_DMG,MAC_PKG -> {
                    }
                }
                if (addPackageName) {
                    normalizedInstallDir = normalizedInstallDir.resolve(effectiveName);
                }
            }
            relativeInstallDir = normalizedInstallDir;
        } else if (type instanceof StandardPackageType stdType) {
            relativeInstallDir = defaultInstallDir(stdType, effectiveName, app);
        } else {
            throw new UnsupportedOperationException();
        }

        if (relativeInstallDir.isAbsolute()) {
            relativeInstallDir = Path.of("/").relativize(relativeInstallDir);
        }

        return new Stub(
                app,
                type,
                effectiveName,
                Optional.ofNullable(description).orElseGet(app::description),
                version = Optional.ofNullable(version).orElseGet(app::version),
                aboutURL,
                licenseFile,
                predefinedAppImage,
                relativeInstallDir);
    }

    PackageBuilder name(String v) {
        name = v;
        return this;
    }

    PackageBuilder fileName(Path v) {
        fileName = v;
        return this;
    }

    PackageBuilder description(String v) {
        description = v;
        return this;
    }

    PackageBuilder version(String v) {
        version = v;
        return this;
    }

    PackageBuilder aboutURL(String v) {
        aboutURL = v;
        return this;
    }

    PackageBuilder licenseFile(Path v) {
        licenseFile = v;
        return this;
    }

    PackageBuilder predefinedAppImage(Path v) {
        predefinedAppImage = v;
        return this;
    }

    PackageBuilder installDir(Path v) {
        installDir = v;
        return this;
    }

    private static Path mapInstallDir(Path installDir, PackageType pkgType)
            throws ConfigException {
        var ex = buildConfigException("error.invalid-install-dir", installDir).create();

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

    private static Path defaultInstallDir(StandardPackageType pkgType, String pkgName, Application app) {
        switch (pkgType) {
            case WIN_EXE, WIN_MSI -> {
                return Path.of(app.name());
            }
            case LINUX_DEB, LINUX_RPM -> {
                return Path.of("/opt").resolve(pkgName);
            }
            case MAC_DMG, MAC_PKG -> {
                Path base;
                if (app.isRuntime()) {
                    base = Path.of("/Library/Java/JavaVirtualMachines");
                } else {
                    base = Path.of("/Applications");
                }
                return base.resolve(pkgName);
            }
            default -> {
                throw new UnsupportedOperationException();
            }
        }
    }

    private String name;
    private Path fileName;
    private String description;
    private String version;
    private String aboutURL;
    private Path licenseFile;
    private Path predefinedAppImage;
    private Path installDir;

    final private PackageType type;
    final private Application app;
}
