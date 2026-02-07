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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.jpackage.internal.WixTool.ToolInfo;
import jdk.jpackage.internal.WixToolset.WixToolsetType;
import jdk.jpackage.test.mock.CommandActionSpecs;
import jdk.jpackage.test.mock.CommandMock;
import jdk.jpackage.test.mock.CommandMockSpec;
import jdk.jpackage.test.mock.Script;
import jdk.jpackage.test.stdmock.JPackageMockUtils;
import jdk.jpackage.test.stdmock.WixToolMock;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


class WixToolTest {

    @ParameterizedTest
    @MethodSource
    void test(TestSpec spec, @TempDir Path workDir) throws IOException {
        spec.run(workDir);
    }

    public static Collection<Object[]> test() {
        List<TestSpec.Builder> builders = new ArrayList<>();

        Stream.of(
                // Simple WiX3
                TestSpec.build()
                        .expect(toolset().version("3.14.1.8722").put(WixToolsetType.Wix3, "foo"))
                        .tool(tool("foo").candle("3.14.1.8722"))
                        .tool(tool("foo").light("3.14.1.8722")),
                // Simple WiX3 with FIPS
                TestSpec.build()
                        .expect(toolset().version("3.14.1.8722").put(WixToolsetType.Wix3, "foo").fips())
                        .tool(tool("foo").candle("3.14.1.8722").fips())
                        .tool(tool("foo").light("3.14.1.8722")),
                // Simple WiX4+
                TestSpec.build()
                        .expect(toolset().version("5.0.2+aa65968c").put(WixToolsetType.Wix4, "foo"))
                        .tool(tool("foo").wix("5.0.2+aa65968c")),
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
                        .tool(tool("foo").wix("Blah-blah-blah"))
        ).forEach(builders::add);

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

                        builders.add(builder);
                    }
                }
            }
        }

        return builders.stream().map(TestSpec.Builder::create).map(spec -> {
            return new Object[] {spec};
        }).toList();
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
            switch (this) {
                case MISSING -> {
                    return Optional.empty();
                }
                case UNEXPECTED_STDOUT -> {
                    var mock = builder.create();
                    return Optional.of(new CommandMockSpec(
                            mock.name(),
                            mock.mockName(),
                            CommandActionSpecs.build().stdout("Blah-Blah-Blah").exit().create()));
                }
                case GOOD -> {
                }
            }

            return Optional.of(builder.create());
        }
    }

    record TestSpec(WixToolset expected, List<Path> lookupDirs, Collection<CommandMockSpec> mocks) {
        TestSpec {
            Objects.requireNonNull(expected);

            if (lookupDirs.isEmpty() || mocks.isEmpty()) {
                throw new IllegalArgumentException();
            }

            lookupDirs.forEach(WixToolTest::assertIsRelative);

            // Ensure tool paths are unique.
            mocks.stream().map(CommandMockSpec::name).collect(toMap(x -> x, x -> x));
        }

        @Override
        public String toString() {
            var tokens = new ArrayList<String>();
            tokens.add(expected.toString());
            tokens.add(String.format("lookupDirs=%s", lookupDirs));
            tokens.add(mocks.toString());
            return String.join(", ", tokens);
        }

        void run(Path workDir) {
            var scriptBuilder = Script.build().commandMockBuilderMutator(CommandMock.Builder::repeatInfinitely);
            mocks.stream().map(mockSpec -> {
                return new CommandMockSpec(workDir.resolve(mockSpec.name()), mockSpec.mockName(), mockSpec.actions());
            }).forEach(scriptBuilder::map);

            scriptBuilder.map(_ -> true, CommandMock.ioerror("non-existent"));

            var script = scriptBuilder.createLoop();

            Globals.main(() -> {
                JPackageMockUtils.buildJPackage()
                        .script(script)
                        .listener(System.out::println)
                        .applyToGlobals();

                var toolset = WixTool.createToolset(() -> {
                    return lookupDirs.stream().map(workDir::resolve).toList();
                }, false);

                assertEquals(resolveAt(expected, workDir), toolset);
                return 0;
            });
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            TestSpec create() {
                return new TestSpec(
                        expected,
                        Stream.concat(
                                lookupDirs.stream(),
                                tools.stream().map(CommandMockSpec::name).map(Path::getParent)
                        ).distinct().toList(),
                        tools);
            }

            Builder expect(WixToolset v) {
                expected = v;
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
                tools.add(v.create());
                return this;
            }

            private WixToolset expected;
            private List<Path> lookupDirs = new ArrayList<>();
            private List<CommandMockSpec> tools = new ArrayList<>();
        }
    }

    private final static class WixToolsetBuilder {

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

        WixToolsetBuilder put(WixToolsetType type, Path path) {
            type.getTools().forEach(tool -> {
                put(tool, path);
            });
            return this;
        }

        WixToolsetBuilder put(WixToolsetType type, String path) {
            return put(type, Path.of(path));
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
}

