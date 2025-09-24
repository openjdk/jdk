/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.cli.StandardAppImageFileOption.LINUX_LAUNCHER_SHORTCUT;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.MAC_APP_STORE;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.MAC_SIGNED;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.WIN_LAUNCHER_DESKTOP_SHORTCUT;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.WIN_LAUNCHER_MENU_SHORTCUT;
import static jdk.jpackage.internal.cli.StandardOption.APPCLASS;
import static jdk.jpackage.internal.cli.StandardOption.APP_VERSION;
import static jdk.jpackage.internal.cli.StandardOption.NAME;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.WithOptionIdentifier;
import jdk.jpackage.internal.cli.OptionIdentifier;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ExternalApplication;
import jdk.jpackage.internal.model.ExternalApplication.LauncherInfo;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LauncherShortcut;
import jdk.jpackage.internal.model.LauncherShortcutStartupDirectory;
import jdk.jpackage.internal.model.LauncherStartupInfo;
import jdk.jpackage.test.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;


public class AppImageFileTest {

    @Test
    public void testSimple() {
        build().createInDir(tempFolder);
    }

    @ParameterizedTest
    @MethodSource
    public void testArbitraryExtra(Map<String, String> extra) {
        build().addExtra(extra).createInDir(tempFolder);
    }

    @Test
    public void testAdditionalLaunchers() {
        build().addlauncher("T").commit()
                .addlauncher("U").service(true).commit()
                .addlauncher("F").addExtra(Map.of("prop", "one", "prop2", "two", "prop3", "")).commit()
                .createInDir(tempFolder);
    }

    @Test
    public void testMalformedXml() throws IOException {
        var ex = assertThrowsExactly(JPackageException.class, () -> createFromXml(List.of("<a>"), OperatingSystem.current(), tempFolder));
        Assertions.assertEquals(I18N.format("error.malformed-app-image-file", ".jpackage.xml", tempFolder), ex.getMessage());
        assertNotNull(ex.getCause());
    }

    @Test
    public void testNoSuchFile() throws IOException {
        var ex = assertThrowsExactly(JPackageException.class, () -> AppImageFile.load(DUMMY_LAYOUT.resolveAt(tempFolder)));
        Assertions.assertEquals(I18N.format("error.missing-app-image-file", ".jpackage.xml", tempFolder), ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    public void testDirectory() throws IOException {
        Files.createDirectory(AppImageFile.getPathInAppImage(DUMMY_LAYOUT.resolveAt(tempFolder)));

        var ex = assertThrowsExactly(JPackageException.class, () -> AppImageFile.load(DUMMY_LAYOUT.resolveAt(tempFolder)));
        Assertions.assertEquals(I18N.format("error.reading-app-image-file", ".jpackage.xml", tempFolder), ex.getMessage());
        assertNotNull(ex.getCause());
    }

    @Test
    @EnabledOnOs(value = OS.WINDOWS, disabledReason = "Can reliably lock a file using FileLock to cuase an IOException on Windows only")
    @SuppressWarnings("try")
    public void testGenericIOException() throws IOException {

        final var appImageFile = AppImageFile.getPathInAppImage(DUMMY_LAYOUT.resolveAt(tempFolder));
        Files.writeString(appImageFile, "");

        try (var out = new FileOutputStream(appImageFile.toFile()); var lock = out.getChannel().lock()) {
            var ex = assertThrowsExactly(JPackageException.class, () -> AppImageFile.load(DUMMY_LAYOUT.resolveAt(tempFolder)));
            Assertions.assertEquals(I18N.format("error.reading-app-image-file", ".jpackage.xml", tempFolder), ex.getMessage());
            assertNotNull(ex.getCause());
        }
    }

    @ParameterizedTest
    @MethodSource
    public void testInavlidXml(List<String> xmlData) throws IOException {
        var ex = assertThrowsExactly(JPackageException.class, () -> createFromXml(xmlData, OperatingSystem.current(), tempFolder));
        Assertions.assertEquals(I18N.format("error.invalid-app-image-file", ".jpackage.xml", tempFolder), ex.getMessage());
        assertNull(ex.getCause());
    }

    @ParameterizedTest
    @MethodSource
    public void testValidXml(ReadTestSpec testSpec) throws IOException {
        testSpec.test(tempFolder);
    }


    private static final class AppBuilder {

        AppBuilder version(String v) {
            version = Objects.requireNonNull(v);
            return this;
        }

        AppBuilder launcherName(String v) {
            launcherName = Objects.requireNonNull(v);
            return this;
        }

        AppBuilder mainClass(String v) {
            mainClass = Objects.requireNonNull(v);
            return this;
        }

        AppBuilder addExtra(Map<String, String> v) {
            extra.add(v);
            return this;
        }

        <T> AppBuilder addExtra(WithOptionIdentifier option, T value) {
            extra.add(option, value);
            return this;
        }

        LauncherBuilder addlauncher(String name) {
            return new LauncherBuilder(name);
        }

        ExternalApplication createExternalApplication() {
            var mainLauncher = createMaunLauncher();
            var appOptions = Options.concat(Options.of(Map.of(
                    APP_VERSION, version,
                    NAME, mainLauncher.name(),
                    APPCLASS, mainLauncher.startupInfo().orElseThrow().qualifiedClassName())), extra.asObjectValues());
            return ExternalApplication.create(
                    appOptions,
                    addLauncherBuilders.stream()
                            .map(LauncherBuilder::createLauncherInfo)
                            .map(LauncherInfo::asOptions).toList());
        }

        Application createApplication() {
            return new Application.Stub(
                    null,
                    null,
                    version,
                    null,
                    null,
                    Optional.empty(),
                    List.of(),
                    null,
                    Optional.empty(),
                    new ApplicationLaunchers(
                            createMaunLauncher(),
                            addLauncherBuilders.stream().map(LauncherBuilder::createLauncher).toList()).asList(),
                    extra.asStringValues());
        }

        void createInDir(Path dir) {
            final var app = createApplication();
            final var copy = toSupplier(() -> {
                var layout = DUMMY_LAYOUT.resolveAt(dir);
                new AppImageFile(app).save(layout);
                return AppImageFile.load(layout);
            }).get();

            assertEquals(createExternalApplication(), copy);
        }

        private Launcher createMaunLauncher() {
            final var startupInfo = new LauncherStartupInfo.Stub(mainClass, List.of(), List.of(), List.of());
            return new Launcher.Stub(launcherName, Optional.of(startupInfo),
                    List.of(), false, null, Optional.empty(), null, Map.of());
        }


        final class LauncherBuilder {
            private LauncherBuilder(String name) {
                this.name = Objects.requireNonNull(name);
            }

            LauncherBuilder service(boolean v) {
                service = v;
                return this;
            }

            LauncherBuilder addExtra(Map<String, String> v) {
                extra.add(v);
                return this;
            }

            <T> LauncherBuilder addExtra(WithOptionIdentifier option, T value) {
                extra.add(option, value);
                return this;
            }

            AppBuilder commit() {
                addLauncherBuilders.add(this);
                return AppBuilder.this;
            }

            private Launcher createLauncher() {
                return new Launcher.Stub(name, Optional.empty(), List.of(), service,
                        null, Optional.empty(), null, extra.asStringValues());
            }

            private LauncherInfo createLauncherInfo() {
                return new LauncherInfo(name, service, extra.asObjectValues());
            }

            private final String name;
            private boolean service;
            private final ExtraPropertyBuilder extra = new ExtraPropertyBuilder();
        }


        private static final class ExtraPropertyBuilder {

            ExtraPropertyBuilder add(Map<String, String> v) {
                stringValues.putAll(v);
                return this;
            }

            <T> ExtraPropertyBuilder add(WithOptionIdentifier option, T value) {
                objValues.put(option, Objects.requireNonNull(value));
                return this;
            }

            Map<String, String> asStringValues() {
                return Map.copyOf(stringValues);
            }

            Options asObjectValues() {
                return Options.of(objValues);
            }

            private Map<String, String> stringValues = new HashMap<>();
            private Map<WithOptionIdentifier, Object> objValues = new HashMap<>();
        }


        private String version = "1.0";
        private String launcherName = "Foo";
        private String mainClass = "Main";
        private final ExtraPropertyBuilder extra = new ExtraPropertyBuilder();
        private final List<LauncherBuilder> addLauncherBuilders = new ArrayList<>();
    }


    private record ReadTestSpec(ExternalApplication expected, List<String> xmlData, OperatingSystem os) {

        ReadTestSpec {
            Objects.requireNonNull(expected);
            Objects.requireNonNull(xmlData);
            Objects.requireNonNull(os);
        }

        void test(Path appImageDir) throws IOException {
            var actual = createFromXml(xmlData, os, appImageDir);
            assertEquals(expected, actual);
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            Builder expect(AppBuilder builder) {
                return expect(builder.createExternalApplication());
            }

            Builder expect(ExternalApplication v) {
                expected = v;
                return this;
            }

            Builder xml(String... xml) {
                xmlData = createXml(xml);
                return this;
            }

            Builder os(OperatingSystem v) {
                os = v;
                return this;
            }

            ReadTestSpec create() {
                return new ReadTestSpec(expected, xmlData, os);
            }

            private ExternalApplication expected;
            private List<String> xmlData;
            private OperatingSystem os = OperatingSystem.LINUX;
        }
    }


    private static AppBuilder build() {
        return new AppBuilder();
    }

    private static Stream<Map<String, String>> testArbitraryExtra() {
        return Stream.of(Map.of("a", "b"), Map.of("foo", ""));
    }

    private static Stream<List<String>> testInavlidXml() {
        return Stream.of(List.of("<foo/>"),
                createValidBodyWithHeader(null, null),
                createValidBodyWithHeader("foo", "foo"),
                createValidBodyWithHeader(null, "foo"),
                createValidBodyWithHeader("foo", null),
                createValidBodyWithHeader(AppImageFile.getPlatform(), null),
                createValidBodyWithHeader(AppImageFile.getPlatform(), "foo"),
                createValidBodyWithHeader(null, AppImageFile.getVersion()),
                createValidBodyWithHeader("foo", AppImageFile.getVersion()),
                createXml("<main-launcher></main-launcher>"),
                createXml("<main-launcher>Foo</main-launcher>", "<main-class></main-class>"),
                createXml("<add-launcher>A</add-launcher>"),
                createXml(createValidBodyWithHeader(AppImageFile.getPlatform(), AppImageFile.getVersion()).toArray(String[]::new))
        );
    }

    private static List<String> createValidBodyWithHeader(String platform, String version) {

        var sb = new StringBuilder();
        sb.append("<jpackage-state");
        Optional.ofNullable(platform).ifPresent(v -> {
            sb.append(String.format(" platform=\"%s\"", v));
        });
        Optional.ofNullable(version).ifPresent(v -> {
            sb.append(String.format(" version=\"%s\"", v));
        });
        sb.append(">");

        return List.of(
                sb.toString(),
                "<main-launcher>D</main-launcher>",
                "<app-version>100</app-version>",
                "<main-class>Hello</main-class>",
                "</jpackage-state>"
        );
    }


    private static Collection<ReadTestSpec> platformSpecificProperties() {
        var builder = ReadTestSpec.build().xml(
                "<app-version>1.34</app-version>",
                "<main-class>Foo</main-class>",
                "<y/>",
                "<x>property-x</x>",
                "<signed>true</signed>",
                "<app-store>False</app-store>",
                "<add-launcher name='add-launcher' service='true'>",
                "  <linux-shortcut>true</linux-shortcut>",
                "  <win-shortcut>false</win-shortcut>",
                "  <win-menu>app-dir</win-menu>",
                "</add-launcher>",
                "<main-launcher>Bar</main-launcher>"
        );

        Supplier<AppBuilder.LauncherBuilder> appBuilder = () -> {
            return build().mainClass("Foo").version("1.34").launcherName("Bar").addlauncher("add-launcher").service(true);
        };

        List<ReadTestSpec> testCases = new ArrayList<>();
        testCases.add(builder.os(OperatingSystem.LINUX).expect(appBuilder.get()
                .addExtra(LINUX_LAUNCHER_SHORTCUT, new LauncherShortcut(LauncherShortcutStartupDirectory.DEFAULT)).commit()).create());

        testCases.add(builder.os(OperatingSystem.WINDOWS).expect(appBuilder.get()
                .addExtra(WIN_LAUNCHER_DESKTOP_SHORTCUT, new LauncherShortcut())
                .addExtra(WIN_LAUNCHER_MENU_SHORTCUT, new LauncherShortcut(LauncherShortcutStartupDirectory.APP_DIR)).commit()).create());

        testCases.add(builder.os(OperatingSystem.MACOS).expect(appBuilder.get().commit()
                .addExtra(MAC_APP_STORE, false)
                .addExtra(MAC_SIGNED, true)).create());

        return testCases;
    }

    private static Stream<ReadTestSpec> testValidXml() {
        return Stream.concat(platformSpecificProperties().stream(), Stream.of(
                ReadTestSpec.build().expect(
                        build().version("72").launcherName("Y").mainClass("main.Class")
                ).xml(
                        "<main-launcher>Y</main-launcher>",
                        "<app-version>72</app-version>",
                        "<main-class>main.Class</main-class>"
                ),
                ReadTestSpec.build().os(OperatingSystem.LINUX).expect(
                        build().addlauncher("another-launcher").addExtra(LINUX_LAUNCHER_SHORTCUT, new LauncherShortcut(LauncherShortcutStartupDirectory.APP_DIR)).commit()
                        .addlauncher("service-launcher").service(true).commit()
                ).xml(
                        "<app-version>1.2</app-version>",
                        "<app-version>1.0</app-version>",
                        "<main-class>OverwrittenMain</main-class>",
                        "<main-class>Main</main-class>",
                        "<x>property-x</x>",
                        "<signed>true</signed>",
                        "<add-launcher name='service-launcher' service='true'>",
                        "  <linux-shortcut><nested>foo</nested></linux-shortcut>",
                        "</add-launcher>",
                        "<add-launcher name='another-launcher'>",
                        "  <linux-shortcut>true</linux-shortcut>",
                        "  <linux-shortcut>app-<!-- This is a comment -->dir</linux-shortcut>",
                        "</add-launcher>",
                        "<main-launcher>Bar</main-launcher>",
                        "<main-launcher>Foo</main-launcher>"
                )
        ).map(ReadTestSpec.Builder::create));
    }

    private static ExternalApplication createFromXml(List<String> xmlData, OperatingSystem os, Path appImageDir) throws IOException {
        Path path = AppImageFile.getPathInAppImage(DUMMY_LAYOUT.resolveAt(appImageDir));

        List<String> data = new ArrayList<>();
        data.add("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>");
        data.addAll(xmlData);

        Files.write(path, data, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

        ExternalApplication image = AppImageFile.load(DUMMY_LAYOUT.resolveAt(appImageDir), os);
        return image;
    }

    private static void assertEquals(ExternalApplication expected, ExternalApplication actual) {
        Assertions.assertEquals(OM.map(expected), OM.map(actual));
    }

    private static Map<String, Map<OptionIdentifier, Object>> additionaLaunchersAsMap(ExternalApplication file) {
        return file.getAddLaunchers().stream().collect(Collectors.toMap(LauncherInfo::name, li -> {
            return li.asOptions().toMap();
        }));
    }

    private static final List<String> createXml(String ...xml) {
        final List<String> content = new ArrayList<>();
        content.add(String.format("<jpackage-state platform=\"%s\" version=\"%s\">", AppImageFile.getPlatform(), AppImageFile.getVersion()));
        content.addAll(List.of(xml));
        content.add("</jpackage-state>");
        return content;
    }

    @TempDir
    private Path tempFolder;

    private static final ObjectMapper OM = ObjectMapper.standard().subst(ExternalApplication.class, "getAddLaunchers", obj -> {
        return additionaLaunchersAsMap(obj);
    }).subst(ExternalApplication.class, "getExtra", obj -> {
        return obj.getExtra().toMap();
    }).exceptLeafClasses().add(NAME.id().getClass().getName()).apply().create();
    private static final ApplicationLayout DUMMY_LAYOUT = ApplicationLayout.build().setAll("").create();
}
