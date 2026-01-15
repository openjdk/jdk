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

package jdk.jpackage.internal.util;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import jdk.jpackage.internal.Log;
import jdk.internal.util.OperatingSystem;

public final class RuntimeVersionReader {

    public static Optional<String> readVersion(Path runtimeImage) {
        Optional<Path> releasePath = getRuntimeReleaseFile(runtimeImage);
        Optional<String> releaseVersion = Optional.empty();

        if (releasePath.isPresent()) {
            try (Reader reader = Files.newBufferedReader(releasePath.get())) {
                Properties props = new Properties();
                props.load(reader);
                String version = props.getProperty("JAVA_VERSION");
                if (version != null) {
                    version = version.replaceAll("^\"|\"$", "");
                }
                releaseVersion = Optional.ofNullable(version);
            } catch (IOException ex) {
                Log.verbose(ex);
            }
        };

        return releaseVersion;
    }

    private static Optional<Path> checkReleaseFilePath(Path releaseFilePath) {
        if (Files.isRegularFile(releaseFilePath)) {
            return Optional.of(releaseFilePath);
        } else {
            return Optional.empty();
        }
    }

    private static Optional<Path> getRuntimeReleaseFile(Path runtimeImage) {
        Optional<Path> releasePath = Optional.empty();

        // Try special case for macOS first ("Contents/Home/release").
        if (OperatingSystem.isMacOS()) {
            releasePath = checkReleaseFilePath(runtimeImage.resolve("Contents/Home/release"));
        }

        // Try root for all platforms including macOS if releasePath is not set
        if (releasePath.isEmpty()) {
            releasePath = checkReleaseFilePath(runtimeImage.resolve("release"));
        }

        return releasePath;
    }
}
