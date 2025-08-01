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

package jdk.jpackage.internal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import jdk.jpackage.internal.model.AppImageLayout;

/**
 * An abstraction of macOS Application bundle.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Bundle_(macOS)#Application_bundles">https://en.wikipedia.org/wiki/Bundle_(macOS)#Application_bundles</a>
 */
record MacBundle(Path root) {

    MacBundle {
        Objects.requireNonNull(root);
    }

    boolean isValid() {
        return Files.isDirectory(contentsDir()) && Files.isDirectory(macOsDir()) && Files.isRegularFile(infoPlistFile());
    }

    boolean isSigned() {
        return Files.isDirectory(contentsDir().resolve("_CodeSignature"));
    }

    Path contentsDir() {
        return root.resolve("Contents");
    }

    Path homeDir() {
        return contentsDir().resolve("Home");
    }

    Path macOsDir() {
        return contentsDir().resolve("MacOS");
    }

    Path resourcesDir() {
        return contentsDir().resolve("Resources");
    }

    Path infoPlistFile() {
        return contentsDir().resolve("Info.plist");
    }

    static boolean isDirectoryMacBundle(Path dir) {
        return new MacBundle(dir).isValid();
    }

    static MacBundle fromAppImageLayout(AppImageLayout layout) {
        return new MacBundle(layout.rootDirectory());
    }
}
