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
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import jdk.jpackage.internal.WixToolset.WixToolsetType;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.DottedVersion;
import jdk.jpackage.internal.model.Logger;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.Slot;

/**
 * WiX tool.
 */
public enum WixTool {
    Candle3("candle", DottedVersion.lazy("3.6")),
    Light3("light", DottedVersion.lazy("3.6")),
    Wix4("wix", DottedVersion.lazy("4.0.4"));

    WixTool(String commandName, DottedVersion minimalVersion) {
        this.toolFileName = PathUtils.addSuffix(Path.of(commandName), ".exe");
        this.minimalVersion = minimalVersion;
    }

    sealed interface ToolInfo {
        Path path();
        DottedVersion version();
    }

    sealed interface CandleInfo extends ToolInfo {
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

        @Override
        public String toString() {
            return String.format("%s|ver=%s", path, version);
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

        @Override
        public String toString() {
            var sb = new StringBuffer();
            sb.append(path);
            if (fips) {
                sb.append("|fips");
            }
            sb.append("|ver=").append(version);
            return sb.toString();
        }
    }

    static WixToolset createToolset() {
        Function<List<ToolLookupResult>, Map<WixTool, ToolInfo>> conv = lookupResults -> {
            return lookupResults.stream().filter(ToolLookupResult::isValid).collect(groupingBy(lookupResult -> {
                return lookupResult.info().version().toString();
            })).values().stream().filter(sameVersionLookupResults -> {
                var sameVersionTools = sameVersionLookupResults.stream()
                        .map(ToolLookupResult::tool)
                        .collect(toSet());
                if (sameVersionTools.equals(Set.of(Candle3)) || sameVersionTools.equals(Set.of(Light3))) {
                    // There is only one tool from WiX v3 toolset of some version available. Discard it.
                    LOGGER.log(Level.TRACE, "Discard [{0}]: incomplete", sameVersionLookupResults.getFirst().info());
                    return false;
                } else {
                    return true;
                }
            }).flatMap(List::stream).collect(toMap(
                    ToolLookupResult::tool,
                    ToolLookupResult::info,
                    (ToolInfo x, ToolInfo y) -> {
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
            }).filter(Optional::isPresent).map(Optional::get).findFirst();
        };

        var toolsInPath = Stream.of(values()).map(tool -> {
            return ToolLookupResult.lookup(tool, Optional.empty());
        }).filter(Optional::isPresent).map(Optional::get).toList();

        // Try to build a toolset from tools in the PATH first.
        var toolset = createToolset.apply(toolsInPath).orElseGet(() -> {
            // Look up for WiX tools in known locations.
            var toolsInKnownWiXDirs = findWixInstallDirs().stream().map(dir -> {
                return Stream.of(values()).map(tool -> {
                    return ToolLookupResult.lookup(tool, Optional.of(dir));
                }).filter(Optional::isPresent).map(Optional::get);
            }).flatMap(x -> x).toList();

            // Build a toolset found in the PATH and in known locations.
            var allFoundTools = Stream.of(toolsInPath, toolsInKnownWiXDirs)
                    .flatMap(List::stream)
                    .filter(ToolLookupResult::isValid)
                    .toList();

            return createToolset.apply(allFoundTools).orElseThrow(() -> {
                return allFoundTools.stream().map(lookupResult -> {
                    if (lookupResult.versionTooOld()) {
                        return new ConfigException(
                                I18N.format("message.wrong-tool-version",
                                        lookupResult.info().path(),
                                        lookupResult.info().version(),
                                        lookupResult.tool().minimalVersion),
                                I18N.getString("error.no-wix-tools.advice"));
                    } else {
                        return null;
                    }
                }).filter(Objects::nonNull).findAny().orElseGet(() -> {
                    return new ConfigException(
                            I18N.getString("error.no-wix-tools"),
                            I18N.getString("error.no-wix-tools.advice"));
                });
            });
        });

        LOGGER.log(Level.TRACE, "Using {0} WiX Toolkit v{1}", toolset.getType(), toolset.getVersion());
        toolset.getType().getTools().stream().sorted().forEach(tool -> {
            LOGGER.log(Level.TRACE, "{0}: {1}", tool, toolset.getToolPath(tool));
        });

        return toolset;
    }

    private record ToolLookupResult(WixTool tool, ToolInfo info) {

        ToolLookupResult {
            Objects.requireNonNull(tool);
            Objects.requireNonNull(info);
        }

        static Optional<ToolLookupResult> lookup(WixTool tool, Optional<Path> lookupDir) {
            Objects.requireNonNull(tool);
            Objects.requireNonNull(lookupDir);

            lookupDir.ifPresentOrElse(theLookupDir -> {
                LOGGER.log(Level.TRACE, "Look up for {0} in [{1}] directory", tool.toolFileName, theLookupDir);
            }, () -> {
                LOGGER.log(Level.TRACE, "Look up for {0} in the PATH", tool.toolFileName);
            });

            final Path toolPath = lookupDir.map(p -> {
                return p.resolve(tool.toolFileName);
            }).orElse(tool.toolFileName);

            final var validator = new ToolValidator(toolPath).setMinimalVersion(tool.minimalVersion);

            final String printVersionArg;
            switch (tool) {
                case Candle3 -> {
                    // Add '-fips' to make "candle.exe" print help message and return
                    // 0 exit code instead of returning error exit code and printing
                    // "error CNDL0308 : The Federal Information Processing Standard (FIPS) appears to be enabled on the machine..."
                    // error message if FIPS is enabled.
                    // If FIPS is disabled, passing '-fips' parameter still makes
                    // "candle.exe" print help message and return 0 exit code.
                    printVersionArg = "-fips";
                }
                case Light3 -> {
                    printVersionArg = "-?";
                }
                default -> {
                    printVersionArg = "--version";
                }
            }
            validator.setCommandLine(printVersionArg);

            final Function<Stream<String>, Optional<String>> versionParser;
            switch (tool) {
                case Candle3, Light3 -> {
                    versionParser = output -> {
                        return output.findFirst().map(firstLineOfOutput -> {
                            int separatorIdx = firstLineOfOutput.lastIndexOf(' ');
                            if (separatorIdx == -1) {
                                return null;
                            }
                            return firstLineOfOutput.substring(separatorIdx + 1);
                        });
                    };
                }
                default -> {
                    versionParser = output -> {
                        return output.findFirst();
                    };
                }
            }

            final var parsedVersion = Slot.<String>createEmpty();
            validator.setVersionParser(output -> {
                versionParser.apply(output).ifPresent(parsedVersion::set);
                return parsedVersion.find().orElse(null);
            });

            if (validator.validate() == null) {
                // Tool found
                ToolInfo info = new DefaultToolInfo(toolPath, parsedVersion.get());
                if (tool == Candle3) {
                    // Detect FIPS mode
                    var fips = false;
                    try {
                        final var result = Executor.of(toolPath.toString(), "-?").setQuiet(true).saveOutput(true).execute();
                        final var exitCode = result.getExitCode();
                        if (exitCode != 0 /* 308 */) {
                            final var output = result.getOutput();
                            if (!output.isEmpty() && output.get(0).contains("error CNDL0308")) {
                                fips = true;
                            }
                        }
                    } catch (IOException ex) {
                        LOGGER.log(Level.ERROR, () -> {
                            return String.format("Failed to execute [%s] command with '-?' option to detect FIPS mode. Assume FIPS=false", toolPath);
                        }, ex);
                    }
                    info = new DefaultCandleInfo(info, fips);
                }

                LOGGER.log(Level.TRACE, "Found [{0}]", info);

                return Optional.of(new ToolLookupResult(tool, info));
            } else {
                if (parsedVersion.find().isPresent()) {
                    LOGGER.log(Level.TRACE, () -> {
                        return String.format("Discard [%s]: failed validation", new DefaultToolInfo(toolPath, parsedVersion.get()));
                    });
                }
                return Optional.empty();
            }
        }

        boolean versionTooOld() {
            return DottedVersion.compareComponents(info.version(), tool.minimalVersion) < 0;
        }

        boolean isValid() {
            return !versionTooOld();
        }
    }

    private static Path getSystemDir(String envVar, String knownDir) {
        return getEnvVariableAsPath(envVar).orElseGet(() -> {
            return getEnvVariableAsPath("SystemDrive").orElseGet(() -> {
                return Path.of("C:");
            }).resolve(knownDir);
        });
    }

    private static Optional<Path> getEnvVariableAsPath(String envVar) {
        Objects.requireNonNull(envVar);
        return Optional.ofNullable(System.getenv(envVar)).map(v -> {
            try {
                return Path.of(v);
            } catch (InvalidPathException ex) {
                LOGGER.log(Level.ERROR, () -> {
                    return String.format("The value of environment variable '%s' [%s] is not a path", envVar, v);
                }, ex);
                return null;
            }
        });
    }

    private static List<Path> findWixInstallDirs() {
        return Stream.of(
                findWixCurrentInstallDirs(),
                findWix3InstallDirs()
        ).flatMap(List::stream).toList();
    }

    private static List<Path> findWixCurrentInstallDirs() {
        return Stream.of(
                getEnvVariableAsPath("USERPROFILE"),
                Optional.ofNullable(System. getProperty("user.home")).map(Path::of)
        ).filter(Optional::isPresent).map(Optional::get).map(path -> {
            return path.resolve(".dotnet/tools");
        }).filter(Files::isDirectory).distinct().toList();
    }

    private static List<Path> findWix3InstallDirs() {
        var wixInstallDirMatcher = FileSystems.getDefault().getPathMatcher("glob:WiX Toolset v*");

        var programFiles = getSystemDir("ProgramFiles", "\\Program Files");
        var programFilesX86 = getSystemDir("ProgramFiles(x86)", "\\Program Files (x86)");

        // Returns list of WiX install directories ordered by WiX version number.
        // Newer versions go first.
        return Stream.of(programFiles, programFilesX86).map(path -> {
            try (var paths = Files.walk(path, 1)) {
                return paths.toList();
            } catch (IOException ex) {
                LOGGER.log(Level.ERROR, () -> {
                    return String.format("Can not get a listing of [%s] directory", path);
                }, ex);
                return List.<Path>of();
            }
        }).flatMap(List::stream)
                .filter(path -> wixInstallDirMatcher.matches(path.getFileName()))
                .sorted(Comparator.comparing(Path::getFileName).reversed())
                .map(path -> path.resolve("bin"))
                .toList();
    }

    private final Path toolFileName;
    private final DottedVersion minimalVersion;

    private final static System.Logger LOGGER = Logger.MAIN.get();
}
