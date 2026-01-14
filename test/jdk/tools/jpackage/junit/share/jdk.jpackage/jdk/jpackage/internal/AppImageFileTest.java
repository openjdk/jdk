/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.DESCRIPTION;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.LAUNCHER_AS_SERVICE;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.LAUNCHER_NAME;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.LINUX_LAUNCHER_SHORTCUT;
import static jdk.jpackage.internal.cli.StandardAppImageFileOption.MAC_APP_STORE;
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
import java.util.stream.Stream;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.cli.OptionIdentifier;
import jdk.jpackage.internal.cli.OptionValue;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.cli.StandardAppImageFileOption.AppImageFileOptionScope;
import jdk.jpackage.internal.cli.WithOptionIdentifier;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ExternalApplication;
import jdk.jpackage.internal.model.ExternalApplication.LauncherInfo;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LauncherShortcut;
import jdk.jpackage.internal.model.LauncherShortcutStartupDirectory;
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
        assertNotNull(ex.getCause());
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

        AppBuilder appName(String v) {
            appName = Objects.requireNonNull(v);
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

        LauncherBuilder mainlauncher() {
            return mainLauncherBuilder;
        }

        ExternalApplication createExternalApplication(OperatingSystem os) {
            var mainLauncherInfo = mainLauncherBuilder.createLauncherInfo();

            var appOptions = Options.concat(
                    Options.of(Map.of(
                            APP_VERSION, version,
                            NAME, appName,
                            APPCLASS, mainClass)
                    ),
                    extra.asObjectValues(),
                    mainLauncherInfo.asOptions());

            return ExternalApplication.create(
                    appOptions,
                    addLauncherBuilders.stream()
                            .map(LauncherBuilder::createLauncherInfo)
                            .map(LauncherInfo::asOptions).toList(),
                    os);
        }

        private Application createApplication() {
            var mainLauncher = mainLauncherBuilder.createLauncher();

            var fullExtra = extra.asStringValues();
            if (OperatingSystem.isMacOS() && !fullExtra.containsKey(APPCLASS.getName())) {
                fullExtra = new HashMap<>(fullExtra);
                fullExtra.put(APPCLASS.getName(), mainClass);
            }

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
                            mainLauncher,
                            addLauncherBuilders.stream().map(LauncherBuilder::createLauncher).toList()).asList(),
                    fullExtra);
        }

        void createInDir(Path dir) {
            final var app = createApplication();
            final var copy = toSupplier(() -> {
                var layout = DUMMY_LAYOUT.resolveAt(dir);
                new AppImageFile(app).save(layout);
                return AppImageFile.load(layout);
            }).get();

            assertEquals(createExternalApplication(OperatingSystem.current()), copy);
        }


        final class LauncherBuilder {
            private LauncherBuilder(String name) {
                this.name = Optional.of(name);
            }

            private LauncherBuilder() {
                this.name = Optional.empty();
            }

            LauncherBuilder service(boolean v) {
                service = v;
                return this;
            }

            LauncherBuilder description(String v) {
                description = v;
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
                if (!isMainLauncher()) {
                    addLauncherBuilders.add(this);
                }
                return AppBuilder.this;
            }

            private Launcher createLauncher() {
                return new Launcher.Stub(
                        name(),
                        Optional.empty(),
                        List.of(),
                        service,
                        description(),
                        Optional.empty(),
                        null,
                        extra.asStringValues());
            }

            private String name() {
                if (isMainLauncher()) {
                    return Objects.requireNonNull(appName);
                } else {
                    return name.orElseThrow();
                }
            }

            private String description() {
                return Optional.ofNullable(description).orElseGet(this::name);
            }

            private boolean isMainLauncher() {
                return name.isEmpty();
            }

            private LauncherInfo createLauncherInfo() {
                var allProps = new ExtraPropertyBuilder(extra);
                if (service) {
                    allProps.add(LAUNCHER_AS_SERVICE, Boolean.valueOf(service));
                }
                allProps.add(LAUNCHER_NAME, name());
                allProps.add(DESCRIPTION, description());
                return LauncherInfo.create(allProps.asObjectValues());
            }

            private final Optional<String> name;
            private boolean service;
            private String description;
            private final ExtraPropertyBuilder extra = new ExtraPropertyBuilder();
        }


        private static final class ExtraPropertyBuilder {

            ExtraPropertyBuilder() {
            }

            ExtraPropertyBuilder(ExtraPropertyBuilder other) {
                stringValues.putAll(other.stringValues);
                objValues.putAll(other.objValues);
            }

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

            private final Map<String, String> stringValues = new HashMap<>();
            private final Map<WithOptionIdentifier, Object> objValues = new HashMap<>();
        }


        private String version = "1.0";
        private String appName = "Foo";
        private String mainClass = "Main";
        private final ExtraPropertyBuilder extra = new ExtraPropertyBuilder();
        private final LauncherBuilder mainLauncherBuilder = new LauncherBuilder();
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
                return expect(builder.createExternalApplication(os));
            }

            Builder expect(ExternalApplication v) {
                expected = v;
                return this;
            }

            Builder xml(String... xml) {
                xmlData = List.of(xml);
                return this;
            }

            Builder os(OperatingSystem v) {
                os = v;
                return this;
            }

            ReadTestSpec create() {
                return new ReadTestSpec(expected, createXml(os, xmlData.toArray(String[]::new)), os);
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

    private static List<List<String>> testInavlidXml() {
        List<List<String>> data = new ArrayList<>();

        var os = OperatingSystem.current();

        data.addAll(List.of
                (List.of("<foo/>"),
                createValidBodyWithHeader(null, null),
                createValidBodyWithHeader("foo", "foo"),
                createValidBodyWithHeader(null, "foo"),
                createValidBodyWithHeader("foo", null),
                createValidBodyWithHeader(AppImageFile.getPlatform(os), null),
                createValidBodyWithHeader(AppImageFile.getPlatform(os), "foo"),
                createValidBodyWithHeader(null, AppImageFile.getVersion()),
                createValidBodyWithHeader("foo", AppImageFile.getVersion()),
                createXml(os, "<main-launcher></main-launcher>"),
                createXml(os, "<main-launcher>A</main-launcher>"),
                createXml(os, "<add-launcher>A</add-launcher>"),
                createXml(os, createValidBodyWithHeader(AppImageFile.getPlatform(os), AppImageFile.getVersion()).toArray(String[]::new)),
                createWithHeader(AppImageFile.getPlatform(os), AppImageFile.getVersion(), () -> {
                    // Missing 'app-version' element.
                    return List.of(
                            "<main-launcher name='D'>",
                            "  <description>Foo</description>",
                            "</main-launcher>",
                            "<main-class>Hello</main-class>"
                    );
                }),
                createWithHeader(AppImageFile.getPlatform(os), AppImageFile.getVersion(), () -> {
                    // Missing 'description' element in the main launcher.
                    return List.of(
                            "<app-version>321</app-version>",
                            "<main-launcher name='B'/>"
                    );
                }),
                createWithHeader(AppImageFile.getPlatform(os), AppImageFile.getVersion(), () -> {
                    // Missing 'description' element in the additional launcher.
                    return List.of(
                            "<app-version>123</app-version>",
                            "<main-launcher name='B'>",
                            "  <description>Foo</description>",
                            "</main-launcher>",
                            "<main-class>Hello</main-class>",
                            "<add-launcher name='C'/>"
                    );
                })
        ));

        if (OperatingSystem.isMacOS()) {
            data.add(createXml(os, "<main-launcher name='Foo'/>", "<main-class></main-class>"));
        }

        return data;
    }

    private static List<String> createValidBodyWithHeader(String platform, String version) {
        return createWithHeader(platform, version, () -> {
            return List.of(
                    "<main-launcher name='D'>",
                    "  <description>Blah-Blah-Blah</description>",
                    "</main-launcher>",
                    "<app-version>100</app-version>",
                    "<main-class>Hello</main-class>"
            );
        });
    }

    private static List<String> createWithHeader(String platform, String version, Supplier<List<String>> body) {

        var sb = new StringBuilder();
        sb.append("<jpackage-state");
        Optional.ofNullable(platform).ifPresent(v -> {
            sb.append(String.format(" platform=\"%s\"", v));
        });
        Optional.ofNullable(version).ifPresent(v -> {
            sb.append(String.format(" version=\"%s\"", v));
        });
        sb.append(">");

        return Stream.of(List.of(sb.toString()), body.get(), List.of("</jpackage-state>")).flatMap(Collection::stream).toList();
    }


    private static Collection<ReadTestSpec> platformSpecificProperties() {
        var builder = ReadTestSpec.build().xml(
                "<app-version>1.34</app-version>",
                "<main-class>Foo</main-class>",
                "<y/>",
                "<x>property-x</x>",
                "<app-store>False</app-store>",
                "<add-launcher name='add-launcher'>",
                "  <description>Quick brown fox</description>",
                "  <service>true</service>",
                "  <linux-shortcut>true</linux-shortcut>",
                "  <win-shortcut>false</win-shortcut>",
                "  <win-menu>app-dir</win-menu>",
                "</add-launcher>",
                "<main-launcher name='Bar'>",
                "  <description>Bar launcher description</description>",
                "</main-launcher>"
        );

        Supplier<AppBuilder.LauncherBuilder> appBuilder = () -> {
            return build()
                    .mainClass("Foo")
                    .version("1.34")
                    .appName("Bar")
                    .mainlauncher().description("Bar launcher description").commit()
                    .addlauncher("add-launcher").service(true).description("Quick brown fox");
        };

        List<ReadTestSpec> testCases = new ArrayList<>();
        testCases.add(builder.os(OperatingSystem.LINUX).expect(appBuilder.get()
                .addExtra(LINUX_LAUNCHER_SHORTCUT, new LauncherShortcut(LauncherShortcutStartupDirectory.DEFAULT)).commit()).create());

        testCases.add(builder.os(OperatingSystem.WINDOWS).expect(appBuilder.get()
                .addExtra(WIN_LAUNCHER_DESKTOP_SHORTCUT, new LauncherShortcut())
                .addExtra(WIN_LAUNCHER_MENU_SHORTCUT, new LauncherShortcut(LauncherShortcutStartupDirectory.APP_DIR)).commit()).create());

        testCases.add(builder.os(OperatingSystem.MACOS).expect(appBuilder.get().commit()
                .addExtra(MAC_APP_STORE, false)).create());

        return testCases;
    }

    private static Stream<ReadTestSpec> testValidXml() {
        return Stream.concat(platformSpecificProperties().stream(), Stream.of(
                ReadTestSpec.build().expect(
                        build().version("72").mainlauncher().description("Blah-Blah-Blah").commit().appName("Y").mainClass("main.Class")
                ).xml(
                        "<main-launcher name='Y'>",
                        "  <description>Blah-Blah-Blah</description>",
                        "</main-launcher>",
                        "<app-version>72</app-version>",
                        "<main-class>main.Class</main-class>"
                ),
                ReadTestSpec.build().os(OperatingSystem.LINUX).expect(
                        build()
                        .mainlauncher().description("Main launcher description").commit()
                        .addlauncher("another-launcher")
                                .addExtra(LINUX_LAUNCHER_SHORTCUT, new LauncherShortcut(LauncherShortcutStartupDirectory.APP_DIR))
                                .description("another-launcher description")
                                .commit()
                        .addlauncher("service-launcher")
                                .service(true)
                                .description("service-launcher description")
                                .commit()
                ).xml(
                        "<app-version>1.2</app-version>",
                        "<app-version>1.0</app-version>",
                        "<main-class>OverwrittenMain</main-class>",
                        "<main-class>Main</main-class>",
                        "<x>property-x</x>",
                        "<add-launcher name='service-launcher' service='true'>",
                        "  <linux-shortcut><nested>foo</nested></linux-shortcut>",
                        "  <description>service-launcher description</description>",
                        "</add-launcher>",
                        "<add-launcher name='another-launcher'>",
                        "  <linux-shortcut>true</linux-shortcut>",
                        "  <linux-shortcut>app-<!-- This is a comment -->dir</linux-shortcut>",
                        "  <description>another-launcher description</description>",
                        "</add-launcher>",
                        "<main-launcher name='Bar'/>",
                        "<main-launcher name='Foo'>",
                        "  <description>Main launcher description</description>",
                        "</main-launcher>"
                )
        ).map(ReadTestSpec.Builder::create));
    }

    private static ExternalApplication createFromXml(List<String> xmlData, OperatingSystem os, Path appImageDir) throws IOException {
        Path path = AppImageFile.getPathInAppImage(DUMMY_LAYOUT.resolveAt(appImageDir));

        List<String> data = new ArrayList<>();
        data.add("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>");
        data.addAll(xmlData);

        Files.write(path, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        ExternalApplication image = AppImageFile.load(DUMMY_LAYOUT.resolveAt(appImageDir), os);
        return image;
    }

    private static void assertEquals(ExternalApplication expected, ExternalApplication actual) {
        Assertions.assertEquals(OM.map(expected), OM.map(actual));
    }

    private static Map<String, Map<String, Object>> additionaLaunchersAsMap(ExternalApplication file) {
        return file.addLaunchers().stream().collect(toMap(LauncherInfo::name, li -> {
            return toPropertyMap(li.asOptions());
        }));
    }

    private static final List<String> createXml(OperatingSystem os, String ...xml) {
        final List<String> content = new ArrayList<>();
        content.add(String.format("<jpackage-state platform=\"%s\" version=\"%s\">", AppImageFile.getPlatform(os), AppImageFile.getVersion()));
        content.addAll(List.of(xml));
        content.add("</jpackage-state>");
        return content;
    }

    private static Map<String, Object> toPropertyMap(Options options) {
        return options.toMap().entrySet().stream().collect(toMap(e -> {
            return Objects.requireNonNull(OPTIONS.get(e.getKey()));
        }, Map.Entry::getValue));
    }

    @TempDir
    private Path tempFolder;

    private static final ObjectMapper OM;

    private static final ApplicationLayout DUMMY_LAYOUT = ApplicationLayout.build().setAll("").create();

    private final static Map<OptionIdentifier, String> OPTIONS = Stream.of(AppImageFileOptionScope.values())
            .flatMap(AppImageFileOptionScope::options)
            .collect(toMap(OptionValue::id, OptionValue::getName));

    static {
        var app = build().addlauncher("foo").commit().createExternalApplication(OperatingSystem.current());

        OM = ObjectMapper.standard()
                .subst(ExternalApplication.class, "addLaunchers", obj -> {
                    return additionaLaunchersAsMap(obj);
                })
                .subst(ExternalApplication.class, "extra", obj -> {
                    return toPropertyMap(obj.extra());
                })
                .subst(LauncherInfo.class, "extra", obj -> {
                    return toPropertyMap(obj.extra());
                })
                .subst(LauncherInfo.class, "asOptions", obj -> {
                    return toPropertyMap(obj.asOptions());
                })
                .exceptLeafClasses().add(NAME.id().getClass().getName()).apply()
                .exceptSomeMethods(app.getClass()).add("options").apply()
                .exceptSomeMethods(app.mainLauncher().getClass()).add("options").apply()
                .create();
    }
}
