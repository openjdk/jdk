/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.text.MessageFormat;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import jdk.jpackage.internal.WixToolset.WixToolsetType;

/**
 * WiX tool.
 */
public enum WixTool {
    Candle3("candle", DottedVersion.lazy("3.0")),
    Light3("light", DottedVersion.lazy("3.0")),
    Wix4("wix", DottedVersion.lazy("4.0.4"));

    WixTool(String commandName, DottedVersion minimalVersion) {
        this.toolFileName = IOUtils.addSuffix(Path.of(commandName), ".exe");
        this.minimalVersion = minimalVersion;
    }

    static final class ToolInfo {

        ToolInfo(Path path, String version) {
            this.path = path;
            this.version = new DottedVersion(version);
        }

        final Path path;
        final DottedVersion version;
    }


    static WixToolset createToolset() throws ConfigException {
        Map<WixTool, ToolInfo> tools = new HashMap<>();

        var wixInstallDirsSupplier = new Supplier<List<Path>>() {
            @Override
            public List<Path> get() {
                if (wixInstallDirs == null) {
                    wixInstallDirs = findWixInstallDirs();
                }
                return wixInstallDirs;
            }
            private List<Path> wixInstallDirs;
        };

        for (var tool : values()) {
            ToolInfo info = tool.find(wixInstallDirsSupplier);
            if (info != null) {
                tools.put(tool, info);
            }
        }

        return Stream.of(WixToolsetType.values()).map(toolsetType -> {
            return WixToolset.create(toolsetType.getTools(), tools);
        }).takeWhile(Objects::nonNull).findFirst().orElseGet(() -> {
            return WixToolset.create(Set.of(), Map.of());
        });
    }

    private ToolInfo find(Supplier<List<Path>> findWixInstallDirs) throws ConfigException {
        String[] version = new String[1];
        ConfigException err = createToolValidator(toolFileName,
                v -> version[0] = v).get();
        if (version[0] != null) {
            if (err == null) {
                // Found in PATH.
                return new ToolInfo(toolFileName, version[0]);
            }

            // Found in PATH, but something went wrong.
            throw err;
        }

        for (var dir : findWixInstallDirs.get()) {
            Path path = dir.resolve(toolFileName);
            if (Files.exists(path)) {
                err = createToolValidator(path, v -> version[0] = v).get();
                if (err != null) {
                    throw err;
                }
                return new ToolInfo(path, version[0]);
            }
        }

        throw err;
    }

    private static Set<WixTool> getWix3Tools() {
        return Set.of(Candle3, Light3);
    }

    private Supplier<ConfigException> createToolValidator(Path toolPath,
            Consumer<String> versionConsumer) {
        final var toolValidator = new ToolValidator(toolPath)
                .setMinimalVersion(minimalVersion)
                .setToolNotFoundErrorHandler(
                        (name, ex) -> new ConfigException(
                                I18N.getString("error.no-wix-tools"),
                                I18N.getString("error.no-wix-tools.advice")))
                .setToolOldVersionErrorHandler(
                        (name, version) -> new ConfigException(
                                MessageFormat.format(I18N.getString(
                                        "message.wrong-tool-version"), name,
                                        version, minimalVersion),
                                I18N.getString("error.no-wix-tools.advice")));

        final Function<Stream<String>, String> versionParser;

        if (getWix3Tools().contains(this)) {
            toolValidator.setCommandLine("/?");
            versionParser = output -> {
                String firstLineOfOutput = output.findFirst().orElse("");
                int separatorIdx = firstLineOfOutput.lastIndexOf(' ');
                if (separatorIdx == -1) {
                    return null;
                }
                return firstLineOfOutput.substring(separatorIdx + 1);
            };
        } else {
            toolValidator.setCommandLine("--version");
            versionParser = output -> {
                // "wix --version" command prints out "5.0.0+41e11442".
                // Strip trailing "+41e11442" as it breaks version parser.
                return output.findFirst().orElse("").split("\\+", 2)[0];
            };
        }

        toolValidator.setVersionParser(output -> {
            var version = versionParser.apply(output);
            versionConsumer.accept(version);
            return version;
        });

        return toolValidator::validate;
    }

    private static Path getSystemDir(String envVar, String knownDir) {
        return Optional
                .ofNullable(getEnvVariableAsPath(envVar))
                .orElseGet(() -> Optional
                .ofNullable(getEnvVariableAsPath("SystemDrive"))
                .orElseGet(() -> Path.of("C:")).resolve(knownDir));
    }

    private static Path getEnvVariableAsPath(String envVar) {
        String path = System.getenv(envVar);
        if (path != null) {
            try {
                return Path.of(path);
            } catch (InvalidPathException ex) {
                Log.error(MessageFormat.format(I18N.getString(
                        "error.invalid-envvar"), envVar));
            }
        }
        return null;
    }

    private static List<Path> findWixInstallDirs() {
        return Stream.of(findWixCurrentInstallDirs(), findWix3InstallDirs()).
                flatMap(List::stream).toList();
    }

    private static List<Path> findWixCurrentInstallDirs() {
        return Stream.of(getEnvVariableAsPath("USERPROFILE").resolve(
                ".dotnet/tools")).filter(Files::isDirectory).toList();
    }

    private static List<Path> findWix3InstallDirs() {
        PathMatcher wixInstallDirMatcher = FileSystems.getDefault().
                getPathMatcher(
                        "glob:WiX Toolset v*");

        Path programFiles = getSystemDir("ProgramFiles", "\\Program Files");
        Path programFilesX86 = getSystemDir("ProgramFiles(x86)",
                "\\Program Files (x86)");

        // Returns list of WiX install directories ordered by WiX version number.
        // Newer versions go first.
        return Stream.of(programFiles, programFilesX86).map(path -> {
            try (var paths = Files.walk(path, 1)) {
                return paths.toList();
            } catch (IOException ex) {
                Log.verbose(ex);
                List<Path> empty = List.of();
                return empty;
            }
        }).flatMap(List::stream)
                .filter(path -> wixInstallDirMatcher.matches(path.getFileName())).
                sorted(Comparator.comparing(Path::getFileName).reversed())
                .map(path -> path.resolve("bin"))
                .toList();
    }

    private final Path toolFileName;
    private final DottedVersion minimalVersion;
}
