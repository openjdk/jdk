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
package jdk.jpackage.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.Result;

record MacDmgSystemEnvironment(Path hdiutil, Path osascript, Optional<Path> setFileUtility) implements SystemEnvironment {

    MacDmgSystemEnvironment {
    }

    static Result<MacDmgSystemEnvironment> create() {
        final var errors = Stream.of(HDIUTIL, OSASCRIPT)
                .map(ToolValidator::new)
                .map(ToolValidator::checkExistsOnly)
                .map(ToolValidator::validate)
                .filter(Objects::nonNull)
                .toList();
        if (errors.isEmpty()) {
            return Result.ofValue(new MacDmgSystemEnvironment(HDIUTIL, OSASCRIPT, findSetFileUtility()));
        } else {
            return Result.ofErrors(errors);
        }
    }

    // Location of SetFile utility may be different depending on MacOS version
    // We look for several known places and if none of them work will
    // try to find it
    static Optional<Path> findSetFileUtility() {
        return SETFILE_KNOWN_PATHS.stream().filter(setFilePath -> {
            // Validate SetFile, if Xcode is not installed it will run, but exit with error code
            return Result.of(
                    Executor.of(setFilePath.toString(), "-h").setQuiet(true)::executeExpectSuccess,
                    IOException.class).hasValue();
        }).findFirst().or(() -> {
            // generic find attempt
            final var executor = Executor.of("/usr/bin/xcrun", "-find", "SetFile").setQuiet(true).saveFirstLineOfOutput();

            return Result.of(executor::executeExpectSuccess, IOException.class).flatMap(execResult -> {
                return Result.of(() -> {
                    return execResult.stdout().stream().findFirst().map(Path::of).orElseThrow(execResult::unexpected);
                }, Exception.class);
            }).value().filter(v -> {
                return new ToolValidator(v).checkExistsOnly().validate() == null;
            }).map(Path::toAbsolutePath);
        });
    }

    static final List<Path> SETFILE_KNOWN_PATHS = Stream.of(
            "/Developer/Tools/SetFile",
            "/usr/bin/SetFile",
            "/Developer/usr/bin/SetFile").map(Path::of).collect(Collectors.toUnmodifiableList());

    private static final Path HDIUTIL = Path.of("/usr/bin/hdiutil");
    private static final Path OSASCRIPT = Path.of("/usr/bin/osascript");
}
