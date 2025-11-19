/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;
import static jdk.jpackage.internal.util.function.ThrowingRunnable.toRunnable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.WixTool.ToolInfo;
import jdk.jpackage.internal.WixToolset.WixToolsetType;
import jdk.jpackage.internal.resources.ResourceLocator;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.test.Annotations.ParameterSupplier;
import jdk.jpackage.test.Annotations.Test;
import jdk.jpackage.test.CfgFile;
import jdk.jpackage.test.JUnitAdapter;
import jdk.jpackage.test.JarBuilder;
import jdk.jpackage.test.TKit;


public class WixToolTest extends JUnitAdapter {

    @Test(ifOS = OperatingSystem.WINDOWS)
    @ParameterSupplier
    public void test(TestSpec spec) throws IOException {
        spec.run();
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
                // WiX3 (good), WiX4+ (broken)
                TestSpec.build()
                        .expect(toolset().version("3.14.1.8722").put(WixToolsetType.Wix3, "foo"))
                        .tool(tool("foo").candle("3.14.1.8722"))
                        .tool(tool("foo").light("3.14.1.8722"))
                        .tool(MockupGenericCommand.build().dir("foo").wix("").stdout("Blah-blah-blah"))
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

        Optional<MockupCommand> map(MockupWixTool.Builder builder) {
            switch (this) {
                case MISSING -> {
                    return Optional.empty();
                }
                case UNEXPECTED_STDOUT -> {
                    var mockup = builder.create();
                    return Optional.of(MockupGenericCommand.build().path(mockup.path()).stdout("Blah-Blah-Blah").create());
                }
                case GOOD -> {
                }
            }

            return Optional.of(builder.create());
        }
    }

    record TestSpec(WixToolset expected, List<Path> lookupDirs, Collection<MockupCommand> specs) {
        TestSpec {
            Objects.requireNonNull(expected);

            if (lookupDirs.isEmpty() || specs.isEmpty()) {
                throw new IllegalArgumentException();
            }

            lookupDirs.forEach(WixToolTest::assertIsRelative);

            // Ensure tool paths are unique.
            specs.stream().map(MockupCommand::path).collect(toMap(x -> x, x -> x));
        }

        void run() throws IOException {
            var workDir = TKit.workDir();

            var jar = workDir.resolve("mockups.jar");

            var jarBuilder = new JarBuilder().setOutputJar(jar);
            for (var c : List.of(MockupWixTool.MAIN_CLASS_NAME, MockupGenericCommand.MAIN_CLASS_NAME)) {
                jarBuilder.addSourceFile(TKit.TEST_SRC_ROOT.resolve(String.format("apps/%s.java", c)));
            }
            jarBuilder.create();

            for (var spec : specs) {
                spec.create(workDir, jar);
            }

            var toolset = WixTool.createToolset(() -> {
                return lookupDirs.stream().map(workDir::resolve).toList();
            }, false);

            for (var spec : specs) {
                var resolvedErrFile = workDir.resolve(spec.errorFilePath());
                if (Files.isRegularFile(resolvedErrFile)) {
                    var content = Files.readAllLines(resolvedErrFile);
                    content.forEach(System.err::println);
                    fail(String.format("Error file [%s] exists"));
                }
            }

            assertEquals(resolveAt(expected, workDir), toolset);
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
                                tools.stream().map(MockupCommand::path).map(Path::getParent)
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

            Builder tool(MockupCommand v) {
                tools.add(Objects.requireNonNull(v));
                return this;
            }

            Builder tool(MockupWixTool.Builder builder) {
                return tool(builder.create());
            }

            Builder tool(MockupGenericCommand.Builder builder) {
                return tool(builder.create());
            }

            private WixToolset expected;
            private List<Path> lookupDirs = new ArrayList<>();
            private List<MockupCommand> tools = new ArrayList<>();
        }
    }

    private sealed interface MockupCommand {
        default void create(Path root, Path mockupCommandJar) throws IOException {
            var resolvedPath = root.resolve(path());

            Files.createDirectories(resolvedPath.getParent());
            try (var in = ResourceLocator.class.getResourceAsStream("jpackageapplauncher.exe")) {
                Files.copy(in, resolvedPath);
            }

            var cfgFilePath = resolvedPath.getParent().resolve("app").resolve(PathUtils.replaceSuffix(resolvedPath.getFileName(), ".cfg"));
            var cfgFile = new CfgFile();

            var mainClass = mainClass();

            cfgFile.addValue("Application", "app.classpath", PathUtils.normalizedAbsolutePathString(mockupCommandJar));
            cfgFile.addValue("Application", "app.mainclass", mainClass);
            cfgFile.addValue("Application", "app.runtime", System.getProperty("java.home"));

            var errorFile = PathUtils.normalizedAbsolutePathString(root.resolve(errorFilePath()));

            Stream.of(
                    Stream.of(Map.entry("error-file", errorFile)),
                    javaProperties(resolvedPath).entrySet().stream()
            ).flatMap(x -> x).forEach(e -> {
                var str = String.format("-Djpackage.test.%s.%s=%s", mainClass, e.getKey(), Objects.requireNonNull(e.getValue()));
                cfgFile.addValue("JavaOptions", "java-options", str);
            });

            Files.createDirectories(cfgFilePath.getParent());
            cfgFile.save(cfgFilePath);
        }

        default Path errorFilePath() {
            return path().getParent().resolve(PathUtils.replaceSuffix(path().getFileName(), ".error"));
        }

        Path path();
        String mainClass();
        Map<String, String> javaProperties(Path resolvedPath);
    }

    private record MockupWixTool(Path dir, WixTool type, String version, boolean fips) implements MockupCommand {

        MockupWixTool {
            Objects.requireNonNull(dir);
            Objects.requireNonNull(type);
            Objects.requireNonNull(version);
        }

        @Override
        public Path path() {
            return dir.resolve(type.fileName());
        }

        @Override
        public String mainClass() {
            return MAIN_CLASS_NAME;
        }

        @Override
        public Map<String, String> javaProperties(Path resolvedPath) {
            return Map.of(
                    "version", version,
                    "fips", Boolean.toString(fips),
                    "type", type.name().toUpperCase()
            );
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            MockupCommand create() {
                return new MockupWixTool(dir, type, version, fips);
            }

            Builder fips(Boolean v) {
                fips = v;
                return this;
            }

            Builder fips() {
                return fips(true);
            }

            Builder dir(Path v) {
                dir = v;
                return this;
            }

            Builder type(WixTool v) {
                type = v;
                return this;
            }


            Builder version(String v) {
                version = v;
                return this;
            }

            Builder candle(String version) {
                return type(WixTool.Candle3).version(version);
            }

            Builder light(String version) {
                return type(WixTool.Light3).version(version);
            }

            Builder wix(String version) {
                return type(WixTool.Wix4).version(version);
            }

            private Path dir;
            private WixTool type;
            private String version;
            private boolean fips;
        }

        static final String MAIN_CLASS_NAME = "MockupWixTool";
    }

    private record MockupGenericCommand(Path path, List<String> stdout, List<String> stderr, int exitCode) implements MockupCommand {

        MockupGenericCommand {
            Objects.requireNonNull(path);
            Objects.requireNonNull(stdout);
            Objects.requireNonNull(stderr);
        }

        @Override
        public String mainClass() {
            return MAIN_CLASS_NAME;
        }

        @Override
        public Map<String, String> javaProperties(Path resolvedPath) {
            Function<String, Path> dataFilePath = role -> {
                return resolvedPath.getParent().resolve(String.format("%s-%s.txt",
                        PathUtils.replaceSuffix(resolvedPath.getFileName(), ""), Objects.requireNonNull(role)));
            };

            var props = new HashMap<String, String>();

            if (!stdout.isEmpty()) {
                var file = dataFilePath.apply("stdout");
                TKit.createTextFile(file, stdout);
                props.put("stdout-file", PathUtils.normalizedAbsolutePathString(file));
            }

            if (!stderr.isEmpty()) {
                var file = dataFilePath.apply("stderr");
                TKit.createTextFile(file, stderr);
                props.put("stderr-file", PathUtils.normalizedAbsolutePathString(file));
            }

            if (exitCode != 0) {
                props.put("exit", Integer.toString(exitCode));
            }

            return props;
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            MockupCommand create() {
                return new MockupGenericCommand(
                        dir.resolve(name),
                        stdout,
                        stderr,
                        exitCode);
            }

            Builder dir(Path v) {
                dir = v;
                return this;
            }

            Builder dir(String v) {
                return dir(Path.of(v));
            }

            Builder name(Path v) {
                name = v;
                return this;
            }

            Builder name(String v) {
                return name(Path.of(v));
            }

            Builder path(Path v) {
                return dir(v.getParent()).name(v.getFileName());
            }

            Builder stdout(String... lines) {
                stdout = List.of(lines);
                return this;
            }

            Builder stderr(String... lines) {
                stderr = List.of(lines);
                return this;
            }

            Builder exitCode(int v) {
                exitCode = v;
                return this;
            }

            Builder candle(String version) {
                return exitCode(0).stderr().stdout(
                        "Windows Installer XML Toolset Compiler version " + Objects.requireNonNull(version),
                        "Copyright (c) .NET Foundation and contributors. All rights reserved.",
                        "",
                        " usage:  candle.exe [-?] [-nologo] [-out outputFile] sourceFile [sourceFile ...] [@responseFile]"
                ).name(WixTool.Candle3.fileName());
            }

            Builder light(String version) {
                return exitCode(0).stderr().stdout(
                        "Windows Installer XML Toolset Linker version " + Objects.requireNonNull(version),
                        "Copyright (c) .NET Foundation and contributors. All rights reserved.",
                        "",
                        " usage:  light.exe [-?] [-b bindPath] [-nologo] [-out outputFile] objectFile [objectFile ...] [@responseFile]"
                ).name(WixTool.Light3.fileName());
            }

            Builder wix(String version) {
                return exitCode(0).stderr().stdout(version).name(WixTool.Wix4.fileName());
            }

            private Path dir;
            private Path name;
            private List<String> stdout = List.of();
            private List<String> stderr = List.of();
            private int exitCode;
        }

        static final String MAIN_CLASS_NAME = "MockupCommand";
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

    private static MockupWixTool.Builder tool() {
        return MockupWixTool.build();
    }

    private static MockupWixTool.Builder tool(Path dir) {
        return tool().dir(dir);
    }

    private static MockupWixTool.Builder tool(String dir) {
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

    static {
        // Ensure JUnitAdapter class is initialized to get the value of the "test.src"
        // property set when the test is executed by a test runner other than jtreg.
        toRunnable(() -> MethodHandles.lookup().ensureInitialized(JUnitAdapter.class)).run();
    }
}

