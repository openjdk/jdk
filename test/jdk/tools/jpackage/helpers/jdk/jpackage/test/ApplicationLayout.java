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
package jdk.jpackage.test;

import java.nio.file.Path;
import java.util.Optional;

public record ApplicationLayout(Path launchersDirectory, Path appDirectory,
        Path runtimeDirectory, Path runtimeHomeDirectory, Path appModsDirectory,
        Path desktopIntegrationDirectory, Path contentDirectory, Path libapplauncher) {

    public ApplicationLayout resolveAt(Path root) {
        return new ApplicationLayout(
                resolve(root, launchersDirectory),
                resolve(root, appDirectory),
                resolve(root, runtimeDirectory),
                resolve(root, runtimeHomeDirectory),
                resolve(root, appModsDirectory),
                resolve(root, desktopIntegrationDirectory),
                resolve(root, contentDirectory),
                resolve(root, libapplauncher));
    }

    public static ApplicationLayout linuxAppImage() {
        return new ApplicationLayout(
                Path.of("bin"),
                Path.of("lib/app"),
                Path.of("lib/runtime"),
                Path.of("lib/runtime"),
                Path.of("lib/app/mods"),
                Path.of("lib"),
                Path.of("lib"),
                Path.of("lib/libapplauncher.so")
        );
    }

    public static ApplicationLayout windowsAppImage() {
        return new ApplicationLayout(
                Path.of(""),
                Path.of("app"),
                Path.of("runtime"),
                Path.of("runtime"),
                Path.of("app/mods"),
                Path.of(""),
                Path.of(""),
                null
        );
    }

    public static ApplicationLayout macAppImage() {
        return new ApplicationLayout(
                Path.of("Contents/MacOS"),
                Path.of("Contents/app"),
                Path.of("Contents/runtime"),
                Path.of("Contents/runtime/Contents/Home"),
                Path.of("Contents/app/mods"),
                Path.of("Contents/Resources"),
                Path.of("Contents"),
                null
        );
    }

    public static ApplicationLayout platformAppImage() {
        if (TKit.isWindows()) {
            return windowsAppImage();
        }

        if (TKit.isLinux()) {
            return linuxAppImage();
        }

        if (TKit.isOSX()) {
            return macAppImage();
        }

        throw new IllegalArgumentException("Unknown platform");
    }

    public static ApplicationLayout javaRuntime() {
        return new ApplicationLayout(
                null,
                null,
                Path.of(""),
                null,
                null,
                null,
                null,
                null
        );
    }

    public static ApplicationLayout linuxUsrTreePackageImage(Path prefix,
            String packageName) {
        final Path lib = prefix.resolve(Path.of("lib", packageName));
        return new ApplicationLayout(
                prefix.resolve("bin"),
                lib.resolve("app"),
                lib.resolve("runtime"),
                lib.resolve("runtime"),
                lib.resolve("app/mods"),
                lib,
                lib,
                lib.resolve("lib/libapplauncher.so")
        );
    }

    private static Path resolve(Path base, Path path) {
        return Optional.ofNullable(path).map(base::resolve).orElse(null);
    }
}
