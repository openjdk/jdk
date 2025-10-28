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

import java.util.Objects;
import java.util.Optional;

/**
 * Custom application launcher icon sourced from the resource directory.
 * <p>
 * Use {@link #create(String)} method to create an instance of this type.
 */
public sealed interface ResourceDirLauncherIcon extends LauncherIcon {

    /**
     * Returns name of the resource referencing an icon file in the resource directory.
     * @return name of the resource referencing an icon file in the resource directory
     */
    String name();

    /**
     * Returns the given icon as {@link ResourceDirLauncherIcon} type or an empty {@link Optional} instance
     * if the given icon object is not an instance of {@link ResourceDirLauncherIcon} type.
     *
     * @param icon application launcher icon object or <code>null</null>
     * @return the given icon as {@link ResourceDirLauncherIcon} type or an empty {@link Optional} instance
     */
    public static Optional<ResourceDirLauncherIcon> fromLauncherIcon(LauncherIcon icon) {
        if (icon instanceof ResourceDirLauncherIcon customIcon) {
            return Optional.of(customIcon);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Creates object of type {@link ResourceDirLauncherIcon} from the name of the resource referencing an icon file in the resource directory.
     * @param name name of the resource referencing an icon file in the resource directory
     * @return {@link ResourceDirLauncherIcon} instance
     */
    public static ResourceDirLauncherIcon create(String name) {
        Objects.requireNonNull(name);
        return new Stub(name);
    }

    /**
     * Default implementation of {@link ResourceDirLauncherIcon} type.
     */
    record Stub(String name) implements ResourceDirLauncherIcon {
    }
}
