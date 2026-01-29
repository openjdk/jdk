/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Standard native package types.
 */
public enum StandardPackageType implements PackageType {
    WIN_MSI("bundle-type.win-msi", ".msi"),
    WIN_EXE("bundle-type.win-exe", ".exe"),
    LINUX_DEB("bundle-type.linux-deb", ".deb"),
    LINUX_RPM("bundle-type.linux-rpm", ".rpm"),
    MAC_PKG("bundle-type.mac-pkg", ".pkg"),
    MAC_DMG("bundle-type.mac-dmg", ".dmg");

    StandardPackageType(String key, String suffix) {
        this.key = Objects.requireNonNull(key);
        this.suffix = Objects.requireNonNull(suffix);
    }

    /**
     * Gets file extension of this package type.
     * E.g.: <code>.msi</code>, <code>.dmg</code>, <code>.deb</code>.
     * @return file extension of this package type
     */
    public String suffix() {
        return suffix;
    }

    @Override
    public String label() {
        return I18N.getString(key);
    }

    private final String key;
    private final String suffix;
}
