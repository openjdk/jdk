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
import static jdk.jpackage.internal.util.function.ExceptionBox.visitUnboxedExceptionsRecursively;
import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
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
import jdk.jpackage.internal.model.BundleVersion;
import jdk.jpackage.internal.model.ExternalApplication;
import jdk.jpackage.internal.model.ExternalApplication.LauncherInfo;
import jdk.jpackage.internal.model.JPackageException;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LauncherShortcut;
import jdk.jpackage.internal.model.LauncherShortcutStartupDirectory;
import jdk.jpackage.test.CannedArgument;
import jdk.jpackage.test.CannedFormattedString;
import jdk.jpackage.test.ExceptionPattern;
import jdk.jpackage.test.JPackageStringBundle;
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
        var ex = assertThrowsExactly(JPackageException.class, () -> createFromXml("<a>", OperatingSystem.current(), tempFolder));
        Assertions.assertEquals(I18N.format("error.malformed-app-image-file", ".jpackage.xml", tempFolder), ex.getMessage());
        assertNotNull(ex.getCause());
    }

    @Test
    public void testNoSuchFile() throws IOException {
        var ex = assertThrowsExactly(JPackageException.class, () -> {
            AppImageFile.load(DUMMY_LAYOUT.resolveAt(tempFolder), OperatingSystem.current());
        });
        Assertions.assertEquals(I18N.format("error.missing-app-image-file", ".jpackage.xml", tempFolder), ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    public void testDirectory() throws IOException {
        Files.createDirectory(AppImageFile.getPathInAppImage(DUMMY_LAYOUT.resolveAt(tempFolder)));

        var ex = assertThrowsExactly(JPackageException.class, () -> {
            AppImageFile.load(DUMMY_LAYOUT.resolveAt(tempFolder), OperatingSystem.current());
        });
        Assertions.assertEquals(I18N.format("error.reading-app-image-file", ".jpackage.xml", tempFolder), ex.getMessage());
        assertNotNull(ex.getCause());
    }

    @Test
    @EnabledOnOs(value = OS.WINDOWS, disabledReason = "Can reliably lock a file using FileLock to cause an IOException on Windows only")
    @SuppressWarnings("try")
    public void testGenericIOException() throws IOException {

        final var appImageFile = AppImageFile.getPathInAppImage(DUMMY_LAYOUT.resolveAt(tempFolder));
        Files.writeString(appImageFile, "");

        try (var out = new FileOutputStream(appImageFile.toFile()); var lock = out.getChannel().lock()) {
            var ex = assertThrowsExactly(JPackageException.class, () -> {
                AppImageFile.load(DUMMY_LAYOUT.resolveAt(tempFolder), OperatingSystem.current());
            });
            Assertions.assertEquals(I18N.format("error.reading-app-image-file", ".jpackage.xml", tempFolder), ex.getMessage());
            assertNotNull(ex.getCause());
        }
    }

    @ParameterizedTest
    @MethodSource
    public void testInavlidXml(InvalidXmlTestSpec testSpec) {
        testSpec.test(tempFolder);
    }

    @ParameterizedTest
    @MethodSource
    public void testValidXml(ReadTestSpec testSpec) throws IOException {
        testSpec.test(tempFolder);
    }


    private static final class AppBuilder {

        AppBuilder version(String v) {
            version = BundleVersion.of(v);
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
                return AppImageFile.load(layout, OperatingSystem.current());
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


        private BundleVersion version = BundleVersion.of("1.0");
        private String appName = "Foo";
        private String mainClass = "Main";
        private final ExtraPropertyBuilder extra = new ExtraPropertyBuilder();
        private final LauncherBuilder mainLauncherBuilder = new LauncherBuilder();
        private final List<LauncherBuilder> addLauncherBuilders = new ArrayList<>();
    }


    private record ReadTestSpec(ExternalApplication expected, String xmlData, OperatingSystem os) {

        ReadTestSpec {
            Objects.requireNonNull(expected);
            Objects.requireNonNull(xmlData);
            Objects.requireNonNull(os);
        }

        @Override
        public String toString() {
            return String.format("%s %s %s", toString(expected), os, xmlData);
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

            Builder xml(String v) {
                xmlData = v;
                return this;
            }

            Builder os(OperatingSystem v) {
                os = v;
                return this;
            }

            ReadTestSpec create() {
                return new ReadTestSpec(expected, createXml(os, xmlData), os);
            }

            private ExternalApplication expected;
            private String xmlData;
            private OperatingSystem os = OperatingSystem.LINUX;
        }

        private static String toString(ExternalApplication app) {
            var tokens = new ArrayList<String>();

            tokens.add(String.format("name=[%s]", app.appName()));
            tokens.add(String.format("version=[%s]", app.appVersion()));
            tokens.add(String.format("main=%s", toString(app.mainLauncher().asOptions()).orElseThrow()));
            var addLaunchers = app.addLaunchers();
            if (!addLaunchers.isEmpty()) {
                tokens.add(String.format("launchers=%s", addLaunchers.stream()
                        .map(LauncherInfo::asOptions)
                        .map(ReadTestSpec::toString)
                        .map(Optional::orElseThrow)
                        .toList()));
            }
            toString(app.extra()).ifPresent(v -> {
                tokens.add(String.format("custom=%s", v));
            });

            return String.join(", ", tokens);
        }

        private static Optional<String> toString(Options options) {
            var map = toPropertyMap(options).entrySet().stream().collect(toMap(Map.Entry::getKey, e -> {
                var value = e.getValue().toString();
                if (Objects.equals(e.getKey(), DESCRIPTION.getName())) {
                    value = "[" + value + "]";
                }
                return value;
            }));
            if (map.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(new TreeMap<>(map).toString());
            }
        }
    }


    private record InvalidXmlTestSpec(OperatingSystem os, String xmlData, List<ExceptionPattern> expected) {

        InvalidXmlTestSpec {
            Objects.requireNonNull(os);
            Objects.requireNonNull(xmlData);
            expected.forEach(Objects::requireNonNull);
        }

        @Override
        public String toString() {
            return String.format("%s %s %s", os, expected, xmlData);
        }

        void test(Path appImageDir) {
            Objects.requireNonNull(appImageDir);

            var resolved = expected.stream().map(pattern -> {
                return pattern.resolveCannedArgumentsRecursive(message -> {
                    return switch (message) {
                        case CannedFormattedString canned -> {
                            yield canned.mapArgs(arg -> {
                                if (arg == JPACKAGE_XML) {
                                    return AppImageFile.getPathInAppImage(DUMMY_LAYOUT.resolveAt(appImageDir));
                                } else if (arg == APP_IMAGE_DIR) {
                                    return appImageDir;
                                } else {
                                    return arg;
                                }
                            }).getValue();
                        }
                        default -> message.getValue();
                    };
                });
            }).toList();

            var ex = assertThrows(Exception.class, () -> {
                createFromXml(xmlData, os, appImageDir);
            });

            var unfoldedExceptions = new ArrayList<Exception>();
            visitUnboxedExceptionsRecursively(ex, unfoldedExceptions::add);

            Assertions.assertEquals(resolved.size(), unfoldedExceptions.size());
            IntStream.range(0, resolved.size()).forEach(idx -> {
                resolved.get(idx).match(unfoldedExceptions.get(idx), Assertions::fail);
            });
        }

        static Builder build() {
            return new Builder();
        }

        static final class Builder {

            Builder expect(ExceptionPattern pattern) {
                expected.add(Objects.requireNonNull(pattern));
                return this;
            }

            Builder expect(ExceptionPattern.Builder pattern) {
                return expect(pattern.create());
            }

            Builder xml(String v) {
                xml = v;
                return this;
            }

            Builder os(OperatingSystem v) {
                os = v;
                return this;
            }

            InvalidXmlTestSpec create() {
                return new InvalidXmlTestSpec(os, xml, expected);
            }

            private String xml;
            private OperatingSystem os = OperatingSystem.current();
            private List<ExceptionPattern> expected = new ArrayList<>();
        }
    }


    private static AppBuilder build() {
        return new AppBuilder();
    }

    private static Stream<Map<String, String>> testArbitraryExtra() {
        return Stream.of(Map.of("a", "b"), Map.of("foo", ""));
    }

    private static ExceptionPattern.Builder expect(String key, Object... formatArgs) {
        return ExceptionPattern.build()
                .expectMessage(JPackageStringBundle.MAIN.cannedFormattedString(key, formatArgs))
                .expectType(JPackageException.class);
    }

    private static ExceptionPattern.Builder expectInvalidFile() {
        return expect("error.invalid-app-image-file", ".jpackage.xml", APP_IMAGE_DIR).expectCause(Exception.class);
    }

    private static List<InvalidXmlTestSpec> testInavlidXml() {
        List<InvalidXmlTestSpec> data = new ArrayList<>();

        var os = OperatingSystem.current();

        Consumer<InvalidXmlTestSpec.Builder> addTestCase = v -> {
            data.add(v.create());
        };

        Stream.of(
                "<foo/>",
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
                createXml(os, createValidBodyWithHeader(AppImageFile.getPlatform(os), AppImageFile.getVersion()))
        ).map(xml -> {
            return InvalidXmlTestSpec.build().xml(xml).expect(expectInvalidFile());
        }).forEach(addTestCase);

        Stream.of(
                // Missing 'main-class' element.
                Map.entry("main-class", InvalidXmlTestSpec.build().xml(createWithHeader(
                        AppImageFile.getPlatform(OperatingSystem.MACOS), AppImageFile.getVersion(),
                        """
                        <app-version>1</app-version>
                        <main-launcher name='D'>
                          <description>Foo</description>
                        </main-launcher>
                        """
                )).os(OperatingSystem.MACOS)),
                // Missing 'app-version' element.
                Map.entry("app-version", InvalidXmlTestSpec.build().xml(createWithHeader(
                        """
                        <main-launcher name='D'>
                          <description>Foo</description>
                        </main-launcher>
                        <main-class>Hello</main-class>
                        """
                ))),
                // Missing 'description' element in the main launcher.
                Map.entry("description", InvalidXmlTestSpec.build().xml(createWithHeader(
                        """
                        <app-version>321</app-version>
                        <main-launcher name='B'/>
                        <main-class>Hello</main-class>
                        """
                ))),
                // Missing 'description' element in the additional launcher.
                Map.entry("description", InvalidXmlTestSpec.build().xml(createWithHeader(
                        """
                        <app-version>123</app-version>
                        <main-launcher name='B'>
                          <description>Foo</description>
                        </main-launcher>
                        <main-class>Hello</main-class>
                        <add-launcher name='C'/>
                        """
                )))
        ).map(spec -> {
            var cause = ExceptionPattern.build()
                    .expectMessage(String.format("Missing mandatory '%s' property", spec.getKey()))
                    .expectNullCause()
                    .create();
            return spec.getValue().expect(expectInvalidFile().expectCause(cause));
        }).forEach(addTestCase);

        Stream.of(
                // Empty 'app-version' element.
                InvalidXmlTestSpec.build().xml(createWithHeader(
                        """
                        <main-launcher name='D'>
                          <description>Blah-Blah-Blah</description>
                        </main-launcher>
                        <app-version>7.8</app-version>
                        <app-version></app-version>
                        <main-class>Hello</main-class>
                        """
                )).expect(expect(
                        "error.properties-parameter-invalid-value",
                        "", "/jpackage-state/app-version[2]", JPACKAGE_XML).create()),

                // Invalid app name.
                InvalidXmlTestSpec.build().xml(createWithHeader(
                        """
                        <main-launcher name='*foo*'>
                          <description/>
                        </main-launcher>
                        <app-version>1.0</app-version>
                        <main-class>Hello</main-class>
                        """
                )).expect(expect(
                        "error.properties-parameter-invalid-value",
                        "*foo*", "/jpackage-state/main-launcher/@name", JPACKAGE_XML).create()),

                // Invalid main class
                InvalidXmlTestSpec.build().os(OperatingSystem.MACOS).xml(createWithHeader(
                        AppImageFile.getPlatform(OperatingSystem.MACOS), AppImageFile.getVersion(),
                        """
                        <main-launcher name='foo'>
                          <description/>
                        </main-launcher>
                        <app-version>1.0</app-version>
                        <main-class>.Hello</main-class>
                        """
                )).expect(expect(
                        "error.properties-parameter-invalid-value",
                        ".Hello", "/jpackage-state/main-class", JPACKAGE_XML).create())
        ).map(InvalidXmlTestSpec.Builder::create).forEach(data::add);

        return data;
    }

    private static String createValidBodyWithHeader(String platform, String version) {
        return createWithHeader(platform, version,
                """
                <main-launcher name='D'>
                    <description>Blah-Blah-Blah</description>
                </main-launcher>
                <app-version>100</app-version>
                <main-class>Hello</main-class>
                """
        );
    }

    private static String createWithHeader(String platform, String version, String body) {

        var sb = new StringBuilder();
        sb.append("<jpackage-state");
        Optional.ofNullable(platform).ifPresent(v -> {
            sb.append(String.format(" platform=\"%s\"", v));
        });
        Optional.ofNullable(version).ifPresent(v -> {
            sb.append(String.format(" version=\"%s\"", v));
        });
        sb.append(">");

        return sb.append(Objects.requireNonNull(body)).append("</jpackage-state>").toString();
    }

    private static String createWithHeader(String body) {
        return createWithHeader(AppImageFile.getPlatform(OperatingSystem.current()), AppImageFile.getVersion(), body);
    }


    private static Collection<ReadTestSpec> platformSpecificProperties() {
        var builder = ReadTestSpec.build().xml(
                """
                <app-version>1.34</app-version>
                <main-class>Foo</main-class>
                <y/>
                <x>property-x</x>
                <app-store>False</app-store>
                <add-launcher name='add-launcher'>
                  <description>Quick brown fox</description>
                  <service>true</service>
                  <linux-shortcut>true</linux-shortcut>
                  <win-shortcut>false</win-shortcut>
                  <win-menu>app-dir</win-menu>
                </add-launcher>
                <main-launcher name='Bar'>
                  <description>Bar launcher description</description>
                </main-launcher>
                """
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
                        """
                        <main-launcher name='Y'>
                          <description>Blah-Blah-Blah</description>
                        </main-launcher>
                        <app-version>72</app-version>
                        <main-class>main.Class</main-class>
                        """
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
                        """
                        <app-version>1.2</app-version>
                        <app-version>1.0</app-version>
                        <main-class>OverwrittenMain</main-class>
                        <main-class>Main</main-class>
                        <x>property-x</x>
                        <add-launcher name='service-launcher' service='true'>
                          <linux-shortcut><nested>foo</nested></linux-shortcut>
                          <description>service-launcher description</description>
                        </add-launcher>
                        <add-launcher name='another-launcher'>
                          <linux-shortcut>true</linux-shortcut>
                          <linux-shortcut>app-<!-- This is a comment -->dir</linux-shortcut>
                          <description>another-launcher description</description>
                        </add-launcher>
                        <main-launcher name='Bar'/>
                        <main-launcher name='Foo'>
                          <description>Main launcher description</description>
                        </main-launcher>
                        """
                )
        ).map(ReadTestSpec.Builder::create));
    }

    private static ExternalApplication createFromXml(String xmlData, OperatingSystem os, Path appImageDir) throws IOException {
        Path path = AppImageFile.getPathInAppImage(DUMMY_LAYOUT.resolveAt(appImageDir));

        List<String> data = new ArrayList<>();
        data.add("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>");
        data.add(Objects.requireNonNull(xmlData));

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

    private static final String createXml(OperatingSystem os, String xml) {
        return new StringBuilder()
            .append(String.format("<jpackage-state platform=\"%s\" version=\"%s\">", AppImageFile.getPlatform(os), AppImageFile.getVersion()))
            .append(Objects.requireNonNull(xml))
            .append("</jpackage-state>")
            .toString();
    }

    private static Map<String, Object> toPropertyMap(Options options) {
        return options.toMap().entrySet().stream().collect(toMap(e -> {
            return Objects.requireNonNull(OPTIONS.get(e.getKey()));
        }, Map.Entry::getValue));
    }

    @TempDir
    private Path tempFolder;

    private static final CannedArgument APP_IMAGE_DIR = CannedArgument.ofString("@@APP_IMAGE_DIR@@");
    private static final CannedArgument JPACKAGE_XML = CannedArgument.ofString("@@JPACKAGE_XML@@");

    private static final ObjectMapper OM;

    private static final ApplicationLayout DUMMY_LAYOUT = ApplicationLayout.build().setAll("").create();

    private static final Map<OptionIdentifier, String> OPTIONS = Stream.of(AppImageFileOptionScope.values())
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
                .subst(ExternalApplication.class, "appVersion", obj -> {
                    return obj.appVersion().toString();
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
