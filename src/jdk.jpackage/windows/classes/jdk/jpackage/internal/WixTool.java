/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.WixToolset.WixToolsetType;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.DottedVersion;
import jdk.jpackage.internal.util.PathUtils;

/**
 * WiX tool.
 */
public enum WixTool {
    Candle3("candle", DottedVersion.lazy("3.0")),
    Light3("light", DottedVersion.lazy("3.0")),
    Wix4("wix", DottedVersion.lazy("4.0.4"));

    WixTool(String commandName, DottedVersion minimalVersion) {
        this.toolFileName = PathUtils.addSuffix(Path.of(commandName), ".exe");
        this.minimalVersion = minimalVersion;
    }

    interface ToolInfo {
        Path path();
        DottedVersion version();
    }

    interface CandleInfo extends ToolInfo {
        boolean fips();
    }

    private record DefaultToolInfo(Path path, DottedVersion version) implements ToolInfo {
        DefaultToolInfo {
            Objects.requireNonNull(path);
            Objects.requireNonNull(version);
        }

        DefaultToolInfo(Path path, String version) {
            this(path, DottedVersion.lazy(version));
        }
    }

    private record DefaultCandleInfo(Path path, DottedVersion version, boolean fips) implements CandleInfo {
        DefaultCandleInfo {
            Objects.requireNonNull(path);
            Objects.requireNonNull(version);
        }

        DefaultCandleInfo(ToolInfo info, boolean fips) {
            this(info.path(), info.version(), fips);
        }
    }

    static WixToolset createToolset() throws ConfigException {
        Function<List<ToolLookupResult>, Map<WixTool, ToolInfo>> conv = lookupResults -> {
            return lookupResults.stream().filter(ToolLookupResult::isValid).collect(Collectors.
                    groupingBy(lookupResult -> {
                return lookupResult.info().version().toString();
            })).values().stream().filter(sameVersionLookupResults -> {
                Set<WixTool> sameVersionTools = sameVersionLookupResults.stream().map(
                        ToolLookupResult::tool).collect(Collectors.toSet());
                if (sameVersionTools.equals(Set.of(Candle3)) || sameVersionTools.equals(Set.of(
                        Light3))) {
                    // There is only one tool from WiX v3 toolset of some version available. Discard it.
                    return false;
                } else {
                    return true;
                }
            }).flatMap(List::stream).collect(Collectors.toMap(ToolLookupResult::tool,
                    ToolLookupResult::info, (ToolInfo x, ToolInfo y) -> {
                        return Stream.of(x, y).sorted(Comparator.comparing((ToolInfo toolInfo) -> {
                            return toolInfo.version().toComponentsString();
                        }).reversed()).findFirst().get();
                    }));
        };

        Function<List<ToolLookupResult>, Optional<WixToolset>> createToolset = lookupResults -> {
            var tools = conv.apply(lookupResults);
            // Try to build a toolset found in the PATH and in known locations.
            return Stream.of(WixToolsetType.values()).map(toolsetType -> {
                return WixToolset.create(toolsetType.getTools(), tools);
            }).filter(Objects::nonNull).findFirst();
        };

        var toolsInPath = Stream.of(values()).map(tool -> {
            return ToolLookupResult.lookup(tool, Optional.empty());
        }).filter(Optional::isPresent).map(Optional::get).toList();

        // Try to build a toolset from tools in the PATH first.
        var toolset = createToolset.apply(toolsInPath);
        if (toolset.isPresent()) {
            return toolset.get();
        }

        // Look up for WiX tools in known locations.
        var toolsInKnownWiXDirs = findWixInstallDirs().stream().map(dir -> {
            return Stream.of(values()).map(tool -> {
                return ToolLookupResult.lookup(tool, Optional.of(dir));
            });
        }).flatMap(Function.identity()).filter(Optional::isPresent).map(Optional::get).toList();

        // Build a toolset found in the PATH and in known locations.
        var allFoundTools = Stream.of(toolsInPath, toolsInKnownWiXDirs).flatMap(List::stream).filter(
                ToolLookupResult::isValid).toList();
        toolset = createToolset.apply(allFoundTools);
        if (toolset.isPresent()) {
            return toolset.get();
        } else if (allFoundTools.isEmpty()) {
            throw new ConfigException(I18N.getString("error.no-wix-tools"), I18N.getString(
                    "error.no-wix-tools.advice"));
        } else {
            var toolOldVerErr = allFoundTools.stream().map(lookupResult -> {
                if (lookupResult.versionTooOld) {
                    return new ConfigException(MessageFormat.format(I18N.getString(
                            "message.wrong-tool-version"), lookupResult.info().path(),
                            lookupResult.info().version(), lookupResult.tool().minimalVersion),
                            I18N.getString("error.no-wix-tools.advice"));
                } else {
                    return null;
                }
            }).filter(Objects::nonNull).findAny();
            if (toolOldVerErr.isPresent()) {
                throw toolOldVerErr.get();
            } else {
                throw new ConfigException(I18N.getString("error.no-wix-tools"), I18N.getString(
                        "error.no-wix-tools.advice"));
            }
        }
    }

    private record ToolLookupResult(WixTool tool, ToolInfo info, boolean versionTooOld) {

        ToolLookupResult {
            Objects.requireNonNull(tool);
            Objects.requireNonNull(info);
        }

        static Optional<ToolLookupResult> lookup(WixTool tool, Optional<Path> lookupDir) {
            Objects.requireNonNull(tool);
            Objects.requireNonNull(lookupDir);

            final Path toolPath = lookupDir.map(p -> p.resolve(
                    tool.toolFileName)).orElse(tool.toolFileName);

            final boolean[] tooOld = new boolean[1];
            final String[] parsedVersion = new String[1];

            final var validator = new ToolValidator(toolPath).setMinimalVersion(tool.minimalVersion).
                    setToolNotFoundErrorHandler((name, ex) -> {
                        return new ConfigException("", "");
                    }).setToolOldVersionErrorHandler((name, version) -> {
                tooOld[0] = true;
                return null;
            });

            final Function<Stream<String>, String> versionParser;

            if (Set.of(Candle3, Light3).contains(tool)) {
                final String printVersionArg;
                if (tool == Candle3) {
                    // Add '-fips' to make "candle.exe" print help message and return
                    // 0 exit code instead of returning error exit code and printing
                    // "error CNDL0308 : The Federal Information Processing Standard (FIPS) appears to be enabled on the machine..."
                    // error message if FIPS is enabled.
                    // If FIPS is disabled, passing '-fips' parameter still makes
                    // "candle.exe" print help message and return 0 exit code.
                    printVersionArg = "-fips";
                } else {
                    printVersionArg = "-?";
                }
                validator.setCommandLine(printVersionArg);
                versionParser = output -> {
                    String firstLineOfOutput = output.findFirst().orElse("");
                    int separatorIdx = firstLineOfOutput.lastIndexOf(' ');
                    if (separatorIdx == -1) {
                        return null;
                    }
                    return firstLineOfOutput.substring(separatorIdx + 1);
                };
            } else {
                validator.setCommandLine("--version");
                versionParser = output -> {
                    return output.findFirst().orElse("");
                };
            }

            validator.setVersionParser(output -> {
                parsedVersion[0] = versionParser.apply(output);
                return parsedVersion[0];
            });

            if (validator.validate() == null) {
                // Tool found
                ToolInfo info = new DefaultToolInfo(toolPath, parsedVersion[0]);
                if (tool == Candle3) {
                    // Detect FIPS mode
                    var fips = false;
                    try {
                        final var exec = Executor.of(toolPath.toString(), "-?").setQuiet(true).saveOutput(true);
                        final var exitCode = exec.execute();
                        if (exitCode != 0 /* 308 */) {
                            final var output = exec.getOutput();
                            if (!output.isEmpty() && output.get(0).contains("error CNDL0308")) {
                                fips = true;
                            }
                        }
                    } catch (IOException ex) {
                        Log.verbose(ex);
                    }
                    info = new DefaultCandleInfo(info, fips);
                }
                return Optional.of(new ToolLookupResult(tool, info, tooOld[0]));
            } else {
                return Optional.empty();
            }
        }

        boolean isValid() {
            return !versionTooOld;
        }
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
        return Stream.of(getEnvVariableAsPath("USERPROFILE"), Optional.ofNullable(System.
                getProperty("user.home")).map(Path::of).orElse(null)).filter(Objects::nonNull).map(
                path -> {
                    return path.resolve(".dotnet/tools");
                }).filter(Files::isDirectory).distinct().toList();
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
