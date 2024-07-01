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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import jdk.internal.util.OperatingSystem;

interface RuntimeBuilder {

    void createRuntime(ApplicationLayout appLayout) throws PackagerException, IOException;

    static RuntimeBuilder createCopyingRuntimeBuilder(Path runtimeDir, Path... modulePath) throws ConfigException {
        if (!Files.exists(runtimeDir)) {
            throw new ConfigException(MessageFormat.format(I18N.getString(
                    "message.runtime-image-dir-does-not-exist"), "--runtime-image", runtimeDir
                            .toString()), MessageFormat.format(I18N.getString(
                            "message.runtime-image-dir-does-not-exist.advice"), "--runtime-image"));
        }

        return appLayout -> {
            final Path runtimeHome = getRuntimeHome(runtimeDir);

            // copy whole runtime, need to skip jmods and src.zip
            final List<String> excludes = Arrays.asList("jmods", "src.zip");
            IOUtils.copyRecursive(runtimeHome, appLayout.runtimeHomeDirectory(), excludes,
                    LinkOption.NOFOLLOW_LINKS);

            // if module-path given - copy modules to appDir/mods
            List<Path> defaultModulePath = getDefaultModulePath();
            Path dest = appLayout.appModsDirectory();

            for (Path mp : modulePath) {
                if (!defaultModulePath.contains(mp)) {
                    IOUtils.copyRecursive(mp, dest);
                }
            }
        };
    }

    private static Path getRuntimeHome(Path runtimeDir) {
        if (OperatingSystem.isMacOS()) {
            // On Mac topImage can be runtime root or runtime home.
            Path runtimeHome = runtimeDir.resolve("Contents/Home");
            if (Files.isDirectory(runtimeHome)) {
                // topImage references runtime root, adjust it to pick data from
                // runtime home
                return runtimeHome;
            }
        }
        return runtimeDir;
    }

    static List<Path> getDefaultModulePath() {
        return List.of(Path.of(System.getProperty("java.home"), "jmods").toAbsolutePath());
    }
}
