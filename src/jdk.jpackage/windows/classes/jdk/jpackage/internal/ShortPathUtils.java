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

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;


@SuppressWarnings("restricted")
final class ShortPathUtils {
    static String adjustPath(String path) {
        return toShortPath(path).orElse(path);
    }

    static Path adjustPath(Path path) {
        return toShortPath(path).orElse(path);
    }

    static Optional<String> toShortPath(String path) {
        Objects.requireNonNull(path);
        return toShortPath(Path.of(path)).map(Path::toString);
    }

    static Optional<Path> toShortPath(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException(String.format("[%s] path does not exist", path));
        }

        var normPath = path.normalize().toAbsolutePath().toString();
        if (normPath.length() > MAX_PATH) {
            return Optional.of(Path.of(getShortPathWrapper(normPath)));
        } else {
            return Optional.empty();
        }
    }

    private static String getShortPathWrapper(final String longPath) {
        String effectivePath;
        if (!longPath.startsWith(LONG_PATH_PREFIX)) {
            effectivePath = LONG_PATH_PREFIX + longPath;
        } else {
            effectivePath = longPath;
        }

        return Optional.ofNullable(getShortPath(effectivePath)).orElseThrow(
                () -> new ShortPathException(MessageFormat.format(I18N.getString(
                        "error.short-path-conv-fail"), effectivePath)));
    }

    static final class ShortPathException extends RuntimeException {

        ShortPathException(String msg) {
            super(msg);
        }

        private static final long serialVersionUID = 1L;
    }

    private static native String getShortPath(String longPath);

    private static final int MAX_PATH = 240;
    // See https://learn.microsoft.com/en-us/windows/win32/api/fileapi/nf-fileapi-getshortpathnamew
    private static final String LONG_PATH_PREFIX = "\\\\?\\";

    static {
        System.loadLibrary("jpackage");
    }
}
