/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.WixTool.ToolInfo;
import jdk.jpackage.internal.WixToolset.WixToolsetType;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.util.TokenReplace;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.JPackageStringBundle;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMock;
import jdk.jpackage.test.mock.CommandMockSpec;
import jdk.jpackage.test.mock.Script;
import jdk.jpackage.test.stdmock.EnvironmentProviderMock;
import jdk.jpackage.test.stdmock.JPackageMockUtils;
import jdk.jpackage.test.stdmock.WixToolMock;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


class WixToolTest {

    @ParameterizedTest
    @MethodSource
    void testLookup(TestSpec spec, @TempDir Path workDir) throws IOException {
        spec.run(workDir);
    }

    @ParameterizedTest
    @MethodSource
    void testLookupDirs(EnvironmentTestSpec spec, @TempDir Path workDir) throws IOException {
        spec.run(workDir);
    }

    private static Collection<TestSpec> testLookup() {

        List<TestSpec> testCases = new ArrayList<>();

        Consumer<TestSpec.Builder> appendTestCases = builder -> {
            testCases.add(builder.create());
        };

        Stream.of(
                // Simple WiX3 of a minimal acceptable version
                TestSpec.build()
                        .expect(toolset().version("3.0").put(WixToolsetType.Wix3, "foo"))
                        .tool(tool("foo").candle("3.0"))
                        .tool(tool("foo").light("3.0")),
                // Simple WiX3 with FIPS
                TestSpec.build()
                        .expect(toolset().version("3.14.1.8722").put(WixToolsetType.Wix3, "foo").fips())
                        .tool(tool("foo").candle("3.14.1.8722").fips())
                        .tool(tool("foo").light("3.14.1.8722")),
                // Simple WiX4+ of a minimal acceptable version
                TestSpec.build()
                        .expect(toolset().version("4.0.4").put(WixToolsetType.Wix4, "foo"))
                        .tool(tool("foo").wix("4.0.4")),
                // WiX3 with light and candle from different directories and non-existent directory
                TestSpec.build()
                        .expect(toolset().version("3.11.2").put(WixTool.Candle3, "foo").put(WixTool.Light3, "bar"))
                        .lookupDir("buz")
                        .tool(tool("foo").candle("3.11.2"))
                        .tool(tool("bar").light("3.11.2"))
                        .tool(tool("bar").candle("3.11.1"))
                        .tool(tool("foo").light("3.11.1")),
                // WiX3, WiX4+ same directory
                TestSpec.build()
                        .expect(toolset().version("5.0.2+aa65968c").put(WixToolsetType.Wix4, "foo"))
                        .tool(tool("foo").candle("3.14.1.8722"))
                        .tool(tool("foo").light("3.14.1.8722"))
                        .tool(tool("foo").wix("5.0.2+aa65968c")),
                // WiX3 (good), WiX4+ (bad version)
                TestSpec.build()
                        .expect(toolset().version("3.14.1.8722").put(WixToolsetType.Wix3, "foo"))
                        .tool(tool("foo").candle("3.14.1.8722"))
                        .tool(tool("foo").light("3.14.1.8722"))
                        .tool(tool("foo").wix("Blah-blah-blah")),
                // WiX3 (incomplete), WiX4+ (good)
                TestSpec.build()
                        .expect(toolset().version("5.0").put(WixToolsetType.Wix4, "foo"))
                        .tool(tool("foo").candle("3.14.1.8722"))
                        .tool(tool("foo").wix("5.0")),
                // WiX5 in the PATH and in the directory, same version; PATH always wins
                TestSpec.build()
                        .expect(toolset().version("5.0").put(WixToolsetType.Wix4))
                        .tool(tool().wix("5.0"))
                        .tool(tool("foo").wix("5.0")),
                 // WiX5 in the PATH and in the directory; the one in the directory is newer; PATH always wins
                 TestSpec.build()
                         .expect(toolset().version("5.0").put(WixToolsetType.Wix4))
                         .tool(tool().wix("5.0"))
                         .tool(tool("foo").wix("5.1")),
                 // WiX5 in the PATH and in the directory; the one in the PATH is newer; PATH always wins
                 TestSpec.build()
                         .expect(toolset().version("5.1").put(WixToolsetType.Wix4))
                         .tool(tool().wix("5.1"))
                         .tool(tool("foo").wix("5.0")),
                 // WiX3 in the PATH, WiX3 in the directory; PATH always wins
                 TestSpec.build()
                         .expect(toolset().version("3.20").put(WixToolsetType.Wix3))
                         .tool(tool().candle("3.20"))
                         .tool(tool().light("3.20"))
                         .tool(tool("foo").wix("5.0")),
                 // Old WiX3 in the PATH, WiX3 in the directory
                 TestSpec.build()
                         .expect(toolset().version("3.20").put(WixToolsetType.Wix3, "foo"))
                         .tool(tool().candle("2.9"))
                         .tool(tool().light("2.9"))
                         .tool(tool("foo").candle("3.20"))
                         .tool(tool("foo").light("3.20"))
        ).forEach(appendTestCases);

        for (var oldLightStatus : ToolStatus.values()) {
            for (var oldCandleStatus : ToolStatus.values()) {
                for (var newLightStatus : ToolStatus.values()) {
                    for (var newCandleStatus : ToolStatus.values()) {
                        boolean newGood = ToolStatus.isAllGood(newLightStatus, newCandleStatus);
                        if (!ToolStatus.isAllGood(oldLightStatus, oldCandleStatus) && !newGood) {
                            continue;
                        }

                        var builder = TestSpec.build();
                        if (newGood) {
                            builder.expect(toolset().version("3.14").put(WixToolsetType.Wix3, "new"));
                        } else {
                            builder.expect(toolset().version("3.11").put(WixToolsetType.Wix3, "old"));
                        }

                        oldCandleStatus.map(tool("old").candle("3.11")).ifPresent(builder::tool);
                        oldLightStatus.map(tool("old").light("3.11")).ifPresent(builder::tool);

                        newCandleStatus.map(tool("new").candle("3.14")).ifPresent(builder::tool);
                        newLightStatus.map(tool("new").light("3.14")).ifPresent(builder::tool);

                        appendTestCases.accept(builder);
                    }
                }
            }
        }

        Stream.of(
                // No WiX tools
                TestSpec.build(),
                TestSpec.build()
                        .lookupDir("foo"),
                TestSpec.build()
                        .lookupDir(LOOKUP_IN_PATH),
                // Incomplete WiX3: missing candle.exe
                TestSpec.build()
                        .tool(tool("foo").light("3.14.1.8722")),
                // Incomplete WiX3: missing light.exe
                TestSpec.build()
                        .tool(tool("foo").candle("3.14.1.8722")),
                // Incomplete WiX3: version mismatch of light.exe and candle.exe
                TestSpec.build()
                        .tool(tool("foo").candle("3.14"))
                        .tool(tool("foo").light("3.15")),
                // WiX3 too old
                TestSpec.build()
                        .tool(tool("foo").candle("2.9"))
                        .tool(tool("foo").light("2.9")),
                // WiX4+ too old
                TestSpec.build()
                        .tool(tool("foo").wix("4.0.3"))
        ).forEach(appendTestCases);

        return testCases;
    }

    private static Collection<EnvironmentTestSpec> testLookupDirs() {

        List<EnvironmentTestSpec> testCases = new ArrayList<>();

        Stream.of(
                EnvironmentTestSpec.build()
                        .env(EnvironmentVariable.USERPROFILE, "@@/foo")
                        .expect("@USERPROFILE@/.dotnet/tools"),
                EnvironmentTestSpec.build()
                        .env(SystemProperty.USER_HOME, "@@/bar")
                        .expect("@user.home@/.dotnet/tools"),
                // "USERPROFILE" environment variable and "user.home" system property set to different values,
                // the order should be "USERPROFILE" followed by "user.home".
                EnvironmentTestSpec.build()
                        .env(EnvironmentVariable.USERPROFILE, "@@/foo")
                        .env(SystemProperty.USER_HOME, "@@/bar")
                        .expect("@USERPROFILE@/.dotnet/tools")
                        .expect("@user.home@/.dotnet/tools"),
                // "USERPROFILE" environment variable and "user.home" system property set to the same value.
                EnvironmentTestSpec.build()
                        .env(EnvironmentVariable.USERPROFILE, "@@/buz")
                        .env(SystemProperty.USER_HOME, "@@/buz")
                        .expect("@USERPROFILE@/.dotnet/tools"),
                // WiX3: newer versions first; 32bit after 64bit
                EnvironmentTestSpec.build()
                        .standardEnv(EnvironmentVariable.PROGRAM_FILES_X86)
                        .standardEnv(EnvironmentVariable.PROGRAM_FILES)
                        .expect(String.format("@%s@/WiX Toolset v3.11/bin", EnvironmentVariable.PROGRAM_FILES_X86.variableName()))
                        .expect(String.format("@%s@/WiX Toolset v3.10/bin", EnvironmentVariable.PROGRAM_FILES.variableName()))
                        .expect(String.format("@%s@/WiX Toolset v3.10/bin", EnvironmentVariable.PROGRAM_FILES_X86.variableName())),
                // Malformed installation directory should be accepted
                EnvironmentTestSpec.build()
                        .standardEnv(EnvironmentVariable.PROGRAM_FILES_X86)
                        .expect(String.format("@%s@/WiX Toolset vb/bin", EnvironmentVariable.PROGRAM_FILES_X86.variableName()))
                        .expect(String.format("@%s@/WiX Toolset va/bin", EnvironmentVariable.PROGRAM_FILES_X86.variableName()))
                        .expect(String.format("@%s@/WiX Toolset v/bin", EnvironmentVariable.PROGRAM_FILES_X86.variableName())),
                // No directories
                EnvironmentTestSpec.build()
        ).map(EnvironmentTestSpec.Builder::create).forEach(testCases::add);

        return testCases;
    }

    private enum ToolStatus {
        GOOD,
        MISSING,
        UNEXPECTED_STDOUT,
        ;

        static boolean isAllGood(ToolStatus... status) {
            return Stream.of(status).allMatch(Predicate.isEqual(GOOD));
        }

        Optional<CommandMockSpec> map(WixToolMock builder) {
            return switch (this) {
                case MISSING -> {
                    yield Optional.empty();
                }
                case UNEXPECTED_STDOUT -> {
                    var mock = builder.create();
                    yield Optional.of(new CommandMockSpec(
                            mock.name(),
                            mock.mockName(),
                            CommandActionSpecs.build().stdout("Blah-Blah-Blah").exit().create()));
                }
                case GOOD -> {
                    yield Optional.of(builder.create());
                }
            };
        }
    }

    record TestSpec(
            Optional<WixToolset> expected,
            List<Path> lookupDirs,
            boolean lookupInPATH,
            Collection<CommandMockSpec> mocks,
            List<CannedFormattedString> expectedErrors) {

        TestSpec {
            Objects.requireNonNull(expected);
            lookupDirs.forEach(Objects::requireNonNull);
            mocks.forEach(Objects::requireNonNull);
            expectedErrors.forEach(Objects::requireNonNull);

            if (expected.isEmpty() == expectedErrors.isEmpty()) {
                // It should be either toolset or errors, not both or non both.
                throw new IllegalArgumentException();
            }

            lookupDirs.forEach(WixToolTest::assertIsRelative);

            lookupDirs.forEach(path -> {
                assertNotEquals(LOOKUP_IN_PATH, path);
            });

            // Ensure tool paths are unique.
            mocks.stream().map(CommandMockSpec::name).collect(toMap(x -> x, x -> x));
        }

        @Override
        public String toString() {
            var tokens = new ArrayList<String>();
            expected.map(Object::toString).ifPresent(tokens::add);
            if (!expectedErrors.isEmpty()) {
                tokens.add(String.format("errors=%s", expectedErrors));
            }

            List<Path> lookupPaths;
            if (lookupInPATH) {
                lookupPaths = new ArrayList<>();
                lookupPaths.add(Path.of("${PATH}"));
                lookupPaths.addAll(lookupDirs);
            } else {
                lookupPaths = lookupDirs;
            }

            if (!lookupPaths.isEmpty()) {
                tokens.add(String.format("lookup-dirs=%s", lookupPaths));
            }
            if (!mocks.isEmpty()) {
                tokens.add(mocks.toString());
            }
            return String.join(", ", tokens);
        }

        void run(Path workDir) {
            var scriptBuilder = Script.build().commandMockBuilderMutator(CommandMock.Builder::repeatInfinitely);
            mocks.stream().map(mockSpec -> {
                Path toolPath = mockSpec.name();
                if (toolPath.getNameCount() > 1) {
                    toolPath = workDir.resolve(toolPath);
                }
                return new CommandMockSpec(toolPath, mockSpec.mockName(), mockSpec.actions());
            }).forEach(scriptBuilder::map);

            scriptBuilder.map(_ -> true, CommandMock.ioerror("non-existent"));

            var script = scriptBuilder.createLoop();

            Supplier<WixToolset> createToolset = () -> {
                return WixTool.createToolset(() -> {
                    return lookupDirs.stream().map(workDir::resolve).toList();
                }, lookupInPATH());
            };

            Globals.main(() -> {
                JPackageMockUtils.buildJPackage()
                        .script(script)
                        .listener(System.out::println)
                        .applyToGlobals();

                expected.ifPresentOrElse(expectedToolset -> {
                    var toolset = createToolset.get();
                    assertEquals(resolveAt(expectedToolset, workDir), toolset);
                }, () -> {
                    var ex = assertThrows(RuntimeException.class, createToolset::get);
                    assertEquals(expectedErrors.getFirst().getValue(), ex.getMessage());
                    if (ex instanceof ConfigException cfgEx) {
                        assertEquals(expectedErrors.getLast().getValue(), cfgEx.getAdvice());
                        assertEquals(2, expectedErrors.size());
                    } else {
                        assertEquals(1, expectedErrors.size());
                    }
                });

                return 0;
            });
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            TestSpec create() {
                if (expected == null && expectedErrors.isEmpty()) {
                    return copy()
                            .expect("error.no-wix-tools")
                            .expect("error.no-wix-tools.advice")
                            .create();
                } else {
                    var allLookupDirs = Stream.concat(
                            lookupDirs.stream(),
                            tools.stream().map(CommandMockSpec::name).map(toolPath -> {
                                if (toolPath.getNameCount() == 1) {
                                    return LOOKUP_IN_PATH;
                                } else {
                                    return toolPath.getParent();
                                }
                            })
                    ).distinct().collect(Collectors.toCollection(ArrayList::new));

                    var lookupInPATH = allLookupDirs.contains(LOOKUP_IN_PATH);
                    if (lookupInPATH) {
                        allLookupDirs.remove(LOOKUP_IN_PATH);
                    }

                    return new TestSpec(
                            Optional.ofNullable(expected),
                            Collections.unmodifiableList(allLookupDirs),
                            lookupInPATH,
                            List.copyOf(tools),
                            List.copyOf(expectedErrors));
                }
            }

            Builder copy() {
                return new Builder(this);
            }

            private Builder() {
                expectedErrors = new ArrayList<>();
                lookupDirs = new ArrayList<>();
                tools = new ArrayList<>();
            }

            private Builder(Builder other) {
                expected = other.expected;
                expectedErrors = new ArrayList<>(other.expectedErrors);
                lookupDirs = new ArrayList<>(other.lookupDirs);
                tools = new ArrayList<>(other.tools);
            }

            Builder expect(WixToolset v) {
                expected = v;
                return this;
            }

            Builder expect(String formatKey, Object ... args) {
                expectedErrors.add(JPackageStringBundle.MAIN.cannedFormattedString(formatKey, args));
                return this;
            }

            Builder expect(WixToolsetBuilder builder) {
                return expect(builder.create());
            }

            Builder lookupDir(String v) {
                return lookupDir(Path.of(v));
            }

            Builder lookupDir(Path v) {
                lookupDirs.add(Objects.requireNonNull(v));
                return this;
            }

            Builder tool(CommandMockSpec v) {
                tools.add(Objects.requireNonNull(v));
                return this;
            }

            Builder tool(WixToolMock v) {
                return tool(v.create());
            }

            private WixToolset expected;
            private final List<CannedFormattedString> expectedErrors;
            private final List<Path> lookupDirs;
            private final List<CommandMockSpec> tools;
        }
    }

    private static final class WixToolsetBuilder {

        WixToolset create() {
            return new WixToolset(tools.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> {
                ToolInfo toolInfo = new WixTool.DefaultToolInfo(e.getValue(), version);
                if (e.getKey() == WixTool.Candle3) {
                    toolInfo = new WixTool.DefaultCandleInfo(toolInfo, fips);
                }
                return toolInfo;
            })));
        }

        WixToolsetBuilder version(String v) {
            version = v;
            return this;
        }

        WixToolsetBuilder put(WixTool tool, String path) {
            return put(tool, Path.of(path));
        }

        WixToolsetBuilder put(WixTool tool, Path path) {
            tools.put(Objects.requireNonNull(tool), path.resolve(tool.fileName()));
            return this;
        }

        WixToolsetBuilder put(WixTool tool) {
            return put(tool, LOOKUP_IN_PATH);
        }

        WixToolsetBuilder put(WixToolsetType type, Path path) {
            type.getTools().forEach(tool -> {
                put(tool, path);
            });
            return this;
        }

        WixToolsetBuilder put(WixToolsetType type, String path) {
            return put(type, Path.of(path));
        }

        WixToolsetBuilder put(WixToolsetType type) {
            return put(type, LOOKUP_IN_PATH);
        }

        WixToolsetBuilder fips(boolean v) {
            fips = true;
            return this;
        }

        WixToolsetBuilder fips() {
            return fips(true);
        }

        private Map<WixTool, Path> tools = new HashMap<>();
        private boolean fips;
        private String version;
    }

    enum EnvironmentVariable {
        USERPROFILE("USERPROFILE"),
        PROGRAM_FILES("ProgramFiles"),
        PROGRAM_FILES_X86("ProgramFiles(x86)"),
        SYSTEM_DRIVE("SystemDrive"),
        ;

        EnvironmentVariable(String variableName) {
            this.variableName = Objects.requireNonNull(variableName);
        }

        String variableName() {
            return variableName;
        }

        private final String variableName;
    }

    enum SystemProperty {
        USER_HOME("user.home"),
        ;

        SystemProperty(String propertyName) {
            this.propertyName = Objects.requireNonNull(propertyName);
        }

        String propertyName() {
            return propertyName;
        }

        private final String propertyName;
    }

    record EnvironmentTestSpec(EnvironmentProviderMock env, List<Path> expectedDirs) {

        EnvironmentTestSpec {
            Objects.requireNonNull(env);
            expectedDirs.forEach(dir -> {
                if (dir.isAbsolute()) {
                    throw new IllegalArgumentException();
                }
            });
        }

        @Override
        public String toString() {
            var tokens = new ArrayList<String>();
            tokens.add(String.format("expect=%s", expectedDirs));
            tokens.add(env.toString());
            return String.join(", ", tokens);
        }

        void run(Path workDir) throws IOException {
            var allResolved = resolve(workDir,  Stream.of(
                    env.envVariables().entrySet().stream(),
                    env.systemProperties().entrySet().stream(),
                    expectedDirs.stream().map(Path::toString).map(dir -> {
                        return Map.entry(dir, dir);
                    })
            ).flatMap(x -> x).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

            Function<Supplier<Map<String, String>>, Map<String, String>> filterAllResolved = filterSupplier -> {
                var filter = filterSupplier.get();
                return allResolved.entrySet().stream().filter(e -> {
                    return filter.containsKey(e.getKey());
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            };

            var resolvedEnv = new EnvironmentProviderMock(
                    filterAllResolved.apply(env::envVariables),
                    filterAllResolved.apply(env::systemProperties));

            var resolvedDirs = expectedDirs.stream().map(Path::toString).map(allResolved::get).map(Path::of).toList();

            for (var dir : resolvedDirs) {
                Files.createDirectories(dir);
            }

            Globals.main(() -> {
                Globals.instance().system(resolvedEnv);
                assertEquals(resolvedDirs, WixTool.findWixInstallDirs());
                return 0;
            });
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            EnvironmentTestSpec create() {
                var env = envVariables.entrySet().stream().collect(Collectors.toMap(e -> {
                    return e.getKey().variableName();
                }, Map.Entry::getValue));

                var props = systemProperties.entrySet().stream().collect(Collectors.toMap(e -> {
                    return e.getKey().propertyName();
                }, Map.Entry::getValue));

                return new EnvironmentTestSpec(new EnvironmentProviderMock(env, props), List.copyOf(expectedDirs));
            }

            Builder expect(List<Path> dirs) {
                expectedDirs.addAll(dirs);
                return this;
            }

            Builder expect(Path... dirs) {
                return expect(List.of(dirs));
            }

            Builder expect(String... dirs) {
                return expect(List.of(dirs).stream().map(Path::of).toList());
            }

            Builder env(SystemProperty k, String v) {
                systemProperties.put(Objects.requireNonNull(k), Objects.requireNonNull(v));
                return this;
            }

            Builder env(EnvironmentVariable k, String v) {
                envVariables.put(Objects.requireNonNull(k), Objects.requireNonNull(v));
                return this;
            }

            Builder standardEnv(EnvironmentVariable k) {
                var value = switch (k) {
                    case PROGRAM_FILES -> "Program Files";
                    case PROGRAM_FILES_X86 -> "Program Files(x86)";
                    default -> {
                        throw new IllegalArgumentException();
                    }
                };
                return env(k, "@@/" + value);
            }

            private final Map<EnvironmentVariable, String> envVariables = new HashMap<>();
            private final Map<SystemProperty, String> systemProperties = new HashMap<>();
            private final List<Path> expectedDirs = new ArrayList<>();
        }

        private static Map<String, String> resolve(Path workDir, Map<String, String> props) {

            var tokens = new ArrayList<String>();

            Stream.of(
                    Stream.of(EnvironmentVariable.values()).map(EnvironmentVariable::variableName),
                    Stream.of(SystemProperty.values()).map(SystemProperty::propertyName)
            ).flatMap(x -> x).map(str -> {
                return String.format("@%s@", str);
            }).forEach(tokens::add);

            tokens.add(TOKEN_WORKDIR);

            var tokenReplace = new TokenReplace(tokens.toArray(String[]::new));

            return props.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
                return tokenReplace.recursiveApplyTo(e.getValue(), token -> {
                    if (token.equals(TOKEN_WORKDIR)) {
                        return workDir;
                    } else {
                        return Objects.requireNonNull(props.get(token.substring(1, token.length() - 1)), () -> {
                            return String.format("Unrecognized token: [%s]", token);
                        });
                    }
                });
            }));
        }

        static final String TOKEN_WORKDIR = "@@";
    }

    private static WixToolsetBuilder toolset() {
        return new WixToolsetBuilder();
    }

    private static WixToolMock tool() {
        return new WixToolMock();
    }

    private static WixToolMock tool(Path dir) {
        return tool().dir(dir);
    }

    private static WixToolMock tool(String dir) {
        return tool(Path.of(dir));
    }

    private static WixToolset resolveAt(WixToolset toolset, Path root) {
        return new WixToolset(toolset.tools().entrySet().stream().collect(toMap(Map.Entry::getKey, e -> {
            var toolInfo = e.getValue();

            assertIsRelative(toolInfo.path());

            if (toolInfo.path().getNameCount() == 1) {
                // The tool is picked from the PATH.
                return toolInfo;
            }

            ToolInfo newToolInfo = new WixTool.DefaultToolInfo(root.resolve(toolInfo.path()), toolInfo.version());
            if (toolInfo instanceof WixTool.CandleInfo candleInfo) {
                newToolInfo = new WixTool.DefaultCandleInfo(newToolInfo, candleInfo.fips());
            }
            return newToolInfo;
        })));
    }

    private static void assertIsRelative(Path path) {
        if (path.isAbsolute()) {
            throw new IllegalArgumentException();
        }
    }

    static final Path LOOKUP_IN_PATH = Path.of("");
}

