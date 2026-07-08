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

package jdk.jpackage.internal.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * An abstraction of macOS Application bundle.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Bundle_(macOS)#Application_bundles">https://en.wikipedia.org/wiki/Bundle_(macOS)#Application_bundles</a>
 */
public record MacBundle(Path root) {

    public MacBundle {
        Objects.requireNonNull(root);
    }

    public boolean isValid() {
        return Files.isDirectory(contentsDir()) && Files.isDirectory(macOsDir()) && Files.isRegularFile(infoPlistFile());
    }

    public Path contentsDir() {
        return root.resolve("Contents");
    }

    public Path homeDir() {
        return contentsDir().resolve("Home");
    }

    public Path macOsDir() {
        return contentsDir().resolve("MacOS");
    }

    public Path resourcesDir() {
        return contentsDir().resolve("Resources");
    }

    public Path infoPlistFile() {
        return contentsDir().resolve("Info.plist");
    }

    public static Optional<MacBundle> fromPath(Path path) {
        var bundle = new MacBundle(path);
        if (bundle.isValid()) {
            return Optional.of(bundle);
        } else {
            return Optional.empty();
        }
    }
}
