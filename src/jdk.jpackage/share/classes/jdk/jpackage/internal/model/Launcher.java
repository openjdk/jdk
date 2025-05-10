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

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jdk.jpackage.internal.resources.ResourceLocator;

/**
 * Application launcher.
 *
 * @see Application#launchers()
 */
public interface Launcher {

    /**
     * Gets the name of this launcher.
     *
     * @return the name of this launcher
     */
    String name();

    /**
     * Gets the name of the executable file of this launcher without file extension.
     *
     * @return the name of the executable file of this launcher
     */
    default String executableName() {
        return name();
    }

    /**
     * Gets extension of the executable file of this launcher if available or an
     * empty {@link Optional} instance otherwise.
     *
     * @return the extension of the executable file of this launcher
     */
    default Optional<String> executableSuffix() {
        return Optional.empty();
    }

    /**
     * Gets the full name of the executable file of this launcher. The full name
     * consists of the name and the extension.
     *
     * @return the full name of the executable file of this launcher
     */
    default String executableNameWithSuffix() {
        return executableName() + executableSuffix().orElse("");
    }

    /**
     * Gets the startup information of this launcher if available or an empty
     * {@link Optional} instance otherwise.
     *
     * @apiNote Launchers from an external application image may not have startup
     *          information.
     * @return the startup information of this launcher
     */
    Optional<LauncherStartupInfo> startupInfo();

    /**
     * Gets the file associations of this launcher.
     *
     * @return the file associations of this launcher
     */
    List<FileAssociation> fileAssociations();

    /**
     * Returns <code>true</code> if this launcher should be installed as a service.
     *
     * @return <code>true</code> if this launcher should be installed as a service
     */
    boolean isService();

    /**
     * Gets the description of this launcher.
     *
     * @return the description of this launcher
     */
    String description();

    /**
     * Opens a stream with the template executable file for this launcher. Caller is
     * responsible for close the stream.
     *
     * @return a stream with the template executable file for this launcher
     */
    default InputStream executableResource() {
        return ResourceLocator.class.getResourceAsStream("jpackageapplauncher");
    }

    /**
     * Gets the icon for this launcher or an empty {@link Optional} instance if the
     * launcher is requested to have no icon.
     *
     * @return the icon for this launcher
     * @see #hasIcon()
     * @see #hasDefaultIcon()
     * @see #hasCustomIcon()
     */
    Optional<LauncherIcon> icon();

    /**
     * Returns <code>true</code> if this launcher is requested to have an icon.
     *
     * @return <code>true</code> if this launcher is requested to have an icon
     * @see #icon()
     * @see #hasDefaultIcon()
     * @see #hasCustomIcon()
     */
    default boolean hasIcon() {
        return icon().isPresent();
    }

    /**
     * Returns <code>true</code> if this launcher has a default icon.
     *
     * @return <code>true</code> if this launcher has a default icon
     * @see DefaultLauncherIcon
     * @see #icon()
     * @see #hasIcon()
     * @see #hasCustomIcon()
     */
    default boolean hasDefaultIcon() {
        return icon().flatMap(DefaultLauncherIcon::fromLauncherIcon).isPresent();
    }

    /**
     * Returns <code>true</code> if this launcher has a custom icon.
     *
     * @return <code>true</code> if this launcher has a custom icon
     * @see CustomLauncherIcon
     * @see #icon()
     * @see #hasDefaultIcon()
     * @see #hasIcon()
     */
    default boolean hasCustomIcon() {
        return icon().flatMap(CustomLauncherIcon::fromLauncherIcon).isPresent();
    }

    /**
     * Gets key in the resource bundle of {@link jdk.jpackage/} module referring to
     * the default launcher icon.
     *
     * @return the key in the resource bundle referring to the default launcher icon
     */
    String defaultIconResourceName();

    /**
     * Gets the additional properties for application launcher entries in the app
     * image (".jpackage") file.
     *
     * @return the additional properties for application launcher entries in
     *         ".jpackage" file
     */
    Map<String, String> extraAppImageFileData();

    /**
     * Default implementation of {@link Launcher} interface.
     */
    record Stub(String name, Optional<LauncherStartupInfo> startupInfo, List<FileAssociation> fileAssociations,
            boolean isService, String description, Optional<LauncherIcon> icon, String defaultIconResourceName,
            Map<String, String> extraAppImageFileData) implements Launcher {
    }
}
