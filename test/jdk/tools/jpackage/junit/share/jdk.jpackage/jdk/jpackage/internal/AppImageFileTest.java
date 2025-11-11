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

import static jdk.jpackage.internal.util.function.ThrowingSupplier.toSupplier;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLaunchers;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.ExternalApplication.LauncherInfo;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.LauncherStartupInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;


public class AppImageFileTest {

    static AppImageBuilder build() {
        return new AppImageBuilder();
    }

    static class AppImageBuilder {

        AppImageBuilder version(String v) {
            version = Objects.requireNonNull(v);
            return this;
        }

        AppImageBuilder launcherName(String v) {
            launcherName = Objects.requireNonNull(v);
            return this;
        }

        AppImageBuilder mainClass(String v) {
            mainClass = Objects.requireNonNull(v);
            return this;
        }

        AppImageBuilder addExtra(Map<String, String> v) {
            extra.putAll(v);
            return this;
        }

        AppImageBuilder addExtra(String key, String value) {
            extra.putAll(Map.of(key, value));
            return this;
        }

        AppImageBuilder addlauncher(String name) {
            return addlauncher(name, false);
        }

        AppImageBuilder addlauncher(String name, boolean isService) {
            return addlauncher(name, isService, Map.of());
        }

        AppImageBuilder addlauncher(String name, boolean isService, Map<String, String> extra) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(extra);
            addLauncherInfos.add(new LauncherInfo(name, isService, extra));
            return this;
        }

        AppImageBuilder addlauncher(String name, Map<String, String> extra) {
            return addlauncher(name, false, extra);
        }

        AppImageFile create() {
            final var additionalLaunchers = addLauncherInfos.stream().map(li -> {
                return (Launcher)new Launcher.Stub(li.name(), Optional.empty(),
                        List.of(), li.service(), null, Optional.empty(), null, li.extra());
            }).toList();

            final var startupInfo = new LauncherStartupInfo.Stub(mainClass, List.of(), List.of(), List.of());
            final var mainLauncher = new Launcher.Stub(launcherName, Optional.of(startupInfo),
                    List.of(), false, null, Optional.empty(), null, Map.of());

            final var app = new Application.Stub(null, null, version, null, null,
                    Optional.empty(), List.of(), null, Optional.empty(),
                    new ApplicationLaunchers(mainLauncher, additionalLaunchers).asList(), extra);

            return new AppImageFile(app);
        }

        void createInDir(Path dir) {
            final var file = create();
            final var copy = toSupplier(() -> {
                file.save(DUMMY_LAYOUT.resolveAt(dir));
                return AppImageFile.load(dir, DUMMY_LAYOUT);
            }).get();

            assertEquals(file, copy);
        }

        private String version = "1.0";
        private String launcherName = "Foo";
        private String mainClass = "Main";
        private Map<String, String> extra = new HashMap<>();
        private List<LauncherInfo> addLauncherInfos = new ArrayList<>();
    }

    @Test
    public void testSimple() {
        build().createInDir(tempFolder);
    }

    @ParameterizedTest
    @MethodSource
    public void testExtra(Map<String, String> extra) {
        build().addExtra(extra).createInDir(tempFolder);
    }

    private static Stream<Map<String, String>> testExtra() {
        return Stream.of(Map.of("a", "b"), Map.of("foo", ""));
    }

    @Test
    public void testAdditionalLaunchers() {
        build().addlauncher("T")
                .addlauncher("U", true)
                .addlauncher("F", Map.of("prop", "one", "prop2", "two", "prop3", ""))
                .createInDir(tempFolder);
    }

    @ParameterizedTest
    @MethodSource
    public void testInavlidXml(List<String> xmlData) throws IOException {
        assertThrowsExactly(ConfigException.class, () -> createFromXml(xmlData), () -> {
            return I18N.format("error.invalid-app-image", tempFolder, ".jpackage.xml");
        });
    }

    private static Stream<List<String>> testInavlidXml() {
        return Stream.of(List.of("<foo/>"),
                List.of("<jpackage-state/>"),
                createXml(),
                createXml("<main-launcher></main-launcher>"),
                createXml("<main-launcher>Foo</main-launcher>", "<main-class></main-class>"),
                createXml("<add-launcher>A</add-launcher>")
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testValidXml(AppImageFile expected, List<String> xmlData) throws IOException, ConfigException {
        final var actual = createFromXml(xmlData);
        assertEquals(expected, actual);
    }

    private static Stream<Arguments> testValidXml() {
        return Stream.of(
                Arguments.of(build().version("72").launcherName("Y").mainClass("main.Class").create(), createXml(
                        "<main-launcher>Y</main-launcher>",
                        "<app-version>72</app-version>",
                        "<main-class>main.Class</main-class>")
                ),
                Arguments.of(build().addExtra("x", "property-x").addExtra("signed", "false")
                        .addExtra("y", "").addlauncher("another-launcher").addlauncher("service-launcher", true)
                        .addlauncher("launcher-with-extra", Map.of("a", "", "b", "", "c", "Q"))
                        .addlauncher("service-launcher-with-extra", true, Map.of("h", "F")).create(), createXml(
                        "<app-version>1.0</app-version>",
                        "<main-class>Main</main-class>",
                        "<y/>",
                        "<x>property-x</x>",
                        "<signed>false</signed>",
                        "<add-launcher name='service-launcher' service='true'/>",
                        "<add-launcher name='another-launcher'></add-launcher>",
                        "<add-launcher name='launcher-with-extra'><a></a><b/><c>Q</c></add-launcher>",
                        "<add-launcher name='service-launcher-with-extra' service='true'><h>F</h></add-launcher>",
                        "<main-launcher>Foo</main-launcher>")
                ),
                Arguments.of(build().addExtra("signed", "FalsE").create(), createXml(
                        "<app-version>1.2</app-version>",
                        "<app-version>1.0</app-version>",
                        "<main-class>OverwrittenMain</main-class>",
                        "<main-class>Main</main-class>",
                        "<main-launcher>Bar</main-launcher>",
                        "<main-launcher>Foo</main-launcher>",
                        "<signed>false</signed>",
                        "<signed>FalsE</signed>")
                ),
                Arguments.of(build().addExtra("signed", "true").addExtra("with-comment", "ab")
                        .addlauncher("a", Map.of("bar", "foo")).create(), createXml(
                        "<app-version>1.0</app-version>",
                        "<main-class>Main</main-class>",
                        "<main-launcher>Foo</main-launcher>",
                        "<signed>true</signed>",
                        "<with-comment>a<!-- This is a comment -->b</with-comment>",
                        "<add-launcher name='a'><name>foo</name><bar>foo</bar><service>true</service></add-launcher>",
                        "<other><nested>false</nested></other>",
                        "<another-other>A<child/>B</another-other>")
                )
        );
    }

    private AppImageFile createFromXml(List<String> xmlData) throws IOException, ConfigException {
        Path path = AppImageFile.getPathInAppImage(DUMMY_LAYOUT.resolveAt(tempFolder));
        path.toFile().mkdirs();
        Files.delete(path);

        List<String> data = new ArrayList<>();
        data.add("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>");
        data.addAll(xmlData);

        Files.write(path, data, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

        AppImageFile image = AppImageFile.load(tempFolder, DUMMY_LAYOUT);
        return image;
    }

    private static void assertEquals(AppImageFile expected, AppImageFile actual) {
        assertPropertyEquals(expected, actual, AppImageFile::getAppVersion);
        assertPropertyEquals(expected, actual, AppImageFile::getLauncherName);
        assertPropertyEquals(expected, actual, AppImageFile::getMainClass);
        assertPropertyEquals(expected, actual, AppImageFile::getExtra);
        Assertions.assertEquals(additionaLaunchersAsMap(expected), additionaLaunchersAsMap(actual));
    }

    private static Map<String, AppImageFile.LauncherInfo> additionaLaunchersAsMap(AppImageFile file) {
        return file.getAddLaunchers().stream().collect(Collectors.toMap(AppImageFile.LauncherInfo::name, x -> x));
    }

    private static <T, U> void assertPropertyEquals(T expected, T actual, Function<T, U> getProperty) {
        Assertions.assertEquals(getProperty.apply(expected), getProperty.apply(actual));
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

    private static final ApplicationLayout DUMMY_LAYOUT = ApplicationLayout.build().setAll("").create();
}
