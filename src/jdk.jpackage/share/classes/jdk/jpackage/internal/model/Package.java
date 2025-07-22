/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Native application package.
 *
 * The interface specifies the source app image layout with two transformations:
 * package app image layout and installed app image layout.
 * <p>
 * Use {@link #appImageLayout()} or {@link #asApplicationLayout()} to get the
 * unresolved source app image layout.
 * <p>
 * Package app image layout is the source app image layout resolved at the
 * relative installation directory of the package. Additionally, to resolve the
 * source layout, some packages may transform the source layout.
 * <p>
 * Use {@link #packageLayout()} or {@link #asPackageApplicationLayout()} to get
 * the package app image layout.
 * <p>
 * Installed app image layout is the layout of the installed app image.
 * <p>
 * Use {@link #installedPackageLayout()} or
 * {@link #asInstalledPackageApplicationLayout()} to get the installed app image
 * layout.
 * <p>
 * The following table shows app image layouts of the application named "Duke"
 * on different platforms:
 * <table border="1">
 * <tr>
 * <th></th>
 * <th>Source app image layout</th>
 * <th>Package app image layout</th>
 * <th>Installed app image layout</th>
 * </tr>
 * <tr>
 * <th>Windows</th>
 * <td>bin/foo.exe app/foo.jar</td>
 * <td>Duke/bin/foo.exe Duke/app/foo.jar</td>
 * <td>Duke/bin/foo.exe Duke/app/foo.jar</td>
 * </tr>
 * <tr>
 * <th>Linux</th>
 * <td>bin/foo lib/app/foo.jar</td>
 * <td>opt/duke/bin/foo opt/duke/lib/app/foo.jar</td>
 * <td>/opt/duke/bin/foo /opt/duke/lib/app/foo.jar</td>
 * </tr>
 * <tr>
 * <th>OSX</th>
 * <td>Contents/MacOS/foo Contents/app/foo.jar</td>
 * <td>Applications/Duke.app/Contents/MacOS/foo Applications/Duke.app/Contents/app/foo.jar</td>
 * <td>/Applications/Duke.app/Contents/MacOS/foo /Applications/Duke.app/Contents/app/foo.jar</td>
 * </tr>
 * </table>
 */
public interface Package extends BundleSpec {

    /**
     * Gets the application of this package.
     *
     * @return the application of this package
     */
    Application app();

    /**
     * Gets the type of this package.
     *
     * @return the type of this package
     */
    PackageType type();

    /**
     * Gets the type of this package as {@link StandardPackageType} type or an empty
     * {@link Optional} instance if the return value of {@link #type()} call is not
     * an instance of {@link StandardPackageType} type.
     *
     * @return the type of this package as {@link StandardPackageType} type
     */
    default Optional<StandardPackageType> asStandardPackageType() {
        if (type() instanceof StandardPackageType stdType) {
            return Optional.of(stdType);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Gets the name of the native package of this package.
     * <p>
     * The value is a valid file system name and can be safely used to name
     * files/directories in the file system.
     *
     * @return the name of the native package of this package
     */
    String packageName();

    /**
     * Gets the description of this package.
     * @return the description of this package
     */
    String description();

    /**
     * Gets the version of this package.
     * @return the version of this package
     */
    String version();

    /**
     * Gets the "About" URL of this package if available or an empty
     * {@link Optional} instance otherwise.
     *
     * @return the "About" URL of this package
     */
    Optional<String> aboutURL();

    /**
     * Gets the path to a license file of this package if available or an empty
     * {@link Optional} instance otherwise.
     *
     * @return the path to a license file of this package
     */
    Optional<Path> licenseFile();

    /**
     * Gets the path to a directory with the predefined app image of this package if
     * available or an empty {@link Optional} instance otherwise.
     * <p>
     * If {@link #isRuntimeInstaller()} returns {@code true}, the method returns the
     * path to a directory with the predefined runtime. The layout of this directory
     * should be of {@link RuntimeLayout} type.
     * <p>
     * If {@link #isRuntimeInstaller()} returns {@code false}, the method returns
     * the path to a directory with the predefined application image. The layout of
     * this directory should be of {@link ApplicationLayout} type.
     *
     * @return the path to a directory with the application app image of this
     *         package
     */
    Optional<Path> predefinedAppImage();

    /**
     * Gets the unresolved source app image layout of the application of this package.
     *
     * @return the unresolved app image layout of the application of this package
     *
     * @see #packageLayout
     * @see #installedPackageLayout
     */
    default AppImageLayout appImageLayout() {
        return app().imageLayout();
    }

    /**
     * Returns the unresolved source app image layout of the application of this
     * package as {@link ApplicationLayout} type or an empty {@link Optional}
     * instance if the layout object is of incompatible type.
     * <p>
     * Returns an empty {@link Optional} instance if {@link #isRuntimeInstaller()}
     * returns <code>true</code>.
     *
     * @return the unresolved source app image layout of the application of this
     *         package as {@link ApplicationLayout} type
     *
     * @see #appImageLayout
     */
    default Optional<ApplicationLayout> asApplicationLayout() {
        return app().asApplicationLayout();
    }

    /**
     * Gets the layout of the installed app image of the application resolved at the
     * relative installation directory of this package.
     *
     * @return the layout of the installed app image of the application resolved at
     *         the relative installation directory of this package
     *
     * @see #relativeInstallDir
     * @see #appImageLayout
     * @see #installedPackageLayout
     */
    default AppImageLayout packageLayout() {
        return appImageLayout().resolveAt(relativeInstallDir());
    }

    /**
     * Returns the layout of the installed app image of the application resolved at
     * the relative installation directory of this package as
     * {@link ApplicationLayout} type or an empty {@link Optional} instance if the
     * layout object is of incompatible type.
     * <p>
     * Returns an empty {@link Optional} instance if {@link #isRuntimeInstaller()}
     * returns <code>true</code>.
     *
     * @return the layout of the installed app image of the application resolved at
     *         the relative installation directory of this package as
     *         {@link ApplicationLayout} type
     *
     * @see #packageLayout
     */
    default Optional<ApplicationLayout> asPackageApplicationLayout() {
        if (packageLayout() instanceof ApplicationLayout layout) {
            return Optional.of(layout);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Gets the layout of the installed app image of this package.
     *
     * @return the layout of the installed app image of this package
     *
     * @see #appImageLayout
     * @see #packageLayout
     */
    default AppImageLayout installedPackageLayout() {
        return asStandardPackageType().map(stdType -> {
            switch (stdType) {
                case LINUX_DEB, LINUX_RPM, MAC_DMG, MAC_PKG -> {
                    return packageLayout().resolveAt(Path.of("/"));
                }
                case WIN_EXE, WIN_MSI -> {
                    return packageLayout();
                }
                default -> {
                    // Should never get here
                    throw new IllegalStateException();
                }
            }
        }).orElseThrow(UnsupportedOperationException::new);
    }

    /**
     * Returns the layout of the installed app image of this package as
     * {@link ApplicationLayout} type or an empty {@link Optional} instance if the
     * layout object is of incompatible type.
     * <p>
     * Returns an empty {@link Optional} instance if {@link #isRuntimeInstaller()}
     * returns <code>true</code>.
     *
     * @return the layout of the installed app image of this package as
     *         {@link ApplicationLayout} type
     *
     * @see #installedPackageLayout
     */
    default Optional<ApplicationLayout> asInstalledPackageApplicationLayout() {
        if (installedPackageLayout() instanceof ApplicationLayout layout) {
            return Optional.of(layout);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Get the name without an extension of the package file of this package.
     *
     * @return the name without an extension of the package file of this package
     */
    default String packageFileName() {
        return String.format("%s-%s", packageName(), version());
    }

    /**
     * Gets the extension of the package file of this package if available or an
     * empty {@link Optional} instance otherwise.
     *
     * @return the extension of the package file of this package
     */
    default Optional<String> packageFileSuffix() {
        return asStandardPackageType().map(StandardPackageType::suffix);
    }

    /**
     * Gets the full name of the package file of this package. The full name
     * consists of the name and the extension.
     *
     * @return the full name of the package file of this package
     */
    default String packageFileNameWithSuffix() {
        return packageFileName() + packageFileSuffix().orElse("");
    }

    /**
     * Returns <code>true</code> if the application of this package is Java runtime.
     *
     * @return <code>true</code> if the application of this package is Java runtime
     *
     * @see Application#isRuntime()
     */
    default boolean isRuntimeInstaller() {
        return app().isRuntime();
    }

    /**
     * Gets the relative path to the package installation directory of this package.
     *
     * On Windows it is relative to the program files directory
     * (<code>"%ProgramFiles%"</code>) and to the system root (<code>"/"</code>) on
     * other platforms.
     *
     * @return the relative path to the package installation directory of this
     *         package
     */
    Path relativeInstallDir();

    /**
     * Default implementation of {@link Package} interface.
     */
    record Stub(Application app, PackageType type, String packageName, String description, String version,
            Optional<String> aboutURL, Optional<Path> licenseFile, Optional<Path> predefinedAppImage,
            Path relativeInstallDir) implements Package {
    }
}
