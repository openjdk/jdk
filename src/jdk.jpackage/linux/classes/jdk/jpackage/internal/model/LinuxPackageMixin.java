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

import java.util.Optional;

/**
 * Details of Linux package.
 */
public interface LinuxPackageMixin {

    /**
     * Overrides {@link Package#packageLayout()}.
     */
    AppImageLayout packageLayout();

    /**
     * Gets the name of the start menu group where to create shortcuts for
     * application launchers of this package.
     *
     * @return the name of the start menu group where to create shortcuts for
     *         application launchers of this package
     *
     * @see LinuxLauncherMixin#shortcut()
     */
    String menuGroupName();

    /**
     * Gets the category of this package.
     *
     * @return the category of this package
     */
    Optional<String> category();

    /**
     * Gets a string with the additional dependencies of this package. Returns an
     * empty {@link Optional} instance if this package has no additional
     * dependencies.
     *
     * @return a string with the additional dependencies of this package
     */
    Optional<String> additionalDependencies();

    /**
     * Gets the release of this package. Returns an empty {@link Optional} instance
     * if this package doesn't have a release.
     * <p>
     * For RPM packages, this is the value of a "Release" property in spec file. RPM
     * packages always have a release.
     * <p>
     * For DEB packages, this is an optional {@code debian_revision} component of a
     * package version. See <a href=
     * "https://www.debian.org/doc/debian-policy/ch-controlfields.html#s-f-version#">https://www.debian.org/doc/debian-policy/ch-controlfields.html#s-f-version#</a>.
     *
     * @return the release of this package
     */
    Optional<String> release();

    /**
     * Gets the platform architecture of this package.
     *
     * @return the platform architecture of this package
     */
    String arch();

    /**
     * Default implementation of {@link LinuxPackageMixin} interface.
     */
    record Stub(AppImageLayout packageLayout, String menuGroupName,
            Optional<String> category, Optional<String> additionalDependencies,
            Optional<String> release, String arch) implements LinuxPackageMixin {
    }
}
