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
import jdk.jpackage.internal.util.CompositeProxy;

/**
 * Linux package.
 * <p>
 * Use {@link #create} method to create objects implementing this interface.
 */
public interface LinuxPackage extends Package, LinuxPackageMixin {

    LinuxApplication app();

    @Override
    AppImageLayout packageLayout();

    @Override
    default String packageFileName() {
        String packageFileNameTemlate = asStandardPackageType().map(stdType -> {
            switch (stdType) {
                case LINUX_DEB -> {
                    return "%s_%s_%s";
                }
                case LINUX_RPM -> {
                    return "%s-%s.%s";
                }
                default -> {
                    throw new IllegalStateException();
                }
            }
        }).orElseThrow(UnsupportedOperationException::new);

        return String.format(packageFileNameTemlate, packageName(), versionWithRelease(), arch());
    }

    /**
     * Gets the version with the release component of this Linux package.
     *
     * @return the version with the release component of this Linux package
     */
    default String versionWithRelease() {
        return String.format("%s%s", version(), release().map(r -> "-" + r).orElse(""));
    }

    /**
     * Returns <code>true</code> in this Linux package installs in "/usr" tree.
     *
     * @return <code>true</code> in this Linux package installs in "/usr" tree
     */
    default boolean isInstallDirInUsrTree() {
        return !relativeInstallDir().getFileName().equals(Path.of(packageName()));
    }

    /**
     * Constructs {@link LinuxPackage} instance from the given
     * {@link Package} and {@link LinuxPackageMixin} instances.
     *
     * @param pkg the generic package
     * @param mixin Linux-specific details supplementing the Linux package
     * @return the proxy dispatching calls to the given objects
     */
    public static LinuxPackage create(Package pkg, LinuxPackageMixin mixin) {
        return CompositeProxy.create(LinuxPackage.class, pkg, mixin);
    }
}
