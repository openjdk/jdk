/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.DottedVersion;


final class ToolValidator {

    ToolValidator(String tool) {
        this(Path.of(tool));
    }

    ToolValidator(Path toolPath) {
        this.toolPath = Objects.requireNonNull(toolPath);
        if (OperatingSystem.isLinux()) {
            setCommandLine("--version");
        }
    }

    ToolValidator setCommandLine(String... args) {
        this.args = List.of(args);
        return this;
    }

    ToolValidator setMinimalVersion(Comparable<String> v) {
        minimalVersion = v;
        return this;
    }

    ToolValidator setMinimalVersion(DottedVersion v) {
        return setMinimalVersion(new Comparable<String>() {
            @Override
            public int compareTo(String o) {
                return DottedVersion.compareComponents(v, DottedVersion.lazy(o));
            }

            @Override
            public String toString() {
                return v.toString();
            }
        });
    }

    ToolValidator setVersionParser(Function<Stream<String>, String> v) {
        versionParser = v;
        return this;
    }

    ToolValidator setToolNotFoundErrorHandler(Function<Path, ConfigException> v) {
        toolNotFoundErrorHandler = v;
        return this;
    }

    ToolValidator setToolOldVersionErrorHandler(BiFunction<Path, String, ConfigException> v) {
        toolOldVersionErrorHandler = v;
        return this;
    }

    ToolValidator checkExistsOnly(boolean v) {
        checkExistsOnly = v;
        return this;
    }

    ToolValidator checkExistsOnly() {
        return checkExistsOnly(true);
    }

    ConfigException validate() {
        if (checkExistsOnly) {
            if (Files.isExecutable(toolPath) && !Files.isDirectory(toolPath)) {
                return null;
            } else if (Files.exists(toolPath)) {
                return new ConfigException(
                        I18N.format("error.tool-not-executable", toolPath), (String)null);
            } else if (toolNotFoundErrorHandler != null) {
                return toolNotFoundErrorHandler.apply(toolPath);
            } else {
                return new ConfigException(
                        I18N.format("error.tool-not-found", toolPath),
                        I18N.format("error.tool-not-found.advice", toolPath));
            }
        }

        List<String> cmdline = new ArrayList<>();
        cmdline.add(toolPath.toString());
        if (args != null) {
            cmdline.addAll(args);
        }

        boolean canUseTool = false;
        if (minimalVersion == null) {
            // No version check.
            canUseTool = true;
        }

        String version = null;

        try {
            var result = Executor.of(cmdline).setQuiet(true).saveOutput().execute();
            var lines = result.content();
            if (versionParser != null && minimalVersion != null) {
                version = versionParser.apply(lines.stream());
                if (version != null && minimalVersion.compareTo(version) <= 0) {
                    canUseTool = true;
                }
            }
        } catch (IOException e) {
            return new ConfigException(I18N.format("error.tool-error", toolPath, e.getMessage()), null, e);
        }

        if (canUseTool) {
            // All good. Tool can be used.
            return null;
        } else if (toolOldVersionErrorHandler != null) {
            return toolOldVersionErrorHandler.apply(toolPath, version);
        } else {
            return new ConfigException(
                    I18N.format("error.tool-old-version", toolPath, minimalVersion),
                    I18N.format("error.tool-old-version.advice", toolPath, minimalVersion));
        }
    }

    private final Path toolPath;
    private List<String> args;
    private Comparable<String> minimalVersion;
    private Function<Stream<String>, String> versionParser;
    private Function<Path, ConfigException> toolNotFoundErrorHandler;
    private BiFunction<Path, String, ConfigException> toolOldVersionErrorHandler;
    private boolean checkExistsOnly;
}
