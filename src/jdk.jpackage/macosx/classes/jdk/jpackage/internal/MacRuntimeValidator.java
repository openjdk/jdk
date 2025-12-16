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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.Predicate;
import jdk.jpackage.internal.model.RuntimeLayout;

final class MacRuntimeValidator {

    static void validateRuntimeHasJliLib(RuntimeLayout runtimeLayout) {
        final var jliName = Path.of("libjli.dylib");
        try (var walk = Files.walk(runtimeLayout.runtimeDirectory().resolve("lib"))) {
            if (walk.map(Path::getFileName).anyMatch(Predicate.isEqual(jliName))) {
                return;
            }
        } catch (NoSuchFileException ex) {
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        throw I18N.buildConfigException("error.invalid-runtime-image-missing-file",
                runtimeLayout.rootDirectory(),
                runtimeLayout.unresolve().runtimeDirectory().resolve("lib/**").resolve(jliName)).create();
    }

    static void validateRuntimeHasNoBinDir(RuntimeLayout runtimeLayout) {
        if (Files.isDirectory(runtimeLayout.runtimeDirectory().resolve("bin"))) {
            throw I18N.buildConfigException()
                    .message("error.invalid-runtime-image-bin-dir", runtimeLayout.rootDirectory())
                    .advice("error.invalid-runtime-image-bin-dir.advice", "--mac-app-store")
                    .create();
        }
    }
}
