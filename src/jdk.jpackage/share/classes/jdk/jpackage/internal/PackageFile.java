/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class PackageFile {

    /**
     * Returns path to package file.
     * @param appImageDir - path to application image
     */
    public static Path getPathInAppImage(Path appImageDir) {
        return getPathInAppImage(ApplicationLayout.platformAppImage().resolveAt(
                appImageDir));
    }

    /**
     * Returns path to package file.
     * @param appLayout - application layout
     */
    public static Path getPathInAppImage(ApplicationLayout appLayout) {
        return Optional.ofNullable(appLayout.appDirectory()).map(
                path -> path.resolve(FILENAME)).orElse(null);
    }

    PackageFile(String packageName) {
        Objects.requireNonNull(packageName);
        this.packageName = packageName;
    }

    void save(ApplicationLayout appLayout) throws IOException {
        Path dstDir = appLayout.appDirectory();
        if (dstDir != null) {
            Files.createDirectories(dstDir);
            Files.writeString(dstDir.resolve(FILENAME), packageName);
        }
    }

    private final String packageName;

    private static final String FILENAME = ".package";
}
