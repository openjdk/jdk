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
import jdk.jpackage.internal.util.CompositeProxy;

/**
 * Linux DEB package.
 * <p>
 * Use {@link #create} method to create objects implementing this interface.
 *
 * @see <a href="https://www.debian.org/doc/debian-policy/ch-binary.html#">https://www.debian.org/doc/debian-policy/ch-binary.html#</a>
 * @see <a href="https://linux.die.net/man/5/deb-control">https://linux.die.net/man/5/deb-control</a>
 */
public interface LinuxDebPackage extends LinuxPackage, LinuxDebPackageMixin {

    /**
     * Gets the value of the maintainer property of this DEB package.
     *
     * @see <a href=
     *      "https://www.debian.org/doc/debian-policy/ch-controlfields.html#s-f-maintainer">https://www.debian.org/doc/debian-policy/ch-controlfields.html#s-f-maintainer</a>
     * @return the maintainer property of this DEB package
     */
    default String maintainer() {
        return String.format("%s <%s>", app().vendor(), maintainerEmail());
    }

    /**
     * Gets the relative path to this DEB package's copyright file. Returns empty
     * {@link Optional} instance if this DEB package has no copyright file.
     *
     * @return the relative path to the copyright file of this DEB package
     */
    default Optional<Path> relativeCopyrightFilePath() {
        if (isInstallDirInUsrTree() || Path.of("/").resolve(relativeInstallDir()).startsWith("/usr/")) {
            return Optional.of(Path.of("usr/share/doc/", packageName(), "copyright"));
        } else if (!isRuntimeInstaller()) {
            return Optional.of(relativeInstallDir().resolve("share/doc/copyright"));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Constructs {@link LinuxDebPackage} instance from the given
     * {@link LinuxPackage} and {@link LinuxDebPackageMixin} instances.
     *
     * @param pkg the Linux package
     * @param mixin DEB-specific details supplementing the Linux package
     * @return the proxy dispatching calls to the given objects
     */
    public static LinuxDebPackage create(LinuxPackage pkg, LinuxDebPackageMixin mixin) {
        return CompositeProxy.create(LinuxDebPackage.class, pkg, mixin);
    }
}
