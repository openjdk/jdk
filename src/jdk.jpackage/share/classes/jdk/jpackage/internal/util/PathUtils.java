/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal.util;

import java.nio.file.Path;
import java.util.Optional;

public final class PathUtils {

    public static String getSuffix(Path path) {
        String filename = replaceSuffix(path.getFileName(), null).toString();
        return path.getFileName().toString().substring(filename.length());
    }

    public static Path addSuffix(Path path, String suffix) {
        Path parent = path.getParent();
        String filename = path.getFileName().toString() + suffix;
        return parent != null ? parent.resolve(filename) : Path.of(filename);
    }

    public static Path replaceSuffix(Path path, String suffix) {
        Path parent = path.getParent();
        String filename = path.getFileName().toString().replaceAll("\\.[^.]*$",
                "") + Optional.ofNullable(suffix).orElse("");
        return parent != null ? parent.resolve(filename) : Path.of(filename);
    }

    public static Path resolveNullablePath(Path base, Path path) {
        return Optional.ofNullable(path).map(base::resolve).orElse(null);
    }

    public static Path normalizedAbsolutePath(Path path) {
        if (path != null) {
            return path.normalize().toAbsolutePath();
        } else {
            return null;
        }
    }

    public static String normalizedAbsolutePathString(Path path) {
        if (path != null) {
            return normalizedAbsolutePath(path).toString();
        } else {
            return null;
        }
    }
}
